package app.hexaphore.feature.search

import app.hexaphore.core.testing.InMemoryFoodCatalog
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.nutrition.NutrientValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Les deux règles d'ergonomie de saisie de [D23][decisions], éprouvées sur le temps
 * simulé plutôt que sur un chronomètre.
 *
 * Elles ne se voient pas à l'œil : une recherche sans anti-rebond marche
 * parfaitement, elle clignote seulement. Et un seuil à trois caractères ne se
 * remarque que le jour où on cherche « riz ».
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Deux réserves, comme la vraie.
     *
     * [BLE] n'est **pas** au catalogue : c'est ce que la table de l'ANSES propose
     * sans l'avoir copié. Sans cette moitié-là, le faux ne rendait que des fiches
     * déjà écrites — et un test écrit contre lui éprouvait un chemin que
     * l'application n'emprunte jamais ([D53][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    private val catalogue = InMemoryFoodCatalog(initial = listOf(RIZ, RIZ_COMPLET), reference = listOf(BLE))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `avant toute frappe, l ecran montre les raccourcis`() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertInstanceOf(SearchUiState.Shortcuts::class.java, viewModel.uiState.value)
    }

    @Test
    fun `un seul caractere ne lance rien`() = runTest(dispatcher) {
        // Le seuil de docs/12. A un caractere, la moitie du catalogue
        // correspondrait, et la liste serait du bruit.
        val viewModel = collecting()

        viewModel.onQueryChange("r")
        advanceUntilIdle()

        assertInstanceOf(SearchUiState.Shortcuts::class.java, viewModel.uiState.value)
    }

    @Test
    fun `deux caracteres suffisent`() = runTest(dispatcher) {
        // « riz », « the », « oeuf » sont des aliments courants qu'un seuil a trois
        // rendrait introuvables tant que le mot n'est pas fini.
        val viewModel = collecting()

        viewModel.onQueryChange("ri")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertInstanceOf(SearchUiState.Results::class.java, state)
        assertEquals(listOf(RIZ, RIZ_COMPLET), (state as SearchUiState.Results).foods)
    }

    @Test
    fun `la requete attend cent vingt millisecondes`() = runTest(dispatcher) {
        val viewModel = collecting()

        viewModel.onQueryChange("riz")
        advanceTimeBy(BEFORE_DEADLINE)

        assertInstanceOf(SearchUiState.Shortcuts::class.java, viewModel.uiState.value)
    }

    @Test
    fun `une frappe annule la precedente`() = runTest(dispatcher) {
        // Sans cela, taper « chocolat » lance huit recherches dont sept sont jetees,
        // et les resultats clignotent pendant qu'on ecrit.
        val viewModel = collecting()

        viewModel.onQueryChange("ri")
        advanceTimeBy(BEFORE_DEADLINE)
        viewModel.onQueryChange("riz complet")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(RIZ_COMPLET), (state as SearchUiState.Results).foods)
    }

    @Test
    fun `revenir sous le seuil rend les raccourcis`() = runTest(dispatcher) {
        val viewModel = collecting()
        viewModel.onQueryChange("riz")
        advanceUntilIdle()

        viewModel.onQueryChange("r")
        advanceUntilIdle()

        assertInstanceOf(SearchUiState.Shortcuts::class.java, viewModel.uiState.value)
    }

    @Test
    fun `une recherche sans resultat propose de creer`() = runTest(dispatcher) {
        val viewModel = collecting()

        viewModel.onQueryChange("pâtes de mamie")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertInstanceOf(SearchUiState.Empty::class.java, state)
        assertEquals("pâtes de mamie", (state as SearchUiState.Empty).query)
    }

    @Test
    fun `un catalogue illisible se dit au lieu de rendre une liste vide`() = runTest(dispatcher) {
        // Une liste vide est une affirmation -- « rien ne correspond » -- et non une
        // absence de reponse. Les confondre proposerait de creer un aliment qui
        // existe peut-etre deja.
        val viewModel = collecting()
        catalogue.failure = true

        viewModel.onQueryChange("riz")
        advanceUntilIdle()

        assertInstanceOf(SearchUiState.Error::class.java, viewModel.uiState.value)
    }

    @Test
    fun `choisir un aliment de la table le verse au catalogue`() = runTest(dispatcher) {
        // Le defaut corrige en tranche 3 : un resultat de la table de l'ANSES porte
        // un identifiant provisoire tant qu'il n'est pas ecrit. Le rendre tel quel
        // faisait chercher une fiche inexistante, et l'ecran de saisie s'ouvrait vide.
        val viewModel = collecting()
        viewModel.onQueryChange("ble")
        advanceUntilIdle()
        val propose = (viewModel.uiState.value as SearchUiState.Results).foods.single()

        viewModel.onPick(propose)
        advanceUntilIdle()

        assertEquals(propose.id, viewModel.picked.value)
        assertTrue(catalogue.all.any { it.id == propose.id }, "la fiche aurait du entrer au catalogue")
    }

    @Test
    fun `choisir un aliment deja copie rend l identifiant de la fiche existante`() = runTest(dispatcher) {
        // Et non le provisoire qu'on lui presente. Le rapprochement se fait par
        // (source, source_ref), parce que l'identifiant est justement celui qui
        // change a chaque recherche : rendre le provisoire recopierait la fiche et
        // remettrait ses compteurs d'usage a zero.
        val viewModel = collecting()

        viewModel.onPick(RIZ.copy(id = FoodId("provisoire")))
        advanceUntilIdle()

        assertEquals(RIZ.id, viewModel.picked.value)
        assertEquals(2, catalogue.all.size, "la fiche a ete recopiee sous un second identifiant")
    }

    @Test
    fun `supprimer une fiche demande confirmation et dit ce qu elle coute`() = runTest(dispatcher) {
        val viewModel = collecting()
        catalogue.usages = mapOf(RIZ.id to 12)

        viewModel.onDeleteRequested(RIZ)
        advanceUntilIdle()

        assertEquals(12, viewModel.deletion.value?.usedEntries)
        assertTrue(catalogue.all.any { it.id == RIZ.id }, "rien n'est supprime avant confirmation")
    }

    @Test
    fun `la suppression confirmee retire la fiche`() = runTest(dispatcher) {
        val viewModel = collecting()
        viewModel.onDeleteRequested(RIZ)
        advanceUntilIdle()

        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        assertTrue(catalogue.all.none { it.id == RIZ.id })
    }

    @Test
    fun `epingler un aliment le fait apparaitre en favori`() = runTest(dispatcher) {
        val viewModel = collecting()

        viewModel.onToggleFavorite(RIZ)
        advanceUntilIdle()

        val state = viewModel.uiState.value as SearchUiState.Shortcuts
        assertEquals(listOf(RIZ.id), state.favorites.map { it.id })
    }

    @Test
    fun `epingler un aliment de la table allume son etoile dans les resultats`() = runTest(dispatcher) {
        // Le defaut : un resultat de la table de l'ANSES porte un identifiant
        // provisoire tant qu'il n'est pas verse au catalogue, et `setFavorite` sur
        // cet identifiant ne mettait a jour aucune ligne. L'etoile ne s'allumait
        // donc jamais sur un aliment neuf -- meme en relancant la recherche.
        val viewModel = collecting()
        viewModel.onQueryChange("ble")
        advanceUntilIdle()
        val propose = (viewModel.uiState.value as SearchUiState.Results).foods.single()

        viewModel.onToggleFavorite(propose)
        advanceUntilIdle()

        val apres = viewModel.uiState.value as SearchUiState.Results
        assertTrue(apres.foods.single().favorite, "l etoile n a pas suivi dans les resultats")
    }

    @Test
    fun `supprimer une fiche la retire des resultats sans relancer la recherche`() = runTest(dispatcher) {
        // Meme forme, autre geste : les resultats venaient d'une lecture unique, donc
        // rien de ce qu'on ecrivait dans le catalogue ne les atteignait.
        val viewModel = collecting()
        viewModel.onQueryChange("riz")
        advanceUntilIdle()

        viewModel.onDeleteRequested(RIZ)
        advanceUntilIdle()
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val apres = viewModel.uiState.value as SearchUiState.Results
        assertTrue(apres.foods.none { it.id == RIZ.id }, "la fiche supprimee est restee affichee")
    }

    // --- Outillage ------------------------------------------------------------

    private fun viewModel() = SearchViewModel(catalogue, catalogue, catalogue, catalogue)

    /**
     * Un `ViewModel` dont l'état est collecté.
     *
     * `stateIn(WhileSubscribed)` ne produit rien tant que personne n'écoute : sans
     * cet abonnement, tous les tests verraient la valeur initiale et passeraient
     * pour de mauvaises raisons.
     */
    private fun TestScope.collecting(): SearchViewModel {
        val viewModel = viewModel()
        backgroundScope.collect(viewModel)
        advanceUntilIdle()
        return viewModel
    }

    private fun CoroutineScope.collect(viewModel: SearchViewModel) {
        launch { viewModel.uiState.collect { } }
    }

    private companion object {
        /** Juste avant l'échéance de l'anti-rebond, en millisecondes. */
        const val BEFORE_DEADLINE = 100L

        val RIZ = Food(
            id = FoodId("f-riz"),
            source = FoodSource.CIQUAL,
            sourceRef = "9104",
            name = "Riz blanc, cuit",
            per100g = NutrientValues(kcal = 155.0),
        )

        val RIZ_COMPLET = Food(
            id = FoodId("f-riz-complet"),
            source = FoodSource.CIQUAL,
            sourceRef = "9105",
            name = "Riz complet, cuit",
            per100g = NutrientValues(kcal = 150.0),
        )

        /** Proposé par la table, jamais copié : son identifiant est provisoire. */
        val BLE = Food(
            id = FoodId("provisoire"),
            source = FoodSource.CIQUAL,
            sourceRef = "9010",
            name = "Ble tendre, cuit",
            per100g = NutrientValues(kcal = 130.0),
        )
    }
}
