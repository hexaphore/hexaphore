package app.hexaphore.feature.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexaphore.domain.diary.DaySummary
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.DraftLineId
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.impactOf
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodLookup
import app.hexaphore.domain.usecase.CreateDraft
import app.hexaphore.domain.usecase.GetDaySummary
import app.hexaphore.domain.usecase.GetDishDraft
import app.hexaphore.domain.usecase.LogDish
import app.hexaphore.domain.usecase.UpdateDish
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * L'écran de validation.
 *
 * Il ne sait pas d'où vient ce qu'il modifie. Un brouillon vierge, un plat relu, et
 * demain une proposition de modèle ou un produit scanné : tous arrivent sous la
 * forme d'un [app.hexaphore.domain.diary.EntryDraft], et rien ici ne teste la
 * provenance. C'est ce qui évite d'avoir à généraliser cet écran trois fois.
 *
 * @see docs/12-plan-de-developpement.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class EntryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getDishDraft: GetDishDraft,
    private val getDaySummary: GetDaySummary,
    private val createDraft: CreateDraft,
    private val foodLookup: FoodLookup,
    private val logDish: LogDish,
    private val updateDish: UpdateDish,
) : ViewModel() {
    private val dishId: DishId? = savedStateHandle.get<String>(EntryDestination.DISH_ID)?.let(::DishId)
    private val foodId: FoodId? = savedStateHandle.get<String>(EntryDestination.FOOD_ID)?.let(::FoodId)

    private val form = MutableStateFlow<EntryForm?>(null)
    private val status = MutableStateFlow(Status.LOADING)

    /**
     * La journée visée, relue une seule fois.
     *
     * Elle ne dépend que de la date, jamais du contenu des champs : sans ce
     * `distinctUntilChanged`, chaque frappe relancerait une lecture de la base pour
     * un chiffre qui n'a pas bougé.
     */
    private val day: Flow<DaySummary?> =
        form
            .map { it?.date }
            .distinctUntilChanged()
            .flatMapLatest { date ->
                if (date == null) {
                    flowOf(null)
                } else {
                    // Un echec de lecture ne prive que du restant. Le brouillon,
                    // lui, reste saisissable et enregistrable : refuser la saisie
                    // parce qu'on n'a pas pu lire la journee serait perdre le repas
                    // pour une information de confort.
                    getDaySummary(date)
                        .map<DaySummary, DaySummary?> { it }
                        .catch { emit(null) }
                }
            }

    val uiState: StateFlow<EntryUiState> =
        combine(form, status, day) { form, status, day ->
            when {
                status == Status.UNAVAILABLE -> EntryUiState.Unavailable
                status == Status.SAVED -> EntryUiState.Saved
                form == null -> EntryUiState.Loading
                status == Status.FAILED -> EntryUiState.Error(form)
                else -> EntryUiState.Content(
                    form = form,
                    impact = day?.impactOf(form.toDraft()),
                    saving = status == Status.SAVING,
                )
            }
        }
            // Aucun `flowOn` ici, contrairement a l'accueil, et c'est deliberé.
            // Ce que produit ce flux a chaque frappe tient en une conversion de
            // quelques lignes et deux additions ; le passer sur un autre
            // dispatcher n'economise rien et coute une image de latence. Cette
            // image, c'est le curseur du champ qui la paie.
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = EntryUiState.Loading,
            )

    init {
        viewModelScope.launch { open() }
        observePickedFood()
    }

    /**
     * La fiche que la recherche vient de déposer pour cet écran.
     *
     * **Elle s'ajoute au brouillon en cours**, elle n'en démarre pas un nouveau :
     * « Ajouter une ligne » rouvre la même recherche que le bouton de l'accueil, et
     * c'est ce qui rend les deux gestes identiques à apprendre. La clé est vidée
     * après lecture, sans quoi revenir sur cet écran rajouterait la même ligne.
     */
    private fun observePickedFood() {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(EntryDestination.PICKED_FOOD, null).collect { value ->
                val id = value?.let(::FoodId) ?: return@collect
                savedStateHandle[EntryDestination.PICKED_FOOD] = null
                addLineFrom(id)
            }
        }
    }

    private suspend fun addLineFrom(id: FoodId) {
        val food = runCatching { foodLookup.byId(id) }.getOrNull() ?: return
        val line = EntryFormLine.of(createDraft.line(food))
        form.update { current -> current?.copy(lines = current.lines + line) }
    }

    fun onLineEdit(id: DraftLineId, edit: LineEdit) {
        form.update { current -> current?.update(id) { line -> line.apply(edit) } }
    }

    fun onRemoveLine(id: DraftLineId) {
        form.update { current -> current?.copy(lines = current.lines.filterNot { it.id == id }) }
    }

    /** Après un échec d'écriture : le brouillon n'a pas bougé, il n'y a qu'à réessayer. */
    fun onRetry() {
        status.value = Status.EDITING
    }

    fun onSave() {
        val current = form.value ?: return
        val draft = current.toDraft()
        if (!draft.saveable || status.value == Status.SAVING) return

        status.value = Status.SAVING
        viewModelScope.launch {
            val written = runCatching { if (draft.editing) updateDish(draft) else logDish(draft) }
            status.value = if (written.isSuccess) Status.SAVED else Status.FAILED
        }
    }

    private suspend fun open() {
        val id = dishId
        if (id == null) {
            form.value = EntryForm.of(newDraft())
            status.value = Status.EDITING
            return
        }

        val relu = runCatching { getDishDraft(id) }.getOrNull()
        if (relu == null) {
            status.value = Status.UNAVAILABLE
        } else {
            form.value = EntryForm.of(relu)
            status.value = Status.EDITING
        }
    }

    /**
     * Le brouillon d'une saisie neuve, prérempli ou non.
     *
     * Une fiche introuvable — supprimée entre la recherche et l'ouverture — donne un
     * brouillon vierge plutôt qu'un écran d'erreur : il reste plus utile de saisir à
     * la main que de repartir de zéro, et la ligne vide dit déjà qu'il n'y a rien.
     *
     * La source suit la provenance, et elle seule : c'est ce qui distinguera la
     * pastille en tête d'écran, et rien d'autre dans tout l'écran n'en dépend.
     */
    private suspend fun newDraft() = foodId
        ?.let { id -> runCatching { foodLookup.byId(id) }.getOrNull() }
        ?.let { food -> createDraft(EntrySource.SEARCH, food) }
        ?: createDraft(EntrySource.MANUAL)

    /**
     * Là où en est l'écran.
     *
     * Séparé du contenu du formulaire, parce que les deux ne changent pas pour les
     * mêmes raisons : le formulaire bouge à chaque frappe, l'étape seulement aux
     * moments qui comptent.
     */
    private enum class Status {
        LOADING,
        EDITING,
        SAVING,
        SAVED,

        /** Le plat visé n'a pas pu être rouvert : supprimé, ou illisible. */
        UNAVAILABLE,
        FAILED,
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
