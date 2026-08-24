package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.diary.JOUR
import app.hexavore.domain.diary.brouillon
import app.hexavore.domain.diary.ligne
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeleteEntryTest {
    private val diary = InMemoryDiaryRepository()
    private val favoris = InMemoryFavoriteDishes()
    private val catalogue = InMemoryFoodCatalog()
    private val ids = SequentialIdGenerator()

    private val logDish = LogDish(diary, catalogue, favoris, FixedClock.atNoon(JOUR), ids)
    private val deleteEntry = DeleteEntry(diary)
    private val restoreDish = RestoreDish(diary)

    @Test
    fun `supprimer une ligne parmi d autres laisse le plat`() = runTest {
        logDish(brouillon(ligne("a", nom = "Riz"), ligne("b", nom = "Poulet")))
        val plat = diary.dishes.single()

        deleteEntry(plat, plat.entries.first().id)

        assertEquals(listOf("Poulet"), diary.dishes.single().entries.map { it.displayName })
    }

    @Test
    fun `supprimer la derniere ligne supprime le plat`() = runTest {
        // Un plat vide s'afficherait a l'accueil avec son heure et sa pastille, a
        // zero calorie : indiscernable d'une saisie reelle qui n'apporterait rien.
        logDish(brouillon(ligne("a")))
        val plat = diary.dishes.single()

        deleteEntry(plat, plat.entries.single().id)

        assertTrue(diary.dishes.isEmpty(), "un plat sans ligne n'a aucune existence")
    }

    @Test
    fun `annuler une suppression remet le plat tel quel`() = runTest {
        logDish(brouillon(ligne("a", nom = "Riz"), ligne("b", nom = "Sauce", fibres = null)))
        val avant = diary.dishes.single()

        deleteEntry(avant, avant.entries.first().id)
        restoreDish(avant)

        assertEquals(avant, diary.dishes.single())
    }

    @Test
    fun `annuler la suppression du dernier plat le fait revenir en entier`() = runTest {
        logDish(brouillon(ligne("a")))
        val avant = diary.dishes.single()

        deleteEntry(avant, avant.entries.single().id)
        restoreDish(avant)

        assertEquals(avant, diary.dishes.single())
    }
}
