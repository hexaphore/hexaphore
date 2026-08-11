package app.hexaphore.data.food

import app.hexaphore.core.testing.firstAfter
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodCategory
import app.hexaphore.domain.food.FoodFilter
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.food.FoodTrait
import app.hexaphore.domain.nutrition.NutrientValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

        val apres = catalogue.chercher(POMME).firstAfter(
            write = { catalogue.setFavorite(POMME_STOCKEE.id, true) },
            matching = { it.pomme?.favorite == true },
        )

        assertTrue("l etoile n a pas suivi", apres.pomme!!.favorite)
    }

    @Test
    fun `supprimer une fiche la retire des resultats sans relancer la recherche`() = runBlocking {
        val catalogue = catalogue(stored = listOf(POMME_PERSONNELLE))

        val apres = catalogue.chercher(POMME_PERSONNELLE.name).firstAfter(
            write = { catalogue.delete(POMME_PERSONNELLE.id) },
            matching = { liste -> liste.none { it.id == POMME_PERSONNELLE.id } },
        )

        assertTrue("la fiche supprimee est restee affichee", apres.none { it.id == POMME_PERSONNELLE.id })
    }

    @Test
    fun `verser une fiche au catalogue change les resultats sans relancer la recherche`() = runBlocking {
        val catalogue = catalogue(reference = listOf(POIRE_DE_REFERENCE))
        val propose = catalogue.chercher(POIRE).first().poire!!

        val apres = catalogue.chercher(POIRE).firstAfter(
            write = { catalogue.place(propose) },
            matching = { it.poire?.id == propose.id },
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

        val apres = catalogue.chercher(POIRE).firstAfter(
            write = {
                val ecrite = catalogue.place(propose)
                catalogue.setFavorite(ecrite.id, true)
            },
            matching = { it.poire?.favorite == true },
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

    // --- Le bandeau de pastilles -----------------------------------------------

    @Test
    fun `une pastille de rayon filtre les resultats`() = runBlocking {
        val catalogue = catalogue(reference = listOf(POMME_DE_REFERENCE, CREME_BRULEE))

        val fruits = catalogue.parcourir(FRUITS).first()

        assertTrue("la pomme est un fruit", fruits.any { it.sourceRef == CODE_POMME })
        assertTrue("une creme brulee n'est pas un fruit", fruits.none { it.sourceRef == CODE_CREME })
    }

    @Test
    fun `une pastille seule et un champ vide listent des aliments`() = runBlocking {
        // Le mode parcours. Sans lui, une pastille sans frappe ne rendrait rien, et
        // le bandeau ne servirait qu'a retrecir une recherche deja faite.
        val catalogue = catalogue(reference = listOf(POMME_DE_REFERENCE))

        assertTrue(catalogue.parcourir(FRUITS).first().isNotEmpty())
    }

    @Test
    fun `deux rayons se cumulent en OU`() = runBlocking {
        val catalogue = catalogue(reference = listOf(POMME_DE_REFERENCE, CREME_BRULEE))
        val deux = FoodFilter(categories = setOf(FoodCategory.FRUITS, FoodCategory.PRODUITS_LAITIERS))

        val trouve = catalogue.parcourir(deux).first()

        assertTrue("le fruit a disparu", trouve.any { it.sourceRef == CODE_POMME })
        assertTrue("le produit laitier a disparu", trouve.any { it.sourceRef == CODE_CREME })
    }

    @Test
    fun `une qualite se pose en ET par-dessus un rayon`() = runBlocking {
        // « Favori + Fruits » montre les fruits epingles. C'est la regle que le
        // domaine porte, et les deux implementations doivent la tenir pareil.
        val epinglee = POMME_STOCKEE.copy(favorite = true)
        val catalogue = catalogue(stored = listOf(epinglee), reference = listOf(POIRE_DE_REFERENCE))
        val favorisFruits = FoodFilter(setOf(FoodCategory.FRUITS), setOf(FoodTrait.FAVORITE))

        val trouve = catalogue.parcourir(favorisFruits).first()

        assertEquals(listOf(POMME_STOCKEE.id), trouve.map { it.id })
    }

    @Test
    fun `une qualite ecarte ce qui n a pas ete verse au catalogue`() = runBlocking {
        // Une ligne de la table de l'ANSES n'est ni personnelle ni epinglee tant
        // qu'elle n'a pas ete copiee. La rendre sous « Mon aliment » serait proposer
        // de supprimer une reference publiee.
        val catalogue = catalogue(stored = listOf(POMME_PERSONNELLE), reference = listOf(POIRE_DE_REFERENCE))

        val miennes = catalogue.parcourir(FoodFilter(traits = setOf(FoodTrait.PERSONAL))).first()

        assertEquals(listOf(POMME_PERSONNELLE.id), miennes.map { it.id })
    }

    @Test
    fun `un aliment personnel ne porte aucun rayon`() = runBlocking {
        // La decision, eprouvee sur les deux implementations : il ne repond qu'a
        // « Mon aliment ».
        val catalogue = catalogue(stored = listOf(POMME_PERSONNELLE))

        assertNull(catalogue.byId(POMME_PERSONNELLE.id)?.category)
        assertTrue(catalogue.parcourir(FRUITS).first().none { it.id == POMME_PERSONNELLE.id })
    }

    @Test
    fun `le rayon accompagne une fiche versee au catalogue`() = runBlocking {
        // Il n'est pas stocke dans `food` : il se relit dans la table de reference.
        // Si ce chemin cassait, une fiche copiee cesserait de repondre a sa pastille
        // -- et seulement apres avoir ete ouverte une fois, donc jamais ici.
        val catalogue = catalogue(reference = listOf(POMME_DE_REFERENCE))
        catalogue.place(catalogue.chercher(POMME).first().pomme!!)

        val apres = catalogue.parcourir(FRUITS).first()

        assertTrue("la fiche copiee a perdu son rayon", apres.any { it.sourceRef == CODE_POMME })
        assertEquals(FoodCategory.FRUITS, catalogue.chercher(POMME).first().pomme!!.category)
    }

    @Test
    fun `les pastilles filtrent aussi les recents et les favoris`() = runBlocking {
        val catalogue = catalogue(stored = listOf(POMME_STOCKEE, POMME_PERSONNELLE))
        catalogue.remember(listOf(POMME_STOCKEE, POMME_PERSONNELLE), CONSOMME_LE)

        val recents = catalogue.observeRecent(RECENTS).first()

        // Le port rend tout ; c'est la regle du domaine qui trie, et elle a besoin
        // que la categorie soit portee par la fiche pour pouvoir le faire.
        assertEquals(FoodCategory.FRUITS, recents.first { it.id == POMME_STOCKEE.id }.category)
        assertNull(recents.first { it.id == POMME_PERSONNELLE.id }.category)
        assertEquals(listOf(POMME_STOCKEE.id), recents.filter(FRUITS::matches).map { it.id })
    }

    @Test
    fun `sans pastille et sans mot, rien ne sort`() = runBlocking {
        // Le catalogue entier n'est pas une reponse : c'est l'ecran des raccourcis
        // qui occupe cet etat, pas une liste de 3 484 lignes.
        val catalogue = catalogue(stored = listOf(POMME_STOCKEE), reference = listOf(POIRE_DE_REFERENCE))

        assertTrue(catalogue.chercher("").first().isEmpty())
    }

    // --- Outillage -------------------------------------------------------------

    /**
     * Le mode parcours demande une limite large.
     *
     * La table de l'ANSES n'est pas garnissable : côté Room, « Fruits » rend les 191
     * vrais fruits, et une limite de trente écarterait la fixture par la longueur de
     * son libellé. Le contrat porterait alors sur le tri, pas sur le filtre.
     */
    private fun FoodCatalogView<*>.parcourir(filter: FoodFilter): Flow<List<Food>> = search("", filter, PARCOURS)

    private fun FoodCatalogView<*>.chercher(query: String, filter: FoodFilter = FoodFilter.NONE): Flow<List<Food>> =
        search(query, filter, RESULTATS)

    private val List<Food>.pomme: Food? get() = firstOrNull { it.sourceRef == CODE_POMME }

    private val List<Food>.poire: Food? get() = firstOrNull { it.sourceRef == CODE_POIRE }

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
            category = FoodCategory.FRUITS,
            per100g = NutrientValues(kcal = 56.6),
        )

        val POMME_DE_REFERENCE = Food(
            id = FoodId("provisoire-pomme"),
            source = FoodSource.CIQUAL,
            sourceRef = CODE_POMME,
            name = "Pomme, chair et peau, crue",
            category = FoodCategory.FRUITS,
            per100g = NutrientValues(kcal = 54.0),
        )

        /** Un autre rayon, pour éprouver que le filtre écarte vraiment. */
        val CREME_BRULEE = Food(
            id = FoodId("provisoire-creme"),
            source = FoodSource.CIQUAL,
            sourceRef = CODE_CREME,
            name = "Crème brûlée, rayon frais",
            category = FoodCategory.PRODUITS_LAITIERS,
            per100g = NutrientValues(kcal = 269.0),
        )

        val FRUITS = FoodFilter(categories = setOf(FoodCategory.FRUITS))

        const val PROTEINES_POMME = 0.3

        /** La même pomme, déjà copiée — avec des fibres **inconnues**, exprès. */
        val POMME_STOCKEE = POMME_DE_REFERENCE.copy(
            id = FoodId("f-pomme"),
            per100g = NutrientValues(kcal = 54.0, protein = PROTEINES_POMME, fiber = null),
        )

        /**
         * Une fiche personnelle : elle seule se supprime, et elle n'a **aucun rayon**.
         *
         * C'est la décision, éprouvée plutôt que déclarée : des « pommes de mamie » ne
         * sortent pas sous « Fruits », seulement sous « Mon aliment ».
         */
        val POMME_PERSONNELLE = Food(
            id = FoodId("f-pomme-de-mamie"),
            source = FoodSource.CUSTOM,
            sourceRef = null,
            name = "Pommes de mamie",
            category = null,
            per100g = NutrientValues(kcal = 60.0),
        )

        val CONSOMME_LE: Instant = Instant.parse("2026-08-10T12:00:00Z")

        const val RECENTS = 20
        const val RESULTATS = 50

        /** Assez large pour que « Fruits » rende les 191 fruits de la table livrée. */
        const val PARCOURS = 1_000
    }
}
