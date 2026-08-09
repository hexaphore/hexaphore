package app.hexaphore.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexaphore.domain.food.FavoriteFoods
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.food.RecentFoods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * L'écran de recherche.
 *
 * **L'anti-rebond et le seuil de deux caractères vivent ici**, et non dans le port.
 * Ce sont des règles d'ergonomie de saisie ([D23][decisions]) : les mettre dans le
 * contrat les imposerait au résolveur de la tranche 6, qui interroge la même
 * recherche avec un mot déjà complet et n'a pas de clavier.
 *
 * Deux caractères parce que la recherche est locale — le coût d'une requête inutile
 * est une lecture SQLite, pas un aller-retour réseau — et parce que « riz », « thé »
 * et « œuf » sont des aliments courants qu'un seuil à trois rendrait introuvables
 * tant que le mot n'est pas fini.
 *
 * 120 ms parce que sans attente, taper « chocolat » lance huit recherches dont sept
 * sont jetées, et les résultats clignotent pendant la frappe. Une frappe qui arrive
 * avant l'échéance annule la précédente : c'est ce que fait [flatMapLatest], et
 * c'est ce qui rend la liste lisible pendant qu'on écrit.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
internal class SearchViewModel @Inject constructor(
    private val foodSearch: FoodSearch,
    private val favorites: FavoriteFoods,
    recentFoods: RecentFoods,
) : ViewModel() {
    private val typed = MutableStateFlow("")

    /** Ce que le champ porte, pour que l'écran de création parte du bon nom. */
    val query: StateFlow<String> = typed

    private val results: Flow<SearchUiState?> =
        typed
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MILLIS)
            .flatMapLatest { query ->
                if (query.length < MINIMUM_CHARACTERS) {
                    // `null` et non un etat : c'est l'absence de recherche, et c'est
                    // ce qui laisse les raccourcis reprendre la main.
                    flow<SearchUiState?> { emit(null) }
                } else {
                    search(query)
                }
            }

    val uiState: StateFlow<SearchUiState> =
        combine(
            results,
            recentFoods.observeRecent(RECENT_COUNT).catch { emit(emptyList()) },
            favorites.observeFavorites().catch { emit(emptyList()) },
        ) { found, recent, pinned ->
            found ?: SearchUiState.Shortcuts(recent = recent, favorites = pinned)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = SearchUiState.Shortcuts(recent = emptyList(), favorites = emptyList()),
        )

    fun onQueryChange(value: String) {
        typed.value = value
    }

    fun onToggleFavorite(food: Food) {
        viewModelScope.launch { runCatching { favorites.setFavorite(food.id, !food.favorite) } }
    }

    /**
     * `Searching` avant la lecture, et pas seulement pendant.
     *
     * Émis dans le flux plutôt que posé avant lui : ainsi une frappe qui annule la
     * recherche en cours annule aussi son état de chargement, et l'écran ne reste
     * jamais à « je cherche » pour une requête abandonnée.
     */
    private fun search(query: String): Flow<SearchUiState> = flow {
        emit(SearchUiState.Searching)
        val foods = foodSearch.search(query, RESULT_LIMIT)
        emit(if (foods.isEmpty()) SearchUiState.Empty(query) else SearchUiState.Results(query, foods))
    }.catch { emit(SearchUiState.Error) }

    private companion object {
        /** [D23][decisions] : la requête part 120 ms après la dernière frappe. */
        const val DEBOUNCE_MILLIS = 120L

        /** [D23][decisions] : dès le deuxième caractère. */
        const val MINIMUM_CHARACTERS = 2

        /** Les vingt derniers aliments distincts, comme docs/02 le demande. */
        const val RECENT_COUNT = 20

        /**
         * Assez pour faire défiler, pas assez pour que le tri coûte.
         *
         * Le budget est de 150 ms à partir de la fin de l'anti-rebond, et il se
         * dépense surtout dans les deux lectures ; classer trente fiches est
         * négligeable, en classer trois cents ne le serait plus.
         */
        const val RESULT_LIMIT = 30

        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
