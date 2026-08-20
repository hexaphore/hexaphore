package app.hexaphore.domain.usecase

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.core.testing.InMemoryFavoriteDishes
import app.hexaphore.core.testing.InMemoryFoodCatalog
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.JOUR
import app.hexaphore.domain.diary.brouillon
import app.hexaphore.domain.diary.ligne
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.nutrition.NutrientValues
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LogDishTest {
    private val diary = InMemoryDiaryRepository()
    private val favoris = InMemoryFavoriteDishes()
    private val catalogue = InMemoryFoodCatalog()
    private val clock = FixedClock.atNoon(JOUR)
    private val ids = SequentialIdGenerator()

    private val logDish = LogDish(diary, catalogue, favoris, clock, ids)

    @Test
    fun `enregistre les lignes du brouillon`() = runTest {
        logDish(brouillon(ligne("a", nom = "Riz"), ligne("b", nom = "Poulet")))

        val plat = diary.dishes.single()
        assertEquals(JOUR, plat.date)
        assertEquals(listOf("Riz", "Poulet"), plat.entries.map { it.displayName })
    }

    @Test
    fun `une fiche deja au catalogue est citee sous son identifiant range`() = runTest {
        // Le defaut qui rendait un plat de l'IA inenregistrable. Un resultat de
        // recherche porte un identifiant **provisoire** ; si la fiche est deja au
        // catalogue, elle y garde le sien, et une entree qui citerait le provisoire
        // designerait une fiche absente -- ce que la base refuse.
        val rangee = ciqual(id = "f-riz-range", ref = "9104")
        catalogue.save(rangee)
        val provisoire = ciqual(id = "provisoire-1", ref = "9104")

        logDish(brouillon(ligne("a", fiche = provisoire)))

        assertEquals(rangee.id, diary.dishes.single().entries.single().foodId)
    }

    @Test
    fun `une fiche inconnue garde l identifiant qu elle portait`() = runTest {
        // Le cas courant : la fiche entre au catalogue sous l'identifiant que la
        // ligne connait, et il n'y a rien a reconcilier.
        val fiche = ciqual(id = "f-poulet", ref = "36001")

        logDish(brouillon(ligne("a", fiche = fiche)))

        assertEquals(fiche.id, diary.dishes.single().entries.single().foodId)
    }

    @Test
    fun `chaque ligne recoit un identifiant du generateur`() = runTest {
        val id = logDish(brouillon(ligne("a"), ligne("b")))

        // Le plat d'abord, puis ses lignes : ce que le test verifie n'est pas
        // l'ordre lui-meme, c'est que rien ne vienne du hasard ambiant.
        assertEquals("id-1", id.value)
        assertEquals(listOf("id-2", "id-3"), diary.dishes.single().entries.map { it.id.value })
    }

    @Test
    fun `l heure vient de l horloge et non de la journee du brouillon`() = runTest {
        // Un diner note a 0 h 30 appartient a la veille : la journee et l'instant ne
        // se deduisent pas l'un de l'autre, et c'est pour ca que les deux existent.
        logDish(brouillon(ligne("a"), date = JOUR.minusDays(1)))

        val plat = diary.dishes.single()
        assertEquals(JOUR.minusDays(1), plat.date)
        assertEquals(clock.instant, plat.loggedAt)
    }

    @Test
    fun `une valeur inconnue traverse l enregistrement sans devenir zero`() = runTest {
        logDish(brouillon(ligne("a", fibres = null)))

        assertNull(
            diary.dishes.single().entries.single().macros.fiber,
            "des fibres non renseignees ne sont pas zero gramme de fibres",
        )
    }

    @Test
    fun `la quantite est convertie en grammes`() = runTest {
        logDish(brouillon(ligne("a", quantite = 250.0)))

        val entree = diary.dishes.single().entries.single()
        assertEquals(250.0, entree.quantity)
        assertEquals(250.0, entree.grams)
        assertEquals("g", entree.unit)
    }

    @Test
    fun `refuse un brouillon dont une ligne est incomplete`() = runTest {
        val incomplet = brouillon(ligne("a"), ligne("b", kcal = null))

        assertThrows<IllegalArgumentException> { logDish(incomplet) }
        assertEquals(0, diary.dishes.size, "rien ne doit etre ecrit quand la saisie est refusee")
    }

    @Test
    fun `refuse un brouillon sans aucune ligne`() = runTest {
        assertThrows<IllegalArgumentException> { logDish(brouillon()) }
    }

    @Test
    fun `la source du brouillon devient celle du plat`() = runTest {
        logDish(brouillon(ligne("a"), source = EntrySource.FAVORITE))

        assertEquals(EntrySource.FAVORITE, diary.dishes.single().source)
        assertNotEquals(EntrySource.MANUAL, diary.dishes.single().source)
    }
}

/** Une fiche de l'ANSES, telle que la recherche en rend une. */
private fun ciqual(id: String, ref: String) = Food(
    id = FoodId(id),
    source = FoodSource.CIQUAL,
    sourceRef = ref,
    name = "Riz blanc, cuit",
    per100g = NutrientValues(kcal = 130.0),
)
