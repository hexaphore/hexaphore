package app.hexaphore.domain.usecase

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.core.testing.InMemoryGoals
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.EntryId
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.FoodEntry
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.Macros
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GetDaySummaryTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val jour = LocalDate.of(2026, 3, 15)

    @Test
    fun `additionne les lignes de tous les plats`() = runBlocking {
        val summary =
            summaryOf(
                plat(macros(kcal = 320.0, protein = 12.0, fiber = 4.0)),
                plat(macros(kcal = 780.0, protein = 41.0, fiber = 9.0)),
            )

        assertEquals(1100.0, summary.totals[Macro.CALORIES].value)
        assertEquals(53.0, summary.totals[Macro.PROTEIN].value)
        assertEquals(13.0, summary.totals[Macro.FIBER].value)
    }

    @Test
    fun `une valeur inconnue n'est pas un zero`() = runBlocking {
        val summary =
            summaryOf(
                plat(
                    macros(kcal = 320.0, fiber = 4.0),
                    // Produit sans valeur de fibres : le cas courant chez
                    // Open Food Facts, et la porte d'entree du bug.
                    macros(kcal = 210.0, fiber = null),
                ),
            )

        val fibres = summary.totals[Macro.FIBER]
        assertEquals(4.0, fibres.value, "le total doit rester la somme de ce qu'on sait")
        assertFalse(fibres.complete, "un total ampute d'une valeur inconnue doit se signaler")
        assertTrue(summary.totals[Macro.CALORIES].complete, "les calories, elles, sont connues")
    }

    @Test
    fun `une journee sans saisie n'est pas une journee a zero`() = runBlocking {
        val summary = summaryOf()

        assertFalse(summary.logged, "aucune saisie ne doit pas se lire comme une journee vecue")
        assertEquals(0.0, summary.totals[Macro.CALORIES].value)
        assertTrue(
            summary.totals[Macro.CALORIES].complete,
            "zero connu n'est pas zero ignore : rien de note est une information exacte",
        )
    }

    @Test
    fun `la journee par defaut est celle de l'horloge, pas celle du systeme`() = runBlocking {
        val veille = jour.minusDays(1)
        val diary =
            InMemoryDiaryRepository(
                listOf(
                    plat(macros(kcal = 900.0), date = veille),
                    plat(macros(kcal = 700.0), date = jour),
                ),
            )

        val summary = GetDaySummary(
            diary,
            InMemoryGoals(listOf(InMemoryGoals.maintenance(jour))),
            FixedClock.atNoon(jour, paris),
        )().first()

        assertEquals(jour, summary.date)
        assertEquals(700.0, summary.totals[Macro.CALORIES].value, "la veille ne doit pas deborder")
    }

    @Test
    fun `une entree de 23h59 appartient au jour local et non au jour UTC`() = runBlocking {
        // 22 h 59 UTC le 15 mars, soit 23 h 59 a Paris le meme jour. Une horloge
        // qui raisonnerait en UTC donnerait deja le 15, mais le meme calcul une
        // heure plus tard basculerait au 16 alors qu'il est 00 h 59 a Paris.
        val minuitMoinsUne = Instant.parse("2026-03-15T22:59:00Z")
        val diary = InMemoryDiaryRepository(listOf(plat(macros(kcal = 150.0))))

        val summary = GetDaySummary(
            diary,
            InMemoryGoals(listOf(InMemoryGoals.maintenance(jour))),
            FixedClock(minuitMoinsUne, paris),
        )().first()

        assertEquals(jour, summary.date)
        assertEquals(150.0, summary.totals[Macro.CALORIES].value)
    }

    @Test
    fun `chaque plat porte ses six apports, et pas seulement ses calories`() = runBlocking {
        val summary =
            summaryOf(
                plat(macros(kcal = 320.0, protein = 12.0, fat = 8.0)),
                plat(macros(kcal = 780.0, protein = 41.0, fat = 21.0)),
            )

        assertEquals(2, summary.dishes.size)
        assertEquals(320.0, summary.dishes[0].totals[Macro.CALORIES].value)
        assertEquals(12.0, summary.dishes[0].totals[Macro.PROTEIN].value)
        assertEquals(21.0, summary.dishes[1].totals[Macro.FAT].value)
    }

    @Test
    fun `la source du plat traverse le resume sans etre reecrite`() = runBlocking {
        val summary = summaryOf(plat(macros(kcal = 320.0), source = EntrySource.PHOTO_AI))

        assertEquals(EntrySource.PHOTO_AI, summary.dishes.single().dish.source)
        assertTrue(summary.dishes.single().dish.source.proposed)
    }

    // --- Decor ---------------------------------------------------------------

    private suspend fun summaryOf(vararg dishes: Dish) = GetDaySummary(
        InMemoryDiaryRepository(dishes.toList()),
        InMemoryGoals(listOf(InMemoryGoals.maintenance(jour))),
        FixedClock.atNoon(jour, paris),
    )()
        .first()

    private var nextIndex = 0

    private fun plat(vararg lignes: Macros, date: LocalDate = jour, source: EntrySource = EntrySource.MANUAL): Dish {
        val index = nextIndex++
        val dishId = DishId("plat-$index")
        return Dish(
            id = dishId,
            date = date,
            source = source,
            loggedAt = Instant.parse("2026-03-15T11:00:00Z").plusSeconds(index.toLong()),
            entries =
            lignes.mapIndexed { position, macros ->
                FoodEntry(
                    id = EntryId("ligne-$index-$position"),
                    dishId = dishId,
                    displayName = "Aliment $position",
                    quantity = 100.0,
                    unit = "g",
                    grams = 100.0,
                    macros = macros,
                )
            },
        )
    }

    private fun macros(
        kcal: Double,
        protein: Double? = 0.0,
        carbs: Double? = 0.0,
        sugars: Double? = 0.0,
        fat: Double? = 0.0,
        fiber: Double? = 0.0,
    ) = Macros(kcal = kcal, protein = protein, carbs = carbs, sugars = sugars, fat = fat, fiber = fiber)
}
