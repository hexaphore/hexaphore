package app.hexaphore.domain.usecase

import app.hexaphore.domain.goal.AdjustmentSettings
import app.hexaphore.domain.goal.AdjustmentSuggestion
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.goal.GoalId
import app.hexaphore.domain.goal.GoalOrigin
import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.time.Clock
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Les trois issues d'une carte de suggestion ([docs/02][parcours]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
enum class AdjustmentResponse {
    /** Écrit une nouvelle version de l'objectif. */
    ACCEPT,

    /** N'écrit rien. La suggestion revient dans deux semaines. */
    IGNORE,

    /** N'écrit rien. Désactive l'adaptation. */
    STOP,
}

/**
 * Répondre à une suggestion.
 *
 * **Un seul cas d'usage pour les trois issues**, et c'est ce qui rend l'invariant
 * visible : *accepter* est le seul chemin qui écrit un objectif, les deux autres ne
 * touchent qu'aux réglages. Trois classes auraient rangé cette propriété nulle part, et
 * le critère de fin de tranche — *aucune suggestion n'est appliquée sans accord
 * explicite* — n'aurait eu aucun endroit où se tester.
 *
 * Accepter **ouvre une version**, il n'en modifie aucune ([D04][decisions]) : le port
 * n'offre d'ailleurs que [Goals.replace]. Le cap reste le même — stratégie, poids visé,
 * échéance — seuls les six chiffres bougent, et [GoalOrigin.ADJUSTMENT] dit d'où ils
 * viennent.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/03-nutrition-calculs.md
 */
class RespondToAdjustment(
    private val goals: Goals,
    private val settings: AdjustmentSettings,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(response: AdjustmentResponse, suggestion: AdjustmentSuggestion) {
        val today = clock.today()

        when (response) {
            AdjustmentResponse.ACCEPT -> accept(suggestion, today)
            AdjustmentResponse.IGNORE -> settings.ignored(today)
            AdjustmentResponse.STOP -> settings.stop()
        }
    }

    /**
     * L'objectif d'abord, le réglage ensuite.
     *
     * Dans l'autre ordre, une écriture qui échoue laisserait l'adaptation muette pour
     * deux semaines sans avoir rien corrigé — le pire des deux états.
     */
    private suspend fun accept(suggestion: AdjustmentSuggestion, today: LocalDate) {
        val current = goals.observeCurrent().first() ?: return

        goals.replace(
            Goal(
                id = GoalId(ids.next()),
                startedAt = today,
                origin = GoalOrigin.ADJUSTMENT,
                strategy = current.strategy,
                targetWeightKg = current.targetWeightKg,
                targetDate = current.targetDate,
                daily = suggestion.proposed,
            ),
        )
        settings.accepted(today)
    }
}
