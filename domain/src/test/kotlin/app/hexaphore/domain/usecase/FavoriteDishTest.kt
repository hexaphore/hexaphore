package app.hexaphore.domain.usecase

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.core.testing.InMemoryFavoriteDishes
import app.hexaphore.core.testing.InMemoryFoodCatalog
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.domain.diary.FavoriteDishId
import app.hexaphore.domain.diary.JOUR
import app.hexaphore.domain.diary.brouillon
import app.hexaphore.domain.diary.ligne
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.nutrition.NutrientValues
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Enregistrer un plat favori, et le rejouer.
 *
 * Deux règles se croisent, et les deux sont invisibles quand elles cassent. Un favori
 * est un **modèle vivant** : rejouer « mes flocons du matin » doit refléter la fiche
 * courante, sans quoi corriger un aliment ne corrigerait jamais les plats qui le
 * citent. Et son nom est **unique** : deux « Petit-déj » dans une liste ne se
 * distinguent plus, et choisir devient un pari.
 */
class FavoriteDishTest {
    @Test
    fun `un brouillon devient un favori, avec son nom nettoye`() = runTest {
        val resultat = saveFavorite(brouillon(ligne("a", nom = "Riz")), "  Petit-déj  ")

        val id = (resultat as FavoriteOutcome.Saved).id
        val favori = favoris.byId(id)!!
        assertEquals("Petit-déj", favori.name, "les espaces de bord ne font pas partie du nom")
        assertEquals(listOf("Riz"), favori.components.map { it.name })
    }

    @Test
    fun `un nom deja pris est une reponse, pas une panne`() = runTest {
        // L'ecran la traduit en phrase et laisse le champ ouvert. Une exception aurait
        // oblige a distinguer, dans un runCatching, ce qui se corrige de ce qui se
        // reessaie.
        saveFavorite(brouillon(ligne("a")), "Petit-déj")

        val second = saveFavorite(brouillon(ligne("b")), "petit dej")

        assertEquals(FavoriteOutcome.NameTaken, second, "la comparaison ignore la casse et les accents")
        assertEquals(1, favoris.all.size, "rien ne devait etre ecrit")
    }

    @Test
    fun `renommer un favori ne le fait pas se heurter a lui-meme`() = runTest {
        val id = (saveFavorite(brouillon(ligne("a")), "Petit-déj") as FavoriteOutcome.Saved).id

        val resultat = saveFavorite(brouillon(ligne("a"), ligne("b")), "Petit-déj", existing = id)

        assertTrue(resultat is FavoriteOutcome.Saved)
        assertEquals(1, favoris.all.size, "renommer remplace, il ne cree pas un second favori")
        assertEquals(2, favoris.byId(id)!!.components.size)
    }

    @Test
    fun `un favori sans ligne enregistrable est refuse`() = runTest {
        // Il ne rejouerait rien. C'est une erreur d'appelant, pas une reponse a
        // afficher : l'ecran n'offre l'etoile que sur un brouillon complet.
        assertThrows<IllegalArgumentException> { saveFavorite(brouillon(), "Vide") }
        assertThrows<IllegalArgumentException> { saveFavorite(brouillon(ligne("a")), "   ") }
    }

    @Test
    fun `rejouer un favori reflete la fiche courante`() = runTest {
        // Le coeur de la promesse : corriger ses flocons corrige tous les
        // petits-dejeuners a venir. Un favori qui figerait ses valeurs ne le ferait
        // pas, et rien ne le dirait.
        catalogue.save(FLOCONS)
        val id = (saveFavorite(brouillon(ligneDeFiche()), "Petit-déj") as FavoriteOutcome.Saved).id

        catalogue.save(FLOCONS.copy(name = "Flocons complets", per100g = FLOCONS.per100g.copy(kcal = 400.0)))
        val rejoue = getFavoriteDraft(id)!!

        val ligne = rejoue.lines.single()
        assertEquals("Flocons complets", ligne.name, "le nom suit la fiche vivante")
        assertEquals(240.0, ligne.values.kcal, "60 g de la fiche corrigee, et non les valeurs figees")
    }

