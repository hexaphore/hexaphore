package app.hexaphore.core.testing

import app.hexaphore.domain.diary.EntryId
import app.hexaphore.domain.diary.FoodEntry
import app.hexaphore.domain.diary.LoggedMeal
import app.hexaphore.domain.diary.Meal
import app.hexaphore.domain.diary.MealId
import app.hexaphore.domain.diary.MealType
import app.hexaphore.domain.nutrition.Macros
import app.hexaphore.domain.nutrition.NutritionSource
import java.time.LocalDate
import java.time.ZoneOffset

/** Une ligne de démonstration, avant qu'elle ne connaisse le repas qui la contient. */
private class SampleEntry(
    val name: String,
    val quantity: Double,
    val unit: String,
    val macros: Macros,
    val source: NutritionSource = NutritionSource.CIQUAL,
)

/**
 * Une journée plausible, pour voir l'accueil fonctionner avant que Room n'existe.
 *
 * Elle contient délibérément une ligne **sans valeur de fibres** : c'est le cas le
 * plus fréquent chez Open Food Facts, et c'est ce qui permet de vérifier sur
 * l'appareil que la mention « totaux minorés » apparaît vraiment. Un jeu de données
 * de démonstration trop propre ne prouve que le cas facile.
 *
 * Elle disparaît de l'application dès que Room alimente le journal ; elle reste
 * utile aux tests d'écran.
 */
object SampleDiary {
    fun day(date: LocalDate): List<LoggedMeal> = listOf(
        meal(
            date = date,
            type = MealType.BREAKFAST,
            sortIndex = 0,
            lines =
            listOf(
                SampleEntry("Pain complet", 80.0, "g", Macros(198.0, 7.8, 36.0, 2.1, 1.6, 5.4)),
                SampleEntry("Yaourt nature", 125.0, "g", Macros(76.0, 4.3, 6.0, 6.0, 3.9, 0.0)),
            ),
        ),
        meal(
            date = date,
            type = MealType.LUNCH,
            sortIndex = 1,
            lines =
            listOf(
                SampleEntry("Riz blanc cuit", 200.0, "g", Macros(258.0, 5.0, 56.0, 0.2, 0.6, 1.4)),
                SampleEntry("Filet de poulet", 150.0, "g", Macros(247.0, 46.5, 0.0, 0.0, 5.7, 0.0)),
                // Produit emballe dont les fibres ne sont pas renseignees.
                SampleEntry(
                    name = "Sauce tomate cuisinee",
                    quantity = 60.0,
                    unit = "g",
                    macros = Macros(41.0, 1.0, 5.4, 4.6, 1.6, null),
                    source = NutritionSource.OPEN_FOOD_FACTS,
                ),
            ),
        ),
    )

    private fun meal(date: LocalDate, type: MealType, sortIndex: Int, lines: List<SampleEntry>): LoggedMeal {
        val mealId = MealId("sample-${type.name.lowercase()}")
        return LoggedMeal(
            meal = Meal(mealId, date, type, customName = null, sortIndex = sortIndex),
            entries =
            lines.mapIndexed { index, line ->
                FoodEntry(
                    id = EntryId("${mealId.value}-$index"),
                    mealId = mealId,
                    displayName = line.name,
                    quantity = line.quantity,
                    unit = line.unit,
                    grams = line.quantity,
                    macros = line.macros,
                    nutritionSource = line.source,
                    loggedAt = date.atStartOfDay().toInstant(ZoneOffset.UTC),
                )
            },
        )
    }
}
