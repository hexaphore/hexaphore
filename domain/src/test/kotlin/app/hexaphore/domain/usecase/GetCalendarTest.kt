package app.hexaphore.domain.usecase

import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.core.testing.InMemoryGoals
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.EntryId
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.FoodEntry
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.goal.GoalId
import app.hexaphore.domain.goal.GoalOrigin
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.nutrition.Macros
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Le calendrier, et la règle qui fait tout son intérêt.
 *
 * **Une journée sans saisie n'est jamais une journée à zéro.** C'est un critère de fin
 * de tranche, et la seule façon de le tenir sans compter sur la vigilance de chaque
 * écran est de ne produire aucune ligne : l'absence **est** la neutralité. Une liste
 * de trente jours dont vingt à zéro afficherait vingt jours de jeûne parfait au
 * premier écran qui oublierait la distinction.
 */
class GetCalendarTest {
    private val diary = InMemoryDiaryRepository()

    @Test
    fun `un jour sans saisie n a pas de ligne`() = runTest {
        diary.setContent(listOf(repas(LUNDI, kcal = 500.0)))

        val jours = calendrier(LUNDI, LUNDI.plusDays(2))

        assertEquals(listOf(LUNDI), jours.map { it.date })
        assertFalse(jours.any { it.date == MARDI }, "mardi n'a rien recu, donc mardi n'existe pas ici")
    }

    @Test
    fun `un jour a zero kilocalorie a bien une ligne`() = runTest {
        // Zero n'est pas rien : quelqu'un a note un cafe noir. Le confondre avec une
        // journee vide effacerait une saisie reelle.
        diary.setContent(listOf(repas(LUNDI, kcal = 0.0)))

        val jours = calendrier(LUNDI, LUNDI)

        assertEquals(1, jours.size)
        assertEquals(0.0, jours.single().totals.calories.value)
    }

    @Test
    fun `les plats d un meme jour sont totalises ensemble`() = runTest {
        diary.setContent(listOf(repas(LUNDI, kcal = 500.0), repas(LUNDI, kcal = 300.0, id = "d2")))

        assertEquals(800.0, calendrier(LUNDI, LUNDI).single().totals.calories.value)
    }

    @Test
    fun `un total minore le reste`() = runTest {
        // La distinction que « aucun SUM en SQL » existe pour preserver : une ligne
        // sans fibres rend le total incomplet, et le calendrier doit le savoir comme
        // l'accueil le sait.
        diary.setContent(listOf(repas(LUNDI, kcal = 500.0, fiber = null)))

        assertFalse(calendrier(LUNDI, LUNDI).single().totals.fiber.complete)
    }

    @Test
    fun `les bornes sont incluses des deux cotes`() = runTest {
        diary.setContent(listOf(repas(LUNDI, kcal = 100.0), repas(MERCREDI, kcal = 200.0, id = "d2")))

        assertEquals(listOf(LUNDI, MERCREDI), calendrier(LUNDI, MERCREDI).map { it.date })
    }

    @Test
    fun `ce qui est hors de la plage n y entre pas`() = runTest {
        val avant = repas(LUNDI.minusDays(1), kcal = 100.0)
        val apres = repas(MERCREDI.plusDays(1), kcal = 200.0, id = "d2")
        diary.setContent(listOf(avant, apres))

        assertTrue(calendrier(LUNDI, MERCREDI).isEmpty())
    }

    @Test
    fun `les jours sortent en ordre chronologique`() = runTest {
        diary.setContent(listOf(repas(MERCREDI, kcal = 100.0), repas(LUNDI, kcal = 200.0, id = "d2")))

        assertEquals(listOf(LUNDI, MERCREDI), calendrier(LUNDI, MERCREDI).map { it.date })
    }

    // --- L'objectif de chaque jour ---------------------------------------------

