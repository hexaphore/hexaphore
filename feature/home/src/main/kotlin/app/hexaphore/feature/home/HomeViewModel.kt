package app.hexaphore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.usecase.GetDaySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
@HiltViewModel
class HomeViewModel @Inject constructor(getDaySummary: GetDaySummary, dispatchers: DispatcherProvider) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        getDaySummary()
            .map<_, HomeUiState> { HomeUiState.Content(it) }
            .flowOn(dispatchers.default)
            .stateIn(
                scope = viewModelScope,
                // La collecte s'arrête cinq secondes après le dernier observateur :
                // assez pour traverser une rotation sans relire la base, trop peu
                // pour qu'un écran quitté continue d'écouter en arrière-plan.
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = HomeUiState.Loading,
            )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
