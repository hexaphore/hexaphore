package app.hexaphore.feature.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexaphore.domain.profile.WeightEntry
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.GetWeightTrend
import app.hexaphore.domain.usecase.RecordWeight
import app.hexaphore.domain.usecase.WeightTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Le journal de poids.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@HiltViewModel
internal class WeightViewModel @Inject constructor(
    getWeightTrend: GetWeightTrend,
    private val recordWeight: RecordWeight,
    private val clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<WeightUiState> = getWeightTrend()
        .map<WeightTrend, WeightUiState> { WeightUiState.Loaded(it, clock.today()) }
        // Une lecture qui echoue ne se deguise pas en journal vide : « vous ne vous
        // etes jamais pese » est une affirmation, pas une absence de reponse.
        .catch { emit(WeightUiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = WeightUiState.Loading,
        )

    /** Le jour proposé par défaut dans la boîte de saisie. */
    fun today(): LocalDate = clock.today()

    fun onRecord(date: LocalDate, weightKg: Double) {
        viewModelScope.launch { recordWeight(WeightEntry(date, weightKg)) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Ce que l'écran affiche. */
internal sealed interface WeightUiState {
    data object Loading : WeightUiState

    /**
     * La lecture n'a pas abouti.
     *
     * Distinct d'un journal vide, et pour la même raison que sur l'accueil : une liste
     * vide dit « rien n'a été noté », ce qui serait un mensonge sur une base illisible.
     */
    data object Error : WeightUiState

    data class Loaded(val trend: WeightTrend, val today: LocalDate) : WeightUiState {
        /** `true` quand aucune pesée n'a jamais été notée. */
        val empty: Boolean get() = trend.points.isEmpty()

        /**
         * `true` quand il y a des pesées mais pas encore de tendance.
         *
         * L'écran le dit plutôt que de laisser chercher pourquoi le tracé en évidence
         * manque : sous trois pesées dans les sept jours, il n'y a rien à lisser.
         */
        val trendMissing: Boolean get() = !empty && trend.points.none { it.averageKg != null }
    }
}
