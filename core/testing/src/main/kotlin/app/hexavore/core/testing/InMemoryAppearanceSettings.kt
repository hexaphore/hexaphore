package app.hexavore.core.testing

import app.hexavore.domain.appearance.AppearanceSettings
import app.hexavore.domain.appearance.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Le thème choisi, en mémoire.
 *
 * Il part sur [ThemeMode.SYSTEM] comme une installation neuve : un faux qui démarrerait
 * sur un thème explicite laisserait passer un défaut mal câblé.
 */
class InMemoryAppearanceSettings(
    initial: ThemeMode = ThemeMode.SYSTEM,
    /** Un fichier de préférences abîmé : la lecture jette, et l'écran doit tenir. */
    var failure: Boolean = false,
) : AppearanceSettings {
    private val state = MutableStateFlow(initial)

    /** Ce que le magasin porte, pour qu'un cas l'affirme sans passer par un flux. */
    val current: ThemeMode get() = state.value

    override fun observe(): Flow<ThemeMode> = state.map {
        if (failure) error("Apparence illisible") else it
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = mode
    }
}
