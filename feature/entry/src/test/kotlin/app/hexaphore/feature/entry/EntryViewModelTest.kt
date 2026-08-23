package app.hexaphore.feature.entry

import androidx.lifecycle.SavedStateHandle
import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.core.testing.InMemoryFavoriteDishes
import app.hexaphore.core.testing.InMemoryFoodCatalog
import app.hexaphore.core.testing.InMemoryGoals
import app.hexaphore.core.testing.InMemorySelectedDay
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.domain.ai.EstimatedUnit
import app.hexaphore.domain.ai.EstimationOutcome
import app.hexaphore.domain.ai.InMemoryPendingRecognition
import app.hexaphore.domain.ai.Recognition
import app.hexaphore.domain.ai.RecognizedItem
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.DraftLineId
import app.hexaphore.domain.diary.EntryId
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.FavoriteNumbering
import app.hexaphore.domain.diary.FoodEntry
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodServing
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.Macros
import app.hexaphore.domain.nutrition.NutrientValues
import app.hexaphore.domain.usecase.AddFoodLine
import app.hexaphore.domain.usecase.CreateDraft
import app.hexaphore.domain.usecase.GetDaySummary
import app.hexaphore.domain.usecase.GetDishDraft
import app.hexaphore.domain.usecase.GetFavoriteDraft
import app.hexaphore.domain.usecase.LogDish
import app.hexaphore.domain.usecase.NextFavoriteNumber
import app.hexaphore.domain.usecase.OpenDraft
import app.hexaphore.domain.usecase.RemoveFavoriteDish
import app.hexaphore.domain.usecase.ResolveFoodLabel
import app.hexaphore.domain.usecase.ResolveRecognition
import app.hexaphore.domain.usecase.SaveDraft
import app.hexaphore.domain.usecase.SaveFavoriteDish
import app.hexaphore.domain.usecase.UpdateDish
import app.hexaphore.domain.usecase.UpdateFavoriteDish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

    /** Un objectif de maintien : le restant se compte par rapport à lui. */
    private val objectif = InMemoryGoals.maintenance(jour)
    private val goals = InMemoryGoals(listOf(objectif))

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

        ajouterAliment(viewModel, RIZ)
        ajouterAliment(viewModel, POULET)
        advanceUntilIdle()

        assertEquals(3, viewModel.content().form.lines.size)
    }

    @Test
    fun `un aliment choisi arrive prerempli, avec sa reference`() = runTest(dispatcher) {
        // Le defaut : la ligne s ajoutait vide, ou pas du tout. Elle doit porter le
        // nom, la quantite et les valeurs de la fiche -- et sa reference, sans quoi
        // la quantite ne recalculerait rien.
        val viewModel = viewModel()

        ajouterAliment(viewModel, RIZ)
        advanceUntilIdle()

        val ajoutee = viewModel.content().form.lines.last()
        assertEquals("Riz blanc, cuit", ajoutee.name)
        assertEquals("155", ajoutee.macros[Macro.CALORIES])
        assertEquals(RIZ.per100g, ajoutee.reference)
    }

    @Test
    fun `les valeurs s affichent en grammes entiers`() = runTest(dispatcher) {
        // Personne ne compte les demi-grammes, et ce qui est affiche est ce qui sera
        // enregistre : l arrondi a lieu a l aller, pas seulement a l ecran.
        val viewModel = viewModel()

        ajouterAliment(viewModel, POMME)
        advanceUntilIdle()

        val ajoutee = viewModel.content().form.lines.last()
        assertEquals("81", ajoutee.macros[Macro.CALORIES], "54 kcal pour 100 g, sur 150 g")
        assertEquals("2", ajoutee.macros[Macro.FIBER], "1,4 g pour 100 g, sur 150 g")
    }

    @Test
    fun `changer la quantite recalcule les valeurs affichees`() = runTest(dispatcher) {
        val viewModel = viewModel()
        ajouterAliment(viewModel, RIZ)
        advanceUntilIdle()
        val ligne = viewModel.content().form.lines.last().id

        viewModel.onLineEdit(ligne, LineEdit.Quantity("200"))

        assertEquals("310", viewModel.content().form.lines.last().macros[Macro.CALORIES])
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

        viewModel.onLineEdit(premiere, LineEdit.Quantity(""))

        assertFalse(viewModel.content().saveable)
    }

    @Test
    fun `enregistrer ecrit un plat avec toutes ses lignes`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        ajouterAliment(viewModel, POULET)
        advanceUntilIdle()
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
        ajouterAliment(viewModel, RIZ)
        advanceUntilIdle()
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
        assertEquals(objectif.daily.kcal - 1100.0, impact.remainingKcal)
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
    fun `une proposition ouvre autant de lignes que le modele en a rendues`() = runTest(dispatcher) {
        // Le chemin que quatre livraisons attendaient, vu depuis l'ecran : la modale
        // depose, la route ne porte qu'un drapeau, et l'ecran recoit un brouillon
        // comme il en recoit depuis la tranche 2.
        runBlocking { catalogue.save(RIZ) }
        pending.offer(Recognition(listOf(item("riz", 1.0), item("tofu fume", 80.0))), EntrySource.TEXT_AI)

        val state = viewModel(proposal = true).content()

        assertEquals(listOf(RIZ.name, "tofu fume"), state.form.lines.map { it.name })
        assertEquals(EntrySource.TEXT_AI, state.form.source)
        // La marque traverse le formulaire : sans elle, une supposition s'afficherait
        // avec la meme autorite qu'un aliment choisi.
        assertNotNull(state.form.lines.first().suggestion, "une ligne proposee doit le dire")
    }

    @Test
    fun `une proposition deja reprise ne se rejoue pas`() = runTest(dispatcher) {
        // Revenir sur la validation par le bouton « retour » ne doit pas ressusciter
        // un plat qu'on vient d'enregistrer, ni le dedoubler.
        pending.offer(Recognition(listOf(item("riz", 1.0))), EntrySource.TEXT_AI)
        viewModel(proposal = true).content()

        val etat = viewModel(proposal = true).uiState
            .filterIsInstance<EntryUiState.Unavailable>()
            .first()

        assertEquals(EntryUiState.Unavailable, etat)
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
            source = EntrySource.MANUAL,
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

    @Test
    fun `un produit scanne ouvre un plat marque code-barres`() = runTest {
        // L'argument de route est lu par le `ViewModel`, et c'est la moitie que le
        // domaine ne couvre pas : `ScannedFoodTest` eprouve la regle, celui-ci
        // eprouve qu'elle est **atteinte**. C'est exactement la ou D52 avait fait
        // perdre une saisie -- un argument que personne ne lisait.
        runBlocking { catalogue.save(RIZ) }

        val etat = viewModel(scannedFoodId = RIZ.id.value).content()

        assertEquals(EntrySource.BARCODE, etat.form.source)
        assertEquals(listOf(RIZ.name), etat.form.lines.map { it.name })
    }

    /**
     * Ce que fait l'écran quand la recherche lui rend une fiche.
     *
     * C'est le `ViewModel` qu'on appelle et non un `SavedStateHandle` qu'on remplit :
     * celui d'un `ViewModel` et celui d'une entrée de pile sont deux objets
     * différents, et le premier n'a jamais vu ce que le second recevait. Un test qui
     * écrivait dans le handle passait, pendant que l'écran ne faisait rien.
     */
    private fun ajouterAliment(viewModel: EntryViewModel, food: Food = RIZ) {
        runBlocking { catalogue.save(food) }
        viewModel.onFoodPicked(food.id)
    }

    private fun viewModel(
        dishId: String? = null,
        favoriteId: String? = null,
        scannedFoodId: String? = null,
        proposal: Boolean = false,
    ) = EntryViewModel(
        savedStateHandle = SavedStateHandle(
            listOfNotNull(
                dishId?.let { "dishId" to it },
                favoriteId?.let { "favoriteId" to it },
                scannedFoodId?.let { "scannedFoodId" to it },
                "proposal" to proposal,
            ).toMap(),
        ),
        openDraft = OpenDraft(
            dishes = GetDishDraft(diary, ids),
            favorites = GetFavoriteDraft(favoris, catalogue, clock, ids),
            create = CreateDraft(clock, ids, InMemorySelectedDay()),
            foods = catalogue,
            pending = pending,
            resolve = ResolveRecognition(
                ResolveFoodLabel(catalogue),
                CreateDraft(clock, ids, InMemorySelectedDay()),
                // Aucun repli : ces cas ne parlent pas de l'etape 4, et un estimateur
                // qui repondrait remplirait des lignes qu'ils veulent vides.
                estimate = { EstimationOutcome.Estimated(emptyList()) },
            ),
        ),
        addFoodLine = AddFoodLine(catalogue, CreateDraft(clock, ids, InMemorySelectedDay())),
        getDaySummary = GetDaySummary(diary, goals, clock),
        saveDraft = SaveDraft(LogDish(diary, catalogue, favoris, clock, ids), UpdateDish(diary, ids)),
        favorites = DraftFavorites(
            saveFavoriteDish = SaveFavoriteDish(favoris, ids),
            removeFavoriteDish = RemoveFavoriteDish(favoris),
            nextFavoriteNumber = NextFavoriteNumber(numerotation, favoris),
            updateFavoriteDish = UpdateFavoriteDish(favoris, diary),
        ),
        clock = clock,
    )

    private val favoris = InMemoryFavoriteDishes()

    /**
     * Un compteur qui avance vraiment, comme celui des préférences.
     *
     * Un faux qui rendrait toujours 1 laisserait passer exactement la régression que
     * ce port existe pour empêcher.
     */
    private val numerotation = object : FavoriteNumbering {
        private var next = 1

        override suspend fun next(): Int = next++
    }

    /** Le dépôt des propositions, partagé entre l'écran qui dépose et celui qui reprend. */
    private val pending = InMemoryPendingRecognition()

    private fun item(label: String, quantity: Double) =
        RecognizedItem(label = label, quantity = quantity, unit = EstimatedUnit.G, confidence = 0.9f)

    private companion object {
        val RIZ = Food(
            id = FoodId("f-riz"),
            source = FoodSource.CIQUAL,
            sourceRef = "9104",
            name = "Riz blanc, cuit",
            per100g = NutrientValues(kcal = 155.0),
        )

        val POMME = Food(
            id = FoodId("f-pomme"),
            source = FoodSource.CIQUAL,
            sourceRef = "13039",
            name = "Pomme, chair et peau, crue",
            per100g = NutrientValues(kcal = 54.0, fiber = 1.4),
            servings = listOf(FoodServing("1 pomme moyenne", grams = 150.0, isDefault = true)),
        )

        val POULET = Food(
            id = FoodId("f-poulet"),
            source = FoodSource.CIQUAL,
            sourceRef = "36005",
            name = "Poulet roti",
            per100g = NutrientValues(kcal = 200.0),
        )
    }
}
