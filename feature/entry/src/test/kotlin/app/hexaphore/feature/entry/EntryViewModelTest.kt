package app.hexaphore.feature.entry

import androidx.lifecycle.SavedStateHandle
import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.core.testing.InMemoryFoodCatalog
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.DraftLineId
import app.hexaphore.domain.diary.EntryId
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.FoodEntry
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.Macros
import app.hexaphore.domain.usecase.CreateDraft
import app.hexaphore.domain.usecase.GetDaySummary
import app.hexaphore.domain.usecase.GetDishDraft
import app.hexaphore.domain.usecase.LogDish
import app.hexaphore.domain.usecase.UpdateDish
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val jour = LocalDate.of(2026, 3, 15)
    private val clock = FixedClock.atNoon(jour)
    private val diary = InMemoryDiaryRepository()
    private val catalogue = InMemoryFoodCatalog()
    private val ids = SequentialIdGenerator()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `une saisie neuve ouvre sur une ligne vide et non enregistrable`() = runTest(dispatcher) {
        val state = viewModel().content()

        assertEquals(1, state.form.lines.size)
        assertFalse(state.saveable, "une ligne vide n'est pas une saisie")
        assertEquals(EntrySource.MANUAL, state.form.source)
        assertEquals(jour, state.form.date, "la journee vient de l'horloge, jamais de LocalDate.now()")
    }

    @Test
    fun `l ecran accepte plusieurs lignes des le depart`() = runTest(dispatcher) {
        // Le piege central du projet : ecrit pour une ligne, cet ecran serait a
        // reecrire a chaque nouveau mode de saisie.
        val viewModel = viewModel()

        viewModel.onAddLine()
        viewModel.onAddLine()

        assertEquals(3, viewModel.content().form.lines.size)
    }

    @Test
    fun `une ligne complete rend la saisie enregistrable`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val ligne = viewModel.content().form.lines.single().id

        viewModel.onLineEdit(ligne, LineEdit.Name("Riz"))
        viewModel.onLineEdit(ligne, LineEdit.Quantity("150"))
        viewModel.onLineEdit(ligne, LineEdit.MacroValue(Macro.CALORIES, "195"))

        assertTrue(viewModel.content().saveable)
    }

    @Test
    fun `une seule ligne incomplete suffit a bloquer l enregistrement`() = runTest(dispatcher) {
        // Enregistrer silencieusement une ligne a moitie remplie serait ecrire une
        // donnee que personne n'a saisie.
        val viewModel = viewModel()
        val premiere = viewModel.content().form.lines.single().id
        remplir(viewModel, premiere)

        viewModel.onAddLine()

        assertFalse(viewModel.content().saveable)
    }

    @Test
    fun `enregistrer ecrit un plat avec toutes ses lignes`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        viewModel.onAddLine()
        remplir(viewModel, viewModel.content().form.lines.last().id, nom = "Poulet")

        viewModel.onSave()

        assertEquals(EntryUiState.Saved, viewModel.uiState.value)
        assertEquals(listOf("Riz", "Poulet"), diary.dishes.single().entries.map { it.displayName })
    }

    @Test
    fun `un champ de macro laisse vide ressort inconnu dans le journal`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)

        viewModel.onSave()

        assertNull(
            diary.dishes.single().entries.single().macros.fiber,
            "un champ vide veut dire inconnu, et il doit le rester jusqu'au journal",
        )
    }

    @Test
    fun `supprimer une ligne la retire du brouillon`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAddLine()
        val aSupprimer = viewModel.content().form.lines.first().id

        viewModel.onRemoveLine(aSupprimer)

        assertEquals(1, viewModel.content().form.lines.size)
    }

    @Test
    fun `le restant tient compte de ce qui est deja note`() = runTest(dispatcher) {
        diary.setContent(listOf(platDeja(kcal = 600.0)))
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id, kcal = "500")

        val impact = viewModel.content().impact!!

        assertEquals(500.0, impact.draftKcal)
        assertEquals(DailyGoal.Placeholder.kcal - 1100.0, impact.remainingKcal)
    }

    @Test
    fun `un echec d ecriture conserve la saisie`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        diary.failure = IllegalStateException("disque plein")

        viewModel.onSave()

        val state = viewModel.uiState.value
        assertTrue(state is EntryUiState.Error)
        assertEquals("Riz", (state as EntryUiState.Error).form.lines.single().name)
    }

    @Test
    fun `reessayer apres un echec rend la saisie enregistrable`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        diary.failure = IllegalStateException("disque plein")
        viewModel.onSave()

        diary.failure = null
        viewModel.onRetry()
        viewModel.onSave()

        assertEquals(EntryUiState.Saved, viewModel.uiState.value)
        assertEquals(1, diary.dishes.size)
    }

    @Test
    fun `un plat introuvable se dit au lieu de s inventer`() = runTest(dispatcher) {
        val etat = viewModel(dishId = "plat-disparu").uiState
            .filterIsInstance<EntryUiState.Unavailable>()
            .first()

        assertEquals(EntryUiState.Unavailable, etat)
    }

    @Test
    fun `rouvrir un plat rend ses lignes telles qu elles ont ete figees`() = runTest(dispatcher) {
        val neuf = viewModel()
        remplir(neuf, neuf.content().form.lines.single().id, nom = "Riz", kcal = "195")
        neuf.onSave()
        val platId = diary.dishes.single().id

        val relu = viewModel(dishId = platId.value).content()

        val ligne = relu.form.lines.single()
        assertEquals("Riz", ligne.name)
        assertEquals("150", ligne.quantity)
        assertEquals("195", ligne.macros[Macro.CALORIES])
        assertEquals(EntrySource.MANUAL, relu.form.source)
    }

    // --- Decor ---------------------------------------------------------------

    private fun platDeja(kcal: Double): Dish {
        val id = DishId("plat-deja")
        return Dish(
            id = id,
            date = jour,
            source = EntrySource.SEARCH,
            loggedAt = clock.now(),
            entries = listOf(
                FoodEntry(
                    id = EntryId("ligne-deja"),
                    dishId = id,
                    displayName = "Deja note",
                    quantity = 100.0,
                    unit = "g",
                    grams = 100.0,
                    macros = Macros.caloriesOnly(kcal),
                ),
            ),
        )
    }

    private fun remplir(viewModel: EntryViewModel, id: DraftLineId, nom: String = "Riz", kcal: String = "195") {
        viewModel.onLineEdit(id, LineEdit.Name(nom))
        viewModel.onLineEdit(id, LineEdit.Quantity("150"))
        viewModel.onLineEdit(id, LineEdit.MacroValue(Macro.CALORIES, kcal))
    }

    private suspend fun EntryViewModel.content(): EntryUiState.Content =
        uiState.filterIsInstance<EntryUiState.Content>().first()

    private fun viewModel(dishId: String? = null) = EntryViewModel(
        savedStateHandle = SavedStateHandle(if (dishId == null) emptyMap() else mapOf("dishId" to dishId)),
        getDishDraft = GetDishDraft(diary, ids),
        getDaySummary = GetDaySummary(diary, clock),
        createDraft = CreateDraft(clock, ids),
        foodLookup = catalogue,
        logDish = LogDish(diary, catalogue, clock, ids),
        updateDish = UpdateDish(diary, ids),
    )
}
