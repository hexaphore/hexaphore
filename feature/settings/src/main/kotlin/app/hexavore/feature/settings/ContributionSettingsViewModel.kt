package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.food.ContributionSettings
import app.hexavore.domain.food.ContributionSetup
import app.hexavore.domain.food.OffAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Le compte Open Food Facts et la cible d'envoi.
 *
 * **Le mot de passe n'est jamais rendu à l'écran.** L'état ne dit que s'il y en a un,
 * pas lequel : un `StateFlow` qui le porterait le ferait vivre en mémoire pour rien, et
 * la première capture d'écran d'un rapport de bogue l'emporterait. C'est plus strict
 * que pour les clés d'IA, où le champ se révèle à la demande ([D77][decisions]) — ici
 * rien ne le demande, puisqu'on ne relit pas un mot de passe, on le remplace.
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class ContributionSettingsViewModel @Inject constructor(private val settings: ContributionSettings) :
    ViewModel() {
    val uiState: StateFlow<ContributionUiState> = settings
        .observe()
        .map(ContributionSetup::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = ContributionUiState(),
        )

    fun onSave(userId: String, password: String) {
        val account = OffAccount(userId.trim(), password)
        if (!account.usable) return
        viewModelScope.launch { settings.save(account) }
    }

    fun onForget() {
        viewModelScope.launch { settings.forget() }
    }

    fun onSandboxChange(sandbox: Boolean) {
        viewModelScope.launch { settings.useSandbox(sandbox) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Ce que l'écran des réglages de contribution montre.
 *
 * [userId] s'affiche — il est public sur le site, et le voir est la seule façon de
 * savoir sous quel nom on contribue. Le mot de passe, lui, n'est représenté que par
 * [connected].
 */
internal data class ContributionUiState(
    val userId: String = "",
    val connected: Boolean = false,
    val sandbox: Boolean = false,
)

private fun ContributionSetup.toUiState() = ContributionUiState(
    userId = account?.userId.orEmpty(),
    connected = open,
    sandbox = sandbox,
)
