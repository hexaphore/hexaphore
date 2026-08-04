package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.DaySummary
import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.MealSummary
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.nutrition.MacroTotals
import app.hexaphore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Le résumé d'une journée : ses repas, leurs sous-totaux, et l'objectif du jour.
 *
 * L'horloge est injectée, et c'est tout l'intérêt : la journée par défaut est celle
 * de [Clock], jamais `LocalDate.now()`. Sans cela, la règle « une entrée de 23 h 59
 * appartient au bon jour » ne se testerait qu'en attendant minuit.
 *
 * L'objectif est encore celui, provisoire, de [DailyGoal.Placeholder] — dette de la
 * tranche 1, levée en tranche 4.
 *
 * @see docs/06-architecture.md
 */
class GetDaySummary(private val diary: DiaryRepository, private val clock: Clock) {
    /**
     * @param date journée demandée. Par défaut celle de l'horloge, évaluée à chaque
     *   appel : un écran resté ouvert pendant la nuit ne doit pas continuer
     *   d'afficher la veille.
     */
    operator fun invoke(date: LocalDate = clock.today()): Flow<DaySummary> = diary.observeDay(date).map { logged ->
        val meals =
            logged.map { (meal, entries) ->
                MealSummary(
                    meal = meal,
                    entries = entries,
                    totals = MacroTotals.of(entries.map { it.macros }),
                )
            }

        DaySummary(
            date = date,
            goal = DailyGoal.Placeholder,
            // Recalculé depuis les lignes et non par somme des sous-totaux :
            // additionner des totaux ferait perdre la trace des valeurs
            // inconnues, qui est justement ce qu'on cherche à conserver.
            totals = MacroTotals.of(logged.flatMap { it.entries }.map { it.macros }),
            meals = meals,
        )
    }
}
