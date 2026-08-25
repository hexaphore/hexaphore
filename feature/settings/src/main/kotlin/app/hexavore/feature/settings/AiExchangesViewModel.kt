package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.ai.AiExchange
import app.hexavore.domain.ai.AiExchangeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Les derniers echanges, tels que le journal les tient.
 *
 * Presque rien a faire : la liste est deja ordonnee et bornee par le journal. Ce
 * modele n'existe que pour tenir l'abonnement au cycle de vie de l'ecran et pour
 * offrir le bouton qui vide.
 */
@HiltViewModel
internal class AiExchangesViewModel @Inject constructor(private val log: AiExchangeLog) : ViewModel() {
    val exchanges: StateFlow<List<AiExchange>> = log
        .observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), emptyList())

    fun onClear() = log.clear()

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
