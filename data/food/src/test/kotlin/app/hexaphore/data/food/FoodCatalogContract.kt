package app.hexaphore.data.food

import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.nutrition.NutrientValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Ce que **les deux** implémentations du catalogue doivent tenir.
 *
 * Six des sept ports du projet sont portés par une même paire de classes —
 * `InMemoryFoodCatalog` et `RoomFoodCatalog` — et deux défauts livrés sont passés
 * par l'écart entre elles : le faux rendait des fiches qui **existent déjà**, le vrai
 * en fabrique qui n'y sont pas encore. Un test écrit contre le faux éprouvait donc un
 * chemin que l'application n'emprunte jamais, et il passait ([D53][decisions]).
 *
 * Ce jeu est écrit une fois et exécuté deux fois. Une propriété que le faux
 * s'autorise à ne pas tenir devient un test rouge, et non une découverte sur
 * l'appareil.
 *
 * **Il tourne en JUnit 4** parce que Robolectric est un lanceur JUnit 4 et que le
 * vrai adaptateur a besoin de Room, donc d'Android — la même raison qui a fait
 * cohabiter les deux moteurs dans `:core:database` ([D35][decisions]). Le moteur
 * vintage les rassemble sous `./gradlew check`.
 *
 * **Ce qu'il ne prouve pas.** Ni l'affichage, ni l'ergonomie, ni ce qu'un `ViewModel`
 * fait de ces flux. Il porte sur le contrat des ports et sur rien d'autre.
 *
 * [decisions]: docs/11-decisions.md
 */
abstract class FoodCatalogContract {
    /**
     * Le catalogue sous test, garni.
     *
     * [stored] est ce qui est déjà écrit ; [reference] est ce que la table de l'ANSES
     * propose **sans** l'avoir copié. La seconde liste est le cœur du dispositif :
     * c'est la moitié du monde que le faux ignorait.
     */
    protected abstract fun catalogue(
        stored: List<Food> = emptyList(),
        reference: List<Food> = emptyList(),
    ): FoodCatalogView<*>

    // --- La recherche est un flux ----------------------------------------------

    @Test
    fun `epingler une fiche connue change les resultats sans relancer la recherche`() = runBlocking {
        // Le defaut corrige : les raccourcis se rafraichissaient, les resultats non,
        // parce qu'ils venaient d'une lecture unique.
        val catalogue = catalogue(stored = listOf(POMME_STOCKEE))

        val apres = catalogue.chercher(POMME).apres(
            ecriture = { catalogue.setFavorite(POMME_STOCKEE.id, true) },
            attendu = { it.pomme?.favorite == true },
        )

        assertTrue("l etoile n a pas suivi", apres.pomme!!.favorite)
    }

    @Test
    fun `supprimer une fiche la retire des resultats sans relancer la recherche`() = runBlocking {
        val catalogue = catalogue(stored = listOf(POMME_PERSONNELLE))

        val apres = catalogue.chercher(POMME_PERSONNELLE.name).apres(
            ecriture = { catalogue.delete(POMME_PERSONNELLE.id) },
            attendu = { liste -> liste.none { it.id == POMME_PERSONNELLE.id } },
        )

        assertTrue("la fiche supprimee est restee affichee", apres.none { it.id == POMME_PERSONNELLE.id })
    }

    @Test
    fun `verser une fiche au catalogue change les resultats sans relancer la recherche`() = runBlocking {
        val catalogue = catalogue(reference = listOf(POIRE_DE_REFERENCE))
        val propose = catalogue.chercher(POIRE).first().poire!!

        val apres = catalogue.chercher(POIRE).apres(
            ecriture = { catalogue.place(propose) },
            attendu = { it.poire?.id == propose.id },
        )

        assertEquals("la fiche versee devrait avoir remplace la proposition", propose.id, apres.poire!!.id)
    }

    // --- La table de reference, et ses identifiants provisoires -----------------

    @Test
    fun `une fiche de reference se trouve sans etre au catalogue`() = runBlocking {
        val catalogue = catalogue(reference = listOf(POIRE_DE_REFERENCE))

        val trouve = catalogue.chercher(POIRE).first().poire!!

        assertEquals(POIRE_DE_REFERENCE.name, trouve.name)
        assertNull("elle ne devrait pas encore etre ecrite", catalogue.byId(trouve.id))
    }

    @Test
    fun `l identifiant d une fiche de reference change a chaque recherche`() = runBlocking {
        // La propriete dont tout le reste depend : un appelant qui garde cet
        // identifiant garde une chose qui ne designe rien. Un faux qui en rendrait un
        // stable rendrait le probleme invisible -- c'est ce qui a fait ouvrir un
        // ecran de saisie vide.
        val catalogue = catalogue(reference = listOf(POIRE_DE_REFERENCE))

        val premier = catalogue.chercher(POIRE).first().poire!!.id
        val second = catalogue.chercher(POIRE).first().poire!!.id

        assertNotEquals("un identifiant provisoire ne doit pas se laisser garder", premier, second)
    }

