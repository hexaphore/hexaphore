package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.appearance.AppearanceSettings
import app.hexavore.domain.appearance.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ce que l'écran Apparence montre : le thème retenu, et rien d'autre pour l'instant. */
internal data class AppearanceUiState(val theme: ThemeMode = ThemeMode.SYSTEM)

/**
 * Le thème, lu et écrit.
 *
 * **Un magasin illisible retombe sur le défaut plutôt que sur un écran vide.** C'est la
 * même règle que partout : l'apparence est un confort, et rien n'y justifie de refuser
 * l'écran à quelqu'un dont le fichier de préférences a été abîmé.
 */
@HiltViewModel
internal class AppearanceViewModel @Inject constructor(private val settings: AppearanceSettings) : ViewModel() {
    val uiState: StateFlow<AppearanceUiState> = settings
        .observe()
        .map { AppearanceUiState(theme = it) }
        .catch { emit(AppearanceUiState()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceUiState())

    fun onTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}
