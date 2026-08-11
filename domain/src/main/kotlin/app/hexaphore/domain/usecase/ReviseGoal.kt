package app.hexaphore.domain.usecase

import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.goal.GoalId
import app.hexaphore.domain.goal.GoalOrigin
import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.profile.Profiles
import app.hexaphore.domain.profile.UserProfile
import app.hexaphore.domain.profile.WeightEntry
import app.hexaphore.domain.profile.WeightLog
import app.hexaphore.domain.time.Clock
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Une correction du profil, telle que l'écran la présente.
 *
 * [daily] vient de l'appelant et n'est **pas** recalculé ici, contrairement à ce qu'un
 * cas d'usage ferait d'ordinaire. Deux raisons, et la seconde est la plus forte.
 *
 * D'abord, l'écran affiche les six chiffres en direct pendant qu'on corrige sa taille
 * ou son échéance : les recalculer au moment d'écrire ouvrirait la possibilité
 * d'enregistrer autre chose que ce qui était affiché — un écart qui n'apparaîtrait
 * qu'une fois la ligne écrite. Ce qui est montré est ce qui est écrit, comme l'aperçu
 * de rythme de [D56][decisions].
 *
 * Ensuite, un objectif **manuel** n'est le résultat d'aucun calcul ([D60][decisions]).
 * Un cas d'usage qui calculerait lui-même n'aurait aucun moyen de produire ces
 * six chiffres-là, et il faudrait lui passer quand même — donc revenir ici.
 *
 * [origin] est le mode, et il décide de tout le reste : `CALCULATED` suit le profil,
 * `MANUAL` ne suit plus rien. [GoalRequest.targetWeightKg] et
 * [GoalRequest.targetDate] restent enregistrés dans les deux cas, parce qu'ils
 * décrivent le cap annoncé — mais en mode manuel, ils ne pilotent aucun des six.
 *
 * [decisions]: docs/11-decisions.md
 */
data class GoalRevision(
    val profile: UserProfile,
    val request: GoalRequest,
    val daily: DailyGoal,
    val origin: GoalOrigin,
)

/**
 * Corriger son profil, et l'objectif qui en découle.
 *
 * **Un objectif corrigé est une ligne de plus, jamais la même modifiée** — c'est
 * [D04][decisions], et c'est le piège que le plan de développement désigne nommément.
 * Le port n'offre d'ailleurs aucun `update` : [Goals.replace] clôt l'ancien et écrit le
 * neuf en une transaction. Une correction faite le jour même clôt donc l'ancien
 * objectif à sa propre date de début, où il ne couvre plus aucune journée — c'est juste,
 * le nouveau prend la journée entière.
 *
 * L'ordre — profil, pesée, objectif — est celui de l'onboarding, et pour la même
 * raison : l'objectif découle des deux autres, et les écrire après lui laisserait un
 * instant pendant lequel l'accueil aurait un objectif sans savoir d'où il vient.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/03-nutrition-calculs.md
 */
class ReviseGoal(
    private val profiles: Profiles,
    private val weights: WeightLog,
    private val goals: Goals,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(revision: GoalRevision) {
        val today = clock.today()

        profiles.save(revision.profile)
        recordWeighing(revision.request.currentWeightKg, today)

        val next = revision.toGoal(GoalId(ids.next()), today)
        val current = goals.observeCurrent().first()

        // Rien de neuf : ouvrir les reglages et ressortir ne doit pas laisser de
        // trace. Une ligne par visite ferait de l'historique des changements de cap
        // un journal de consultations, et c'est precisement la contrepartie qu'on
        // paie en versionnant.
        if (current == null || !current.sameAimAs(next)) goals.replace(next)
    }

    /**
     * Une pesée est un fait daté, pas un champ de formulaire.
     *
     * Le poids affiché est celui de la dernière pesée connue, et il faut le montrer :
     * c'est lui qui entre dans le calcul. Mais le réécrire à la date du jour parce que
     * l'écran a été ouvert affirmerait qu'on s'est pesé aujourd'hui — et la moyenne
     * mobile sur sept jours compterait alors une mesure que personne n'a faite.
     */
    private suspend fun recordWeighing(weightKg: Double, today: LocalDate) {
        val latest = weights.observeLatest().first()
        if (latest?.weightKg != weightKg) weights.record(WeightEntry(today, weightKg))
    }
}

private fun GoalRevision.toGoal(id: GoalId, today: LocalDate) = Goal(
    id = id,
    startedAt = today,
    origin = origin,
    strategy = request.strategy,
    targetWeightKg = request.targetWeightKg,
    targetDate = request.targetDate,
    daily = daily,
)
