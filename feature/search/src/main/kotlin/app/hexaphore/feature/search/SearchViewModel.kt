package app.hexaphore.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexaphore.domain.food.FavoriteFoods
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodCategory
import app.hexaphore.domain.food.FoodFilter
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.food.FoodStore
import app.hexaphore.domain.food.FoodTrait
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
import kotlinx.coroutines.flow.onStart
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
    private val store: FoodStore,
    recentFoods: RecentFoods,
) : ViewModel() {
    private val typed = MutableStateFlow("")

    /** Ce que le champ porte, pour que la saisie manuelle parte du bon nom. */
    val query: StateFlow<String> = typed

    /**
     * La fiche choisie, une fois **versée au catalogue**.
     *
     * Un résultat de la table de l'ANSES n'y est pas encore : il porte un
     * identifiant provisoire, régénéré à chaque recherche. Le rendre tel quel à
     * l'écran de validation le faisait chercher une fiche inexistante, et l'écran
     * s'ouvrait vide — c'était le défaut. La fiche est donc écrite ici, et c'est son
     * identifiant définitif qui voyage.
     */
    private val chosen = MutableStateFlow<FoodId?>(null)
    val picked: StateFlow<FoodId?> = chosen

    /** La fiche dont la corbeille vient d'être touchée, et ce qu'il faut en dire. */
    private val pendingDeletion = MutableStateFlow<FoodDeletion?>(null)
    val deletion: StateFlow<FoodDeletion?> = pendingDeletion

    /**
     * Les pastilles retenues.
     *
     * Hors de [uiState], comme [query], parce que le bandeau doit rester à l'écran
     * dans les cinq états — y compris pendant la recherche et sur une erreur. Le
     * faire transiter par l'état d'écran obligerait chaque variante à le porter.
     */
    private val active = MutableStateFlow(FoodFilter.NONE)
    val filter: StateFlow<FoodFilter> = active

    /**
     * **L'anti-rebond ne porte que sur la frappe.** Un tap sur une pastille est un
     * geste unique et délibéré : le retarder de 120 ms ferait paraître le bandeau
     * mou, alors que l'attente existe pour empêcher huit recherches pendant qu'on
     * écrit « chocolat ».
     */
    private val results: Flow<SearchUiState?> =
        combine(
            typed.map { it.trim() }.distinctUntilChanged().debounce(DEBOUNCE_MILLIS),
            active,
        ) { query, filter -> query to filter }
            .flatMapLatest { (query, filter) ->
                if (query.length < MINIMUM_CHARACTERS && filter.isEmpty) {
                    // `null` et non un etat : c'est l'absence de recherche, et c'est
                    // ce qui laisse les raccourcis reprendre la main.
                    flow<SearchUiState?> { emit(null) }
                } else {
                    // Une pastille seule, champ vide, est un mode parcours : c'est
                    // une demande explicite, et elle merite une liste.
                    search(query, filter)
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

    fun onToggleCategory(category: FoodCategory) {
        active.value = active.value.toggle(category)
    }

    fun onToggleTrait(trait: FoodTrait) {
        active.value = active.value.toggle(trait)
    }

    /**
     * Épingler, y compris une fiche que le catalogue ne connaît pas encore.
     *
     * Le [FoodStore.place] n'est pas une précaution : un résultat de la table de
     * l'ANSES porte un identifiant provisoire tant qu'il n'y est pas écrit
     * ([D51][decisions]), et `setFavorite` sur cet identifiant ne mettait à jour
     * **aucune ligne**. L'étoile ne s'allumait donc jamais sur un aliment neuf, et
     * relancer la recherche n'y changeait rien.
     *
     * L'état épinglé est lu sur la fiche **rendue par le catalogue** et non sur
     * celle qu'on affiche : c'est elle qui fait foi.
     */
    fun onToggleFavorite(food: Food) {
        viewModelScope.launch {
            runCatching {
                val stored = store.place(food)
                favorites.setFavorite(stored.id, !stored.favorite)
            }
        }
    }

    fun onPick(food: Food) {
        viewModelScope.launch {
            // En cas d'echec d'ecriture, la fiche presentee sert de repli : l'ecran
            // de validation la retrouvera par son identifiant provisoire s'il le
            // peut, et sinon proposera une saisie vierge plutot qu'une impasse.
            chosen.value = runCatching { store.place(food).id }.getOrDefault(food.id)
        }
    }

    /**
     * Demande la suppression d'une fiche personnelle.
     *
     * Elle n'est jamais immédiate : [docs/02][parcours] réserve le dialogue à ce qui
     * est destructif, et supprimer un aliment utilisé dans l'historique en fait
     * partie. Le nombre d'entrées concernées est lu avant de demander, parce que
     * c'est lui qui fait la différence entre « êtes-vous sûr » et une vraie
     * information.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    fun onDeleteRequested(food: Food) {
        viewModelScope.launch {
            val used = runCatching { store.usageCount(food.id) }.getOrDefault(0)
            pendingDeletion.value = FoodDeletion(food, used)
        }
    }

    fun onDeleteCancelled() {
        pendingDeletion.value = null
    }

    fun onDeleteConfirmed() {
        val target = pendingDeletion.value ?: return
        pendingDeletion.value = null
        viewModelScope.launch { runCatching { store.delete(target.food.id) } }
    }

    fun onPickHandled() {
        chosen.value = null
    }

    /**
     * `Searching` avant la lecture, et pas seulement pendant.
     *
     * Émis dans le flux plutôt que posé avant lui : ainsi une frappe qui annule la
     * recherche en cours annule aussi son état de chargement, et l'écran ne reste
     * jamais à « je cherche » pour une requête abandonnée.
     *
     * Le port rend un flux ([D53][decisions]) : les résultats suivent désormais les
     * écritures du catalogue sans qu'on relance la recherche. `onStart` et non une
     * émission manuelle, parce qu'il n'y a plus une lecture mais une suite.
     */
    private fun search(query: String, filter: FoodFilter): Flow<SearchUiState> = foodSearch
        .search(query, filter, RESULT_LIMIT)
        .map { foods ->
            if (foods.isEmpty()) SearchUiState.Empty(query) else SearchUiState.Results(query, foods)
        }.onStart { emit(SearchUiState.Searching) }
        .catch { emit(SearchUiState.Error) }

    private companion object {
        // --- Reglages ---------------------------------------------------------
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

/**
 * Une suppression en attente de confirmation.
 *
 * [usedEntries] est ce qui change la phrase posée : une fiche jamais servie se
 * supprime sans conséquence, une fiche citée par douze entrées demande qu'on dise
 * ce qu'elles deviennent — elles survivent, avec leurs valeurs figées.
 */
internal data class FoodDeletion(val food: Food, val usedEntries: Int)
