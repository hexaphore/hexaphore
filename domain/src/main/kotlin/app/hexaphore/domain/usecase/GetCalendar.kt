package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.goal.activeOn
import app.hexaphore.domain.nutrition.MacroTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * Ce qu'une plage de jours a reçu, jour par jour.
 *
 * **Une journée sans saisie n'a pas de clé**, et c'est toute la conception de ce
 * type. [docs/12][plan] en fait un critère de fin de tranche : une journée sans
 * saisie est visuellement neutre et n'est **jamais** comptée comme une journée à
 * zéro. Rendre une carte de trente jours dont vingt à zéro obligerait chaque écran à
 * refaire la distinction, et le premier qui l'oublierait afficherait vingt jours de
 * jeûne parfait.
 *
 * C'est la même règle que `null` face à zéro sur une teneur, appliquée à l'échelle
 * d'une journée : l'absence est une information, pas une valeur nulle.
 *
 * **Aucun `SUM` en SQL**, comme pour une seule journée : agréger en base traiterait
 * les valeurs inconnues comme absentes, ce qui est juste arithmétiquement mais perd
 * la trace de ce qui manquait ([D29][decisions]). Les plats remontent entiers et le
 * domaine totalise — quelques centaines de lignes pour un mois, et la distinction
 * survit.
 *
 * [plan]: docs/12-plan-de-developpement.md
 * [decisions]: docs/11-decisions.md
 */
class GetCalendar(private val diary: DiaryRepository, private val goals: Goals) {
    /**
     * @param from,to bornes incluses.
     * @return les seuls jours qui portent au moins un plat.
     */
    operator fun invoke(from: LocalDate, to: LocalDate): Flow<List<CalendarDay>> =
        combine(diary.observeRange(from, to), goals.observeAll()) { dishes, all ->
            dishes
                .groupBy { it.date }
                .map { (date, ofDay) ->
                    CalendarDay(
                        date = date,
                        totals = MacroTotals.of(ofDay.flatMap { dish -> dish.entries }.map { it.macros }),
                        // L'objectif **qui valait ce jour-la**, comme pour une journee
                        // ouverte : sans lui, changer d'objectif repeindrait tout le
                        // mois ecoule en depassement ([D04]).
                        goal = all.activeOn(date)?.daily,
                    )
                }
                .sortedBy { it.date }
        }
}

/**
 * Une journée du calendrier, telle qu'une pastille la montre.
 *
 * Elle n'existe **que si** le jour porte au moins un plat. Une journée absente de la
 * liste est une journée sans saisie, et l'écran la dessine neutre.
 *
 * [goal] peut manquer là où [totals] ne manque jamais : une journée notée avant qu'un
 * objectif existe a bien des apports, mais rien à quoi les comparer. L'anneau se
 * dessine alors sans remplissage.
 */
data class CalendarDay(val date: LocalDate, val totals: MacroTotals, val goal: DailyGoal?)
