package app.hexaphore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.EntryId
import app.hexaphore.domain.usecase.DeleteEntry
import app.hexaphore.domain.usecase.GetDaySummary
import app.hexaphore.domain.usecase.RestoreDish
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * L'accueil : une journée, ses totaux, ses repas.
 *
 * Aucun calcul ici. Le cas d'usage produit un [app.hexaphore.domain.diary.DaySummary]
 * complet et le ViewModel se contente de l'emballer dans un état d'écran — si un
 * calcul mérite un test, il appartient au domaine.
 *
 * L'agrégation tourne sur le dispatcher de calcul et non sur le fil principal :
 * une journée chargée peut contenir quelques dizaines de lignes aujourd'hui, et
 * beaucoup plus le jour où l'écran Journée affichera un mois.
 *
 * @see docs/06-architecture.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    getDaySummary: GetDaySummary,
    dispatchers: DispatcherProvider,
    private val deleteEntry: DeleteEntry,
    private val restoreDish: RestoreDish,
) : ViewModel() {
    /**
     * Le déclencheur de relecture.
     *
     * Un flux qui a émis une exception est terminé : le relancer demande un nouvel
     * abonnement, pas un simple `retry` d'appel. Chaque valeur poussée ici en
     * fabrique un, et `flatMapLatest` abandonne le précédent.
     */
    private val attempts = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> =
        attempts
            .flatMapLatest {
                getDaySummary()
                    .map<_, HomeUiState> { HomeUiState.Content(it) }
                    // `catch` **dans** le flatMapLatest, et c'est ce qui rend le
                    // bouton Reessayer utile. Un flux qui a rattrape une exception
                    // est termine : place a l'exterieur, il ferait terminer la
                    // source de `stateIn`, qui resterait alors bloquee sur Error
                    // quoi qu'on pousse dans `attempts`. Ici, seul le flux interne
                    // se termine ; celui des tentatives, lui, ne finit jamais.
                    .catch { emit(HomeUiState.Error) }
            }
            .flowOn(dispatchers.default)
            .stateIn(
                scope = viewModelScope,
                // La collecte s'arrête cinq secondes après le dernier observateur :
                // assez pour traverser une rotation sans relire la base, trop peu
                // pour qu'un écran quitté continue d'écouter en arrière-plan.
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = HomeUiState.Loading,
            )

    /**
     * Le plat à remettre si l'utilisateur annule.
     *
     * La suppression part **immédiatement** et c'est ce plat qui permet d'y revenir,
     * plutôt que de la retarder pendant les cinq secondes du `Snackbar`. Une
     * suppression différée disparaît si le processus est tué entre-temps, et
     * l'utilisateur retrouve alors une ligne qu'il croyait supprimée. Ici, le pire
     * cas est l'inverse : la ligne est bien partie, et c'est ce que l'écran montrait.
     */
    private val undoable = MutableStateFlow<Dish?>(null)

    val pendingUndo: StateFlow<Dish?> = undoable.asStateFlow()

    /** Relit le journal après un échec. */
    fun retry() {
        attempts.update { it + 1 }
    }

    fun onDeleteEntry(dish: Dish, entryId: EntryId) {
        viewModelScope.launch {
            runCatching { deleteEntry(dish, entryId) }
                // Pas d'annulation a proposer sur un echec : rien n'a ete supprime.
                .onSuccess { undoable.value = dish }
        }
    }

    fun onUndo() {
        val dish = undoable.value ?: return
        undoable.value = null
        viewModelScope.launch { runCatching { restoreDish(dish) } }
    }

    /** La fenêtre d'annulation est passée. Le plat cesse d'être rattrapable. */
    fun onUndoExpired() {
        undoable.value = null
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
