package app.hexaphore.data.diary

import app.hexaphore.core.testing.firstAfter
import app.hexaphore.domain.diary.FavoriteComponent
import app.hexaphore.domain.diary.FavoriteDish
import app.hexaphore.domain.diary.FavoriteDishId
import app.hexaphore.domain.diary.FavoriteDishes
import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.nutrition.NutrientValues
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que **les deux** implémentations des plats favoris doivent tenir.
 *
 * Le port naît avec deux implémentations, donc il naît avec son contrat : c'est la
 * règle que [D53][decisions] a posée et que [D58][decisions] a fini d'appliquer aux
 * sept ports existants. L'écrire après coup aurait laissé le temps aux deux côtés de
 * diverger, et c'est exactement ce qui a produit quatre défauts livrés.
 *
 * **Deux propriétés méritent d'être nommées ici**, parce qu'un faux les tient trop
 * facilement par accident : la comparaison de noms ignore la casse et les accents, et
 * l'ordre des composants survit à un aller-retour. La première décide d'un message
 * d'erreur, la seconde de la tête qu'aura un plat rejoué.
 *
 * **Ce qu'il ne prouve pas.** Ni le rejeu — c'est `GetFavoriteDraft` qui le fait, et il
 * relit des fiches que ce port ne connaît pas — ni la délision d'un plat du journal
 * quand son favori disparaît : celle-là est une clé étrangère, et elle est éprouvée
 * dans le test de migration, où la base est réelle.
 *
 * [decisions]: docs/11-decisions.md
 */
abstract class FavoriteDishContract {
    /** Le magasin sous test, vide. Tout ce que les cas contiennent y entre par le port. */
    protected abstract fun favorites(): FavoriteDishes

    @Test
    fun `sans favori, la liste est vide`() = runBlocking {
        assertEquals(emptyList<FavoriteDish>(), favorites().observeAll().first())
    }

    @Test
    fun `un favori enregistre se relit entier`() = runBlocking {
        val magasin = favorites()

        magasin.save(PETIT_DEJ)

        // L'objet entier, et non trois champs choisis : ce qui se perd dans un
        // aller-retour se perd toujours dans le champ qu'on n'a pas regarde.
        assertEquals(PETIT_DEJ, magasin.byId(PETIT_DEJ.id))
    }

    @Test
    fun `un favori inconnu ne rend rien`() = runBlocking {
        assertNull(favorites().byId(FavoriteDishId("jamais-ecrit")))
    }

    @Test
    fun `l ordre des composants survit a l aller-retour`() = runBlocking {
        // Rien ne planterait s'il se perdait : le plat rejoue n'aurait simplement
        // plus la tete qu'on lui connaissait, un rejeu sur deux.
        val magasin = favorites()

        magasin.save(PETIT_DEJ)

        assertEquals(
            listOf("Flocons", "Lait", "Banane"),
            magasin.byId(PETIT_DEJ.id)!!.components.map { it.name },
        )
    }

    @Test
    fun `une valeur inconnue reste inconnue`() = runBlocking {
        // La regle la plus couteuse du projet (D29), a l'endroit ou une recopie de
        // six colonnes la trahit le plus facilement. Les sucres a zero sont dans la
        // meme ligne que les fibres nulles : c'est la paire qui rend la confusion
        // visible si elle a lieu.
        val magasin = favorites()

        magasin.save(PETIT_DEJ)

        val banane = magasin.byId(PETIT_DEJ.id)!!.components.last()
        assertNull("les fibres inconnues sont devenues un chiffre", banane.values.fiber)
        assertEquals(0.0, banane.values.sugars)
    }

    @Test
    fun `enregistrer un favori deja ecrit le remplace, il n en cree pas un second`() = runBlocking {
        val magasin = favorites()
        magasin.save(PETIT_DEJ)

        magasin.save(PETIT_DEJ.copy(name = "Petit-dejeuner", components = listOf(FLOCONS)))

        val relu = magasin.byId(PETIT_DEJ.id)!!
        assertEquals("Petit-dejeuner", relu.name)
        assertEquals("les composants retires devaient partir", 1, relu.components.size)
        assertEquals(1, magasin.observeAll().first().size)
    }