    @Test
    fun `chaque jour porte l objectif qui valait ce jour-la`() = runTest {
        // Sans cela, changer d'objectif repeindrait tout le mois ecoule en
        // depassement. C'est la raison d'etre des objectifs versionnes (D04).
        val ancien = objectif("v1", debut = LUNDI, fin = MERCREDI, kcal = 2500.0)
        val nouveau = objectif("v2", debut = MERCREDI, kcal = 2000.0)
        diary.setContent(listOf(repas(LUNDI, kcal = 100.0), repas(MERCREDI, kcal = 200.0, id = "d2")))

        val jours = calendrier(LUNDI, MERCREDI, InMemoryGoals(listOf(nouveau, ancien)))

        assertEquals(2500.0, jours.first().goal?.kcal)
        assertEquals(2000.0, jours.last().goal?.kcal, "le jour du remplacement releve du nouveau")
    }

    @Test
    fun `le jour du remplacement releve du nouveau, quel que soit l ordre donne`() = runTest {
        // La liste est donnee dans le desordre : c'est la lecture qui la trie par date
        // de debut decroissante, comme le fait la requete. Ce cas verifie ce tri.
        //
        // **Il ne verifie pas la convention de borne** -- debut inclus, fin exclue --
        // et c'est la campagne de defaite qui l'a montre : reecrire la condition avec
        // une fin incluse ne fait rien tomber ici, parce que le tri rend de toute
        // facon le plus recent qui couvre. La convention est eprouvee la ou elle vit,
        // sur `Goal.coversOn`, dans `GoalCoverageTest`.
        val ancien = objectif("v1", debut = LUNDI, fin = MERCREDI, kcal = 2500.0)
        val nouveau = objectif("v2", debut = MERCREDI, kcal = 2000.0)
        diary.setContent(listOf(repas(MERCREDI, kcal = 200.0)))

        val jour = calendrier(MERCREDI, MERCREDI, InMemoryGoals(listOf(ancien, nouveau))).single()

        assertEquals(2000.0, jour.goal?.kcal, "le plus recent qui couvre ce jour")
    }

    @Test
    fun `une journee anterieure au premier objectif n en porte aucun`() = runTest {
        // Elle a bien des apports, mais rien a quoi les comparer : lui appliquer
        // l'objectif d'aujourd'hui serait la juger sur une regle qu'elle n'avait pas.
        val tardif = objectif("v1", debut = LUNDI.plusDays(5), kcal = 2000.0)
        diary.setContent(listOf(repas(LUNDI, kcal = 100.0)))

        assertNull(calendrier(LUNDI, LUNDI, InMemoryGoals(listOf(tardif))).single().goal)
    }

    private suspend fun calendrier(from: LocalDate, to: LocalDate, goals: InMemoryGoals = InMemoryGoals()) =
        GetCalendar(diary, goals)(from, to).first()

    private fun repas(date: LocalDate, kcal: Double, fiber: Double? = 3.0, id: String = "d1") = Dish(
        id = DishId(id),
        date = date,
        loggedAt = Instant.parse("2026-08-10T12:00:00Z"),
        source = EntrySource.MANUAL,
        entries = listOf(
            FoodEntry(
                id = EntryId("$id-e1"),
                dishId = DishId(id),
                displayName = "Repas",
                quantity = 100.0,
                unit = "g",
                grams = 100.0,
                macros = Macros(kcal = kcal, protein = 10.0, carbs = 20.0, sugars = 5.0, fat = 3.0, fiber = fiber),
            ),
        ),
    )

    private fun objectif(id: String, debut: LocalDate, fin: LocalDate? = null, kcal: Double) = Goal(
        id = GoalId(id),
        startedAt = debut,
        endedAt = fin,
        origin = GoalOrigin.CALCULATED,
        strategy = GoalStrategy.MAINTAIN,
        daily = DailyGoal(kcal = kcal, protein = 112.0, carbs = 223.0, sugars = 50.0, fat = 67.0, fiber = 28.0),
    )

    private companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 10)
        val MARDI: LocalDate = LUNDI.plusDays(1)
        val MERCREDI: LocalDate = LUNDI.plusDays(2)
    }
}
