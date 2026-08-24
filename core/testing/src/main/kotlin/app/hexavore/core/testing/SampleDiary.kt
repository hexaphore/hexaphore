package app.hexavore.core.testing

import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.nutrition.Macros
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Une ligne de démonstration, avant qu'elle ne connaisse le plat qui la contient. */
private class SampleEntry(val name: String, val quantity: Double, val unit: String, val macros: Macros)

/**
 * Une journée plausible, pour voir l'accueil fonctionner avant que Room n'existe.
 *
 * Deux choix délibérés, pour que la démonstration montre les cas difficiles et pas
 * seulement les faciles :
 *
 * - une ligne **sans valeur de fibres**, le cas le plus fréquent chez Open Food
 *   Facts, qui doit faire apparaître la mention « totaux minorés » ;
 * - un plat **proposé par l'IA** à côté de plats saisis à la main, pour que le
 *   contour en pointillés du badge se vérifie sur l'appareil.
 *
 * Elle disparaît de l'application dès que Room alimente le journal ; elle reste
 * utile aux tests d'écran.
 */
object SampleDiary {
    fun day(date: LocalDate): List<Dish> = listOf(
        dish(
            date = date,
            hour = 8,
            source = EntrySource.MANUAL,
            lines = listOf(
                SampleEntry("Pain complet", 80.0, "g", Macros(198.0, 7.8, 36.0, 2.1, 1.6, 5.4)),
                SampleEntry("Yaourt nature", 125.0, "g", Macros(76.0, 4.3, 6.0, 6.0, 3.9, 0.0)),
            ),
        ),
        dish(
            date = date,
            hour = 13,
            source = EntrySource.PHOTO_AI,
            lines = listOf(
                SampleEntry("Riz blanc cuit", 200.0, "g", Macros(258.0, 5.0, 56.0, 0.2, 0.6, 1.4)),
                SampleEntry("Filet de poulet", 150.0, "g", Macros(247.0, 46.5, 0.0, 0.0, 5.7, 0.0)),
                // Produit emballe dont les fibres ne sont pas renseignees.
                SampleEntry("Sauce tomate cuisinee", 60.0, "g", Macros(41.0, 1.0, 5.4, 4.6, 1.6, null)),
            ),
        ),
        dish(
            date = date,
            hour = 16,
            source = EntrySource.MANUAL,
            lines = listOf(
                SampleEntry("Amandes", 30.0, "g", Macros(174.0, 6.3, 1.4, 1.2, 15.0, 3.7)),
            ),
        ),
    )

    private fun dish(date: LocalDate, hour: Int, source: EntrySource, lines: List<SampleEntry>): Dish {
        val dishId = DishId("sample-$hour")
        return Dish(
            id = dishId,
            date = date,
            source = source,
            loggedAt = date.atTime(LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC),
            entries = lines.mapIndexed { index, line ->
                FoodEntry(
                    id = EntryId("${dishId.value}-$index"),
                    dishId = dishId,
                    displayName = line.name,
                    quantity = line.quantity,
                    unit = line.unit,
                    grams = line.quantity,
                    macros = line.macros,
                )
            },
        )
    }
}
