package app.hexaphore.feature.home

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.core.testing.SampleDiary
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.usecase.GetDaySummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val jour = LocalDate.of(2026, 3, 15)

    /**
     * `viewModelScope` s'exécute sur `Dispatchers.Main`, qui n'existe pas dans une
     * JVM nue. On le remplace ici plutôt que de le contourner : le ViewModel doit
     * être testé tel qu'il tourne.
     */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `expose le resume de la journee de l'horloge`() = runTest(dispatcher) {
        val clock = FixedClock.atNoon(jour)
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))

        val state = viewModel(diary, clock).uiState.filterIsInstance<HomeUiState.Content>().first()

        assertEquals(jour, state.summary.date)
        assertEquals(3, state.summary.dishes.size)
        assertTrue(state.summary.logged)
    }

    @Test
    fun `chaque plat porte ses six apports`() = runTest(dispatcher) {
        val clock = FixedClock.atNoon(jour)
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))

        val state = viewModel(diary, clock).uiState.filterIsInstance<HomeUiState.Content>().first()

        val premier = state.summary.dishes.first()
        assertTrue(
            premier.totals[Macro.PROTEIN].value > 0.0,
            "un plat doit exposer ses proteines, pas seulement ses calories",
        )
        assertEquals(EntrySource.SEARCH, premier.dish.source)
    }

    @Test
    fun `un total ampute reste signale jusqu'a l'ecran`() = runTest(dispatcher) {
        val clock = FixedClock.atNoon(jour)
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))

        val state = viewModel(diary, clock).uiState.filterIsInstance<HomeUiState.Content>().first()

        assertFalse(
            state.summary.totals[Macro.FIBER].complete,
            "la sauce du jeu de demonstration n'a pas de valeur de fibres",
        )
        assertTrue(state.summary.totals[Macro.PROTEIN].complete)
    }

    @Test
    fun `une journee sans saisie n'est pas une journee a zero`() = runTest(dispatcher) {
        val clock = FixedClock.atNoon(jour)

        val state = viewModel(InMemoryDiaryRepository(), clock).uiState
            .filterIsInstance<HomeUiState.Content>()
            .first()

        assertFalse(state.summary.logged)
        assertEquals(0.0, state.summary.totals[Macro.CALORIES].value)
    }

    @Test
    fun `une lecture qui echoue ne se lit pas comme une journee vide`() = runTest(dispatcher) {
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))
        diary.failure = IllegalStateException("base illisible")

        val state = viewModel(diary, FixedClock.atNoon(jour)).uiState
            .filterIsInstance<HomeUiState.Error>()
            .first()

        assertEquals(
            HomeUiState.Error,
            state,
            "un echec de lecture doit se dire, pas s'afficher comme une journee sans saisie",
        )
    }

    @Test
    fun `reessayer relit le journal`() = runTest(dispatcher) {
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))
        diary.failure = IllegalStateException("base illisible")
        val viewModel = viewModel(diary, FixedClock.atNoon(jour))
        viewModel.uiState.filterIsInstance<HomeUiState.Error>().first()

        diary.failure = null
        viewModel.retry()

        val state = viewModel.uiState.filterIsInstance<HomeUiState.Content>().first()
        assertEquals(3, state.summary.dishes.size)
    }

    private fun viewModel(diary: InMemoryDiaryRepository, clock: FixedClock) =
        HomeViewModel(GetDaySummary(diary, clock), TestDispatchers(dispatcher))

    /** Tout sur le dispatcher de test : aucun vrai pool de threads dans un test. */
    private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
    }
}