    @Test
    fun `rejouer un favori dont la fiche a disparu garde la ligne`() = runTest {
        // Les valeurs enregistrees avec le favori prennent le relais. Refuser la
        // ligne, ou rejouer un plat ampute sans le dire, seraient deux facons de
        // perdre un favori pour un aliment supprime.
        catalogue.save(FLOCONS)
        val id = (saveFavorite(brouillon(ligneDeFiche()), "Petit-déj") as FavoriteOutcome.Saved).id

        catalogue.delete(FLOCONS.id)
        val rejoue = getFavoriteDraft(id)!!

        val ligne = rejoue.lines.single()
        assertEquals("Flocons", ligne.name, "le nom fige prend le relais")
        assertEquals(218.0, ligne.values.kcal)
    }

    @Test
    fun `un brouillon rejoue porte le lien vers son favori`() = runTest {
        val id = (saveFavorite(brouillon(ligne("a")), "Petit-déj") as FavoriteOutcome.Saved).id

        val rejoue = getFavoriteDraft(id)!!

        assertEquals(id, rejoue.favoriteId, "c'est lui qui allume l etoile")
        assertNull(rejoue.dishId, "rejouer compose un plat neuf, il n en modifie aucun")
        assertEquals(JOUR, rejoue.date)
    }

    @Test
    fun `un favori supprime entre le choix et l ouverture ne rejoue rien`() = runTest {
        assertNull(getFavoriteDraft(FavoriteDishId("jamais-ecrit")))
    }

    @Test
    fun `enregistrer un plat rejoue marque le favori comme utilise et garde le lien`() = runTest {
        val id = (saveFavorite(brouillon(ligne("a")), "Petit-déj") as FavoriteOutcome.Saved).id
        val rejoue = getFavoriteDraft(id)!!

        val dishId = LogDish(diary, catalogue, favoris, clock, ids)(rejoue)

        assertEquals(1, favoris.byId(id)!!.useCount, "la liste doit remonter ce qui sert")
        assertEquals(id, diary.dishes.single { it.id == dishId }.favoriteId)
    }

    @Test
    fun `deux rejeux du meme favori font deux plats distincts`() = runTest {
        // Les composants ne portent ni identifiant de ligne ni identifiant d'entree :
        // les trainer aurait fait ecrire le second plat par-dessus le premier.
        val id = (saveFavorite(brouillon(ligne("a")), "Petit-déj") as FavoriteOutcome.Saved).id
        val logDish = LogDish(diary, catalogue, favoris, clock, ids)

        val premier = logDish(getFavoriteDraft(id)!!)
        val second = logDish(getFavoriteDraft(id)!!)

        assertNotNull(diary.dishes.firstOrNull { it.id == premier })
        assertEquals(2, diary.dishes.size, "le second rejeu a ecrase le premier")
        assertTrue(premier != second)
    }

    // --- Montage -----------------------------------------------------------------

    private val diary = InMemoryDiaryRepository()
    private val catalogue = InMemoryFoodCatalog()
    private val favoris = InMemoryFavoriteDishes()
    private val clock = FixedClock.atNoon(JOUR)
    private val ids = SequentialIdGenerator("fav")

    private val saveFavorite = SaveFavoriteDish(favoris, ids)
    private val getFavoriteDraft = GetFavoriteDraft(favoris, catalogue, clock, ids)

    /** Une ligne issue d'une fiche : c'est celle qui suivra la fiche vivante. */
    private fun ligneDeFiche() = ligne("a", nom = "Flocons", quantite = 60.0, kcal = 218.0).copy(
        foodId = FLOCONS.id,
        reference = FLOCONS.per100g,
    )

    private companion object {
        val FLOCONS = Food(
            id = FoodId("food-flocons"),
            source = FoodSource.CIQUAL,
            name = "Flocons",
            per100g = NutrientValues(kcal = 363.0, protein = 13.5, carbs = 60.0, sugars = 1.0, fat = 7.0, fiber = 10.0),
        )
    }
}