    @Test
    fun `un nom deja pris est signale, accents et casse compris`() = runBlocking {
        // C'est ce qui decide du message « Un plat en favori porte deja ce nom ».
        // Un faux qui comparerait la chaine brute laisserait passer le doublon, et
        // seul l'index unique de la base le refuserait -- trop tard, et sans phrase.
        val magasin = favorites()
        magasin.save(PETIT_DEJ)

        assertTrue(magasin.nameTaken("Petit-déj"))
        assertTrue(magasin.nameTaken("petit dej"))
        assertTrue(magasin.nameTaken("  PETIT-DEJ  "))
        assertFalse(magasin.nameTaken("Dejeuner"))
    }

    @Test
    fun `un favori ne se heurte pas a lui-meme quand on le renomme`() = runBlocking {
        val magasin = favorites()
        magasin.save(PETIT_DEJ)

        assertFalse(
            "renommer un favori en gardant son nom aurait ete refuse",
            magasin.nameTaken("Petit-déj", excluding = PETIT_DEJ.id),
        )
    }

    @Test
    fun `supprimer un favori le retire de la liste`() = runBlocking {
        val magasin = favorites()
        magasin.save(PETIT_DEJ)

        magasin.delete(PETIT_DEJ.id)

        assertNull(magasin.byId(PETIT_DEJ.id))
        assertEquals(emptyList<FavoriteDish>(), magasin.observeAll().first())
    }

    @Test
    fun `supprimer un favori libere son nom`() = runBlocking {
        val magasin = favorites()
        magasin.save(PETIT_DEJ)

        magasin.delete(PETIT_DEJ.id)

        assertFalse("le nom devait redevenir disponible", magasin.nameTaken("Petit-déj"))
    }

    @Test
    fun `un rejeu remonte le favori dans la liste`() = runBlocking {
        val magasin = favorites()
        magasin.save(PETIT_DEJ)
        magasin.save(DEJEUNER)

        magasin.markUsed(DEJEUNER.id)

        assertEquals(listOf(DEJEUNER.id, PETIT_DEJ.id), magasin.observeAll().first().map { it.id })
    }

    @Test
    fun `le compteur d usage s incremente et se relit`() = runBlocking {
        val magasin = favorites()
        magasin.save(PETIT_DEJ)

        magasin.markUsed(PETIT_DEJ.id)
        magasin.markUsed(PETIT_DEJ.id)

        assertEquals(2, magasin.byId(PETIT_DEJ.id)!!.useCount)
    }

    @Test
    fun `le flux se dement apres une ecriture`() = runBlocking {
        // Une relecture apres coup passerait meme si le flux n'avait jamais re-emis,
        // c'est-a-dire meme si le port etait reste une lecture unique.
        val magasin = favorites()
        magasin.save(PETIT_DEJ)

        val apres = magasin.observeAll().firstAfter(
            write = { magasin.save(DEJEUNER) },
            matching = { it.size == 2 },
        )

        assertEquals(setOf(PETIT_DEJ.id, DEJEUNER.id), apres.map { it.id }.toSet())
    }

    private companion object {
        val FLOCONS = FavoriteComponent(
            name = "Flocons",
            quantity = 60.0,
            unit = QuantityUnit.Gram,
            grams = 60.0,
            values = NutrientValues(kcal = 218.0, protein = 8.1, carbs = 36.0, sugars = 0.6, fat = 4.2, fiber = 6.0),
        )

        val LAIT = FavoriteComponent(
            name = "Lait",
            quantity = 200.0,
            unit = QuantityUnit.Millilitre,
            grams = 206.0,
            values = NutrientValues(kcal = 94.0, protein = 6.6, carbs = 9.6, sugars = 9.6, fat = 3.2, fiber = 0.0),
        )

        /** Une ligne tapée à la main : pas de fiche, et des fibres inconnues. */
        val BANANE = FavoriteComponent(
            foodId = null,
            name = "Banane",
            quantity = 1.0,
            unit = QuantityUnit.Serving("piece", 120.0),
            grams = 120.0,
            values = NutrientValues(kcal = 107.0, protein = 1.3, carbs = 24.0, sugars = 0.0, fat = 0.3, fiber = null),
        )

        val PETIT_DEJ = FavoriteDish(
            id = FavoriteDishId("fav-petit-dej"),
            name = "Petit-déj",
            components = listOf(FLOCONS, LAIT, BANANE),
        )

        val DEJEUNER = FavoriteDish(
            id = FavoriteDishId("fav-dejeuner"),
            name = "Déjeuner rapide",
            components = listOf(FLOCONS),
        )
    }
}
