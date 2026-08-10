package app.hexaphore.domain.usecase

import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.goal.EnergyExpenditureCalculator
import app.hexaphore.domain.goal.GoalGuard
import app.hexaphore.domain.goal.GoalSafetyPolicy
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.goal.KCAL_PER_KILOGRAM
import app.hexaphore.domain.goal.MacroDistributionPolicy
import app.hexaphore.domain.profile.UserProfile
import app.hexaphore.domain.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Ce que l'utilisateur vise, exprimé comme il le saisit.
 *
 * [targetWeightKg] et [targetDate] sont nuls en maintien : il n'y a ni poids à
 * atteindre ni échéance, seulement une dépense à couvrir.
 */
data class GoalRequest(
    val strategy: GoalStrategy,
    val currentWeightKg: Double,
    val targetWeightKg: Double? = null,
    val targetDate: LocalDate? = null,
)

/**
 * Le résultat complet, et pas seulement les six chiffres.
 *
 * L'écran a besoin de savoir **pourquoi** l'objectif est ce qu'il est : le TDEE pour
 * l'expliquer, les garde-fous qui ont mordu pour le dire, et la date atteignable pour
 * la proposer. Ne rendre que [goal] obligerait l'écran à refaire le calcul pour
 * l'annoncer, c'est-à-dire à le refaire différemment un jour.
 */
data class GoalPlan(
    val goal: DailyGoal,
    val tdee: Double,
    val guardsApplied: Set<GoalGuard>,
    val reachableOn: LocalDate?,
    val carbsBelowMinimum: Boolean,
) {
    val capped: Boolean get() = guardsApplied.isNotEmpty()

    /**
     * L'écart entre la somme des macros et l'objectif calorique, en kcal.
     *
     * Il vaut quelques kcal d'arrondi, jamais davantage. **Les calories font foi** ;
     * les macros sont des répartitions indicatives, et cet écart n'est ni affiché ni
     * corrigé artificiellement. Il est exposé pour être **éprouvé** : c'est ce
     * contrôle qui a révélé les 70 kcal de fibres distribuées deux fois ([D24][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val energyGap: Double get() = goal.macroEnergy - goal.kcal
}

/**
 * Le calcul d'un objectif quotidien, de bout en bout.
 *
 * ```
 * BMR → TDEE → Δkcal demandé par l'échéance → garde-fous → répartition
 * ```
 *
 * Chaque étape vit dans son propre objet et se teste seule ; celui-ci les enchaîne et
 * ne décide de rien. C'est ce qui permet d'éprouver un garde-fou sur ses deux bornes
 * sans construire un profil complet à chaque fois.
 *
 * **L'horloge est injectée**, parce que l'âge et le nombre de jours restants en
 * dépendent : un objectif calculé avec `LocalDate.now()` serait invérifiable, et se
 * périmerait le lendemain de sa propre écriture.
 *
 * @see docs/03-nutrition-calculs.md
 */
class CalculateDailyGoal(private val clock: Clock) {
    operator fun invoke(profile: UserProfile, request: GoalRequest): GoalPlan {
        val today = clock.today()
        val tdee = EnergyExpenditureCalculator.totalExpenditure(profile, request.currentWeightKg, today)

        val budget = GoalSafetyPolicy.apply(
            rawDeltaKcal = request.rawDailyDelta(today),
            tdee = tdee,
            currentWeightKg = request.currentWeightKg,
            targetWeightKg = request.targetWeightKg,
            sex = profile.sex,
            from = today,
        )

        // L'objectif est arrondi avant la repartition, et non apres : c'est lui que
        // l'ecran affiche, et une repartition calculee sur 2 524,7 kcal ne retomberait
        // pas sur les 2 525 annonces.
        val kcal = (tdee + budget.dailyDeltaKcal).roundToInt().toDouble()
        val macros = MacroDistributionPolicy.distribute(
            kcal = kcal,
            strategy = request.strategy,
            currentWeightKg = request.currentWeightKg,
            targetWeightKg = request.targetWeightKg ?: request.currentWeightKg,
        )

        return GoalPlan(
            goal = macros.goal,
            tdee = tdee,
            guardsApplied = budget.guardsApplied,
            reachableOn = budget.reachableOn,
            carbsBelowMinimum = macros.carbsBelowMinimum,
        )
    }

    /**
     * Ce que l'échéance demanderait, sans limite.
     *
     * Zéro en maintien, et zéro aussi si l'échéance est aujourd'hui ou passée : diviser
     * par zéro jour rendrait un infini, que les garde-fous ramèneraient certes à une
     * borne, mais en passant par une valeur qui n'a aucun sens.
     */
    private fun GoalRequest.rawDailyDelta(today: LocalDate): Double {
        val target = targetWeightKg
        val date = targetDate
        if (target == null || date == null) return 0.0

        val days = ChronoUnit.DAYS.between(today, date)
        return if (days > 0) (target - currentWeightKg) * KCAL_PER_KILOGRAM / days else 0.0
    }
}
