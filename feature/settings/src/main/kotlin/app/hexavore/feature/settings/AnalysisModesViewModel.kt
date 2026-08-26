package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.DebugSettings
import app.hexavore.domain.ai.DeepAnalysisSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Comment on analyse : en profondeur, et en gardant trace.
 *
 * **Sorti d'`AiSettingsViewModel` quand son seuil de fonctions a mordu**, et le
 * découpage suit ce que les choses sont : là-bas ce qu'on saisit pour un fournisseur —
 * une clé, un modèle, une URL —, ici deux façons d'analyser qui n'appartiennent à aucun
 * fournisseur en particulier et qui ne se rangent pas au même endroit.
 *
 * Les deux modèles cohabitent sur le même écran, chacun sur sa section. C'est le cas
 * normal d'un écran qui règle deux sujets voisins.
 */
@HiltViewModel
internal class AnalysisModesViewModel @Inject constructor(
    private val deep: DeepAnalysisSettings,
    private val debug: DebugSettings,
    credentials: AiCredentials,
) : ViewModel() {
    val uiState: StateFlow<ModesUiState> = combine(
        deep.observe(),
        debug.observe(),
        credentials.observe(),
    ) { deeply, tracing, setup ->
        ModesUiState(
            deepAnalysis = deeply,
            debug = tracing,
            toolingAvailable = setup.active?.tooling == true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ModesUiState())

    fun onDeepAnalysis(enabled: Boolean) {
        viewModelScope.launch { deep.setEnabled(enabled) }
    }

    fun onDebug(enabled: Boolean) {
        viewModelScope.launch { debug.setEnabled(enabled) }
    }
}

/**
 * Ce que les deux sections montrent.
 *
 * [toolingAvailable] grise la case sans l'éteindre : le réglage reste, et il reprendra
 * effet quand on rebranchera un fournisseur qui sait appeler des outils. L'éteindre à
 * la bascule ferait perdre un choix que personne n'a défait.
 */
internal data class ModesUiState(
    val deepAnalysis: Boolean = false,
    val debug: Boolean = false,
    val toolingAvailable: Boolean = false,
)
