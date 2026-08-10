package app.hexaphore.domain.goal

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import app.hexaphore.domain.profile.UserProfile
import app.hexaphore.domain.usecase.CalculateDailyGoal
import app.hexaphore.domain.usecase.GoalRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.abs

/**
 * L'exemple chiffré de [docs/03][calculs], au kcal près.
 *
 * C'est le test de référence de la tranche : il ne vérifie pas qu'un calcul a lieu,
 * il vérifie **les chiffres publiés dans la conception**. Un écart d'un gramme sur les
 * lipides ne fait rien planter et ne se voit nulle part — sauf ici.
 *
 * Le contrôle de cohérence énergétique en fait partie, et ce n'est pas une précaution
 * de style : c'est lui qui a révélé les 70 kcal de fibres distribuées deux fois
 * ([D24][decisions]). Sans lui, la répartition aurait paru juste.
 *
 * [calculs]: docs/03-nutrition-calculs.md
 * [decisions]: docs/11-decisions.md
 */
class GoalCalculatorTest {
    private val clock = FixedClock.atNoon(AUJOURD_HUI)
    private val calculate = CalculateDailyGoal(clock)

    @Test
    fun `le metabolisme de base suit Mifflin-St Jeor`() {
        val bmr = EnergyExpenditureCalculator.basalRate(HOMME, POIDS_ACTUEL, AUJOURD_HUI)

        assertEquals(1_847.5, bmr, TOLERANCE)
    }

    @Test
    fun `la depense totale applique le facteur d activite`() {
        val tdee = EnergyExpenditureCalculator.totalExpenditure(HOMME, POIDS_ACTUEL, AUJOURD_HUI)

        assertEquals(2_863.625, tdee, TOLERANCE)
    }

    @Test
    fun `un sexe non precise applique la moyenne des deux formules`() {
        // Et non un repli sur la variante masculine : l'ecran annonce « une estimation
        // intermediaire », et le calcul doit dire la meme chose que l'ecran.
        val homme = EnergyExpenditureCalculator.basalRate(HOMME, POIDS_ACTUEL, AUJOURD_HUI)
        val femme = EnergyExpenditureCalculator.basalRate(HOMME.copy(sex = Sex.FEMALE), POIDS_ACTUEL, AUJOURD_HUI)
        val neutre =
            EnergyExpenditureCalculator.basalRate(HOMME.copy(sex = Sex.UNSPECIFIED), POIDS_ACTUEL, AUJOURD_HUI)

        assertEquals((homme + femme) / 2, neutre, TOLERANCE)
    }

    @Test
    fun `l exemple de reference donne 2525 kcal`() {
        val plan = calculate(HOMME, REFERENCE)

        assertEquals(2_525.0, plan.goal.kcal, TOLERANCE)
        assertFalse(plan.capped, "aucun garde-fou ne doit mordre sur cet exemple")
    }

    @Test
    fun `l exemple de reference donne la repartition publiee`() {
        val plan = calculate(HOMME, REFERENCE)

        assertEquals(144.0, plan.goal.protein, TOLERANCE, "proteines : 1,8 g/kg de poids cible")
        assertEquals(70.0, plan.goal.fat, TOLERANCE, "lipides : 25 % des calories")
        assertEquals(35.0, plan.goal.fiber, TOLERANCE, "fibres : 14 g pour 1 000 kcal")
        assertEquals(312.0, plan.goal.carbs, TOLERANCE, "glucides : le solde, fibres deduites")
        assertEquals(63.0, plan.goal.sugars, TOLERANCE, "sucres : plafond a 10 % des calories")
    }

    @Test
    fun `le controle de coherence energetique retombe a un kcal pres`() {
        // Le test qui aurait attrape les 70 kcal de fibres distribuees deux fois.
        // 576 + 630 + 70 + 1 248 = 2 524, soit 1 kcal d'arrondi.
        val plan = calculate(HOMME, REFERENCE)

        assertEquals(2_524.0, plan.goal.macroEnergy, TOLERANCE)
        assertTrue(
            abs(plan.energyGap) <= ARRONDI_MAXIMAL,
            "ecart de ${plan.energyGap} kcal entre la somme des macros et l objectif",
        )
    }

    @Test
    fun `les fibres sont deduites du solde et non ajoutees par-dessus`() {
        // La forme directe de D24 : sans la deduction, les glucides vaudraient
        // (2525 - 576 - 630) / 4 = 330 g, et la somme depasserait l'objectif de 70 kcal.
        val plan = calculate(HOMME, REFERENCE)

        assertEquals(312.0, plan.goal.carbs, TOLERANCE)
        assertTrue(plan.goal.macroEnergy <= plan.goal.kcal, "la repartition depasse le budget calorique")
    }

    @Test
    fun `les sucres ne sont pas comptes en plus des glucides`() {
        // Ils y sont inclus. Les additionner ferait deborder le controle de 252 kcal
        // sur cet exemple, et c'est exactement la forme de l'erreur que D24 decrit.
        val plan = calculate(HOMME, REFERENCE)

        assertTrue(plan.goal.sugars < plan.goal.carbs, "les sucres sont une part des glucides")
    }

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 10)

        const val POIDS_ACTUEL = 88.0
        const val POIDS_CIBLE = 80.0

        /** Homme, 35 ans, 182 cm, sport 3 à 5 fois par semaine. */
        val HOMME = UserProfile(
            birthDate = LocalDate.of(1991, 3, 4),
            sex = Sex.MALE,
            heightCm = 182.0,
            activityLevel = ActivityLevel.MODERATE,
        )

        /** 88 → 80 kg en 6 mois, soit 182 jours. */
        val REFERENCE = GoalRequest(
            strategy = GoalStrategy.LOSE,
            currentWeightKg = POIDS_ACTUEL,
            targetWeightKg = POIDS_CIBLE,
            targetDate = AUJOURD_HUI.plusDays(182),
        )

        const val TOLERANCE = 0.01

        /** « Quelques kcal, jamais davantage » — docs/03 dit l'arrondi, pas plus. */
        const val ARRONDI_MAXIMAL = 4.0
    }
}