    @Test
    fun `epingler une fiche de reference l ecrit puis l etoile`() = runBlocking {
        // Le geste que l'ecran fait : place, puis setFavorite sur l'identifiant
        // definitif. Sur le provisoire, setFavorite ne touchait aucune ligne et
        // l'etoile ne s'allumait jamais -- meme en relancant la recherche.
        val catalogue = catalogue(reference = listOf(POIRE_DE_REFERENCE))
        val propose = catalogue.chercher(POIRE).first().poire!!

        val apres = catalogue.chercher(POIRE).apres(
            ecriture = {
                val ecrite = catalogue.place(propose)
                catalogue.setFavorite(ecrite.id, true)
            },
            attendu = { it.poire?.favorite == true },
        )

        assertTrue("l etoile n a pas suivi", apres.poire!!.favorite)
        assertEquals(listOf(POIRE_DE_REFERENCE.name), catalogue.observeFavorites().first().map { it.name })
    }

    @Test
    fun `verser deux fois la meme fiche de reference n en fait pas deux`() = runBlocking {
        // Le rapprochement se fait par (source, source_ref) et non par l'identifiant,
        // precisement parce que celui-ci est le provisoire.
        val catalogue = catalogue(reference = listOf(POIRE_DE_REFERENCE))

        val premiere = catalogue.place(catalogue.chercher(POIRE).first().poire!!)
        val seconde = catalogue.place(catalogue.chercher(POIRE).first().poire!!)

        assertEquals("la fiche a ete recopiee sous un second identifiant", premiere.id, seconde.id)
        assertEquals(1, catalogue.chercher(POIRE).first().count { it.sourceRef == CODE_POIRE })
    }

    @Test
    fun `verser une fiche deja connue ne defait pas ses compteurs`() = runBlocking {
        val connue = POMME_STOCKEE.copy(useCount = 7, favorite = true)
        val catalogue = catalogue(stored = listOf(connue), reference = listOf(POMME_DE_REFERENCE))

        val rendue = catalogue.place(POMME_DE_REFERENCE.copy(id = FoodId("provisoire")))

        assertEquals("les usages ont ete remis a zero", 7, rendue.useCount)
        assertTrue("l etoile a ete perdue", rendue.favorite)
    }

    @Test
    fun `une fiche deja copiee n apparait pas deux fois`() = runBlocking {
        val catalogue = catalogue(stored = listOf(POMME_STOCKEE), reference = listOf(POMME_DE_REFERENCE))

        val trouve = catalogue.chercher(POMME).first()

        assertEquals("le doublon table / catalogue n a pas ete ecarte", 1, trouve.count { it.sourceRef == CODE_POMME })
        assertEquals(POMME_STOCKEE.id, trouve.pomme!!.id)
    }

    // --- Les autres lectures ---------------------------------------------------

    @Test
    fun `une fiche jamais servie n est pas dans les recents`() = runBlocking {
        val catalogue = catalogue(stored = listOf(POMME_STOCKEE))

        assertTrue(catalogue.observeRecent(RECENTS).first().isEmpty())
    }

    @Test
    fun `noter un usage la fait entrer dans les recents`() = runBlocking {
        val catalogue = catalogue(stored = listOf(POMME_STOCKEE))

        catalogue.remember(listOf(POMME_STOCKEE), CONSOMME_LE)

        val recents = catalogue.observeRecent(RECENTS).first()
        assertEquals(listOf(POMME_STOCKEE.id), recents.map { it.id })
        assertEquals("l usage n a pas ete compte", 1, recents.single().useCount)
    }

    @Test
    fun `noter l usage d une fiche de reference l ecrit d abord`() = runBlocking {
        // L'ordre compte : une entree de journal qui designe une fiche absente est
        // refusee par la base. C'est LogDish qui appelle remember, avant le plat.
        val catalogue = catalogue(reference = listOf(POIRE_DE_REFERENCE))
        val propose = catalogue.chercher(POIRE).first().poire!!

        catalogue.remember(listOf(propose), CONSOMME_LE)

        assertEquals(listOf(POIRE_DE_REFERENCE.name), catalogue.observeRecent(RECENTS).first().map { it.name })
    }

    @Test
    fun `depingler retire des favoris et de l affichage`() = runBlocking {
        val catalogue = catalogue(stored = listOf(POMME_STOCKEE.copy(favorite = true)))

        catalogue.setFavorite(POMME_STOCKEE.id, false)

        assertTrue(catalogue.observeFavorites().first().isEmpty())
        assertFalse(catalogue.chercher(POMME).first().pomme!!.favorite)
    }

    @Test
    fun `une valeur inconnue le reste d un bout a l autre`() = runBlocking {
        // La regle du projet, eprouvee la ou elle peut se perdre sans bruit : un
        // aller-retour par la base ne doit pas remplir les fibres avec un zero.
        val catalogue = catalogue(stored = listOf(POMME_STOCKEE))

        val relue = catalogue.byId(POMME_STOCKEE.id)

        assertNull("des fibres inconnues ne sont pas zero gramme de fibres", relue?.per100g?.fiber)
        assertEquals(PROTEINES_POMME, relue?.per100g?.protein!!, 0.0)
    }

