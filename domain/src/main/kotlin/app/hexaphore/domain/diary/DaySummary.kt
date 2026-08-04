package app.hexaphore.domain.diary

import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.nutrition.MacroTotals
import java.time.LocalDate

/**
 * Un repas avec ses lignes et ses sous-totaux.
 *
 * Les sous-totaux sont calculés une fois ici plutôt que par chaque écran : deux
 * calculs du même nombre finissent toujours par diverger.
 */
data class MealSummary(val meal: Meal, val entries: List<FoodEntry>, val totals: MacroTotals)

/**
 * Ce qu'affiche l'accueil pour une journée.
 *
 * [logged] distingue « rien noté » de « rien mangé ». Une journée sans saisie n'est
 * pas une journée à zéro : la compter comme telle fausserait les moyennes du
 * calendrier et déclencherait des suggestions d'ajustement absurdes.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
data class DaySummary(
    val date: LocalDate,
    val goal: DailyGoal,
    val totals: MacroTotals,
    val meals: List<MealSummary>,
) {
    /** `false` quand la journée ne contient aucune saisie. */
    val logged: Boolean get() = meals.isNotEmpty()
}
