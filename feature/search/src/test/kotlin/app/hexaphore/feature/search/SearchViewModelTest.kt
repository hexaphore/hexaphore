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
    private val catalogue = InMemoryFoodCatalog(listOf(RIZ, RIZ_COMPLET))

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
    fun `epingler un aliment le fait apparaitre en favori`() = runTest(dispatcher) {
        val viewModel = collecting()

        viewModel.onToggleFavorite(RIZ)
        advanceUntilIdle()

        val state = viewModel.uiState.value as SearchUiState.Shortcuts
        assertEquals(listOf(RIZ.id), state.favorites.map { it.id })
    }

    // --- Outillage ------------------------------------------------------------

    private fun viewModel() = SearchViewModel(catalogue, catalogue, catalogue)

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
    }
}