    @Test
    fun `une recherche vide ne rend rien`() = runBlocking {
        val catalogue = catalogue(stored = listOf(POMME_STOCKEE), reference = listOf(POIRE_DE_REFERENCE))

        assertTrue(catalogue.chercher("").first().isEmpty())
    }

    @Test
    fun `les accents et la casse sont sans effet des deux cotes`() = runBlocking {
        val catalogue = catalogue(reference = listOf(CREME_BRULEE))

        val sansAccents = catalogue.chercher("creme brulee rayon frais").first()
        val avecAccents = catalogue.chercher("Crème Brûlée, rayon frais").first()

        assertEquals(1, sansAccents.count { it.sourceRef == CODE_CREME })
        assertEquals(1, avecAccents.count { it.sourceRef == CODE_CREME })
    }

    // --- Outillage -------------------------------------------------------------

    private fun FoodCatalogView<*>.chercher(query: String): Flow<List<Food>> = search(query, RESULTATS)

    private val List<Food>.pomme: Food? get() = firstOrNull { it.sourceRef == CODE_POMME }

    private val List<Food>.poire: Food? get() = firstOrNull { it.sourceRef == CODE_POIRE }

    /**
     * Ce que le flux rend **après** [ecriture], et non ce qu'il rendait avant.
     *
     * Écrit ainsi plutôt qu'en relisant après coup : une relecture passerait même si
     * le flux n'avait jamais ré-émis, c'est-à-dire même si le défaut d'origine était
     * toujours là. Ici, une lecture unique fait expirer l'attente, et le message le
     * dit.
     */
    private suspend fun Flow<List<Food>>.apres(
        ecriture: suspend () -> Unit,
        attendu: (List<Food>) -> Boolean,
    ): List<Food> = coroutineScope {
        val recu = Channel<List<Food>>(Channel.UNLIMITED)
        val collecte = launch(Dispatchers.Default) { collect { recu.send(it) } }
        try {
            withTimeout(DELAI_MILLIS) {
                recu.receive()
                ecriture()
                var valeur = recu.receive()
                while (!attendu(valeur)) valeur = recu.receive()
                valeur
            }
        } catch (_: TimeoutCancellationException) {
            error("le flux n a pas re-emis apres l ecriture : la recherche est-elle encore une lecture unique ?")
        } finally {
            collecte.cancel()
        }
    }

    protected companion object {
        /**
         * Trois vraies lignes de la table livrée.
         *
         * De vraies lignes et non des inventions : la table de l'ANSES n'est pas
         * injectable — elle est livrée dans l'APK — donc l'adaptateur Room ne peut
         * jouer ce contrat que sur des fiches qui y sont réellement. Chaque sous-classe
         * vérifie que les codes et les intitulés concordent, sinon le contrat
         * n'éprouverait rien du tout d'un côté.
         */
        const val CODE_POMME = "13039"
        const val CODE_POIRE = "13037"
        const val CODE_CREME = "39213"

        const val POMME = "pomme chair et peau crue"
        const val POIRE = "poire chair et peau crue"

        /** Le catalogue local suffit à porter la fiche : la référence n'en a pas besoin. */
        val POIRE_DE_REFERENCE = Food(
            id = FoodId("provisoire-poire"),
            source = FoodSource.CIQUAL,
            sourceRef = CODE_POIRE,
            name = "Poire, chair et peau, crue",
            per100g = NutrientValues(kcal = 56.6),
        )

        val POMME_DE_REFERENCE = Food(
            id = FoodId("provisoire-pomme"),
            source = FoodSource.CIQUAL,
            sourceRef = CODE_POMME,
            name = "Pomme, chair et peau, crue",
            per100g = NutrientValues(kcal = 54.0),
        )

        val CREME_BRULEE = Food(
            id = FoodId("provisoire-creme"),
            source = FoodSource.CIQUAL,
            sourceRef = CODE_CREME,
            name = "Crème brûlée, rayon frais",
            per100g = NutrientValues(kcal = 269.0),
        )

        const val PROTEINES_POMME = 0.3

        /** La même pomme, déjà copiée — avec des fibres **inconnues**, exprès. */
        val POMME_STOCKEE = POMME_DE_REFERENCE.copy(
            id = FoodId("f-pomme"),
            per100g = NutrientValues(kcal = 54.0, protein = PROTEINES_POMME, fiber = null),
        )

        /** Une fiche personnelle : elle seule se supprime. */
        val POMME_PERSONNELLE = Food(
            id = FoodId("f-pomme-de-mamie"),
            source = FoodSource.CUSTOM,
            sourceRef = null,
            name = "Pommes de mamie",
            per100g = NutrientValues(kcal = 60.0),
        )

        val CONSOMME_LE: Instant = Instant.parse("2026-08-10T12:00:00Z")

        const val RECENTS = 20
        const val RESULTATS = 50
        const val DELAI_MILLIS = 5_000L
    }
}
