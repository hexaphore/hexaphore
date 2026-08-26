package app.hexavore.core.testing

import app.hexavore.domain.ai.DeepAnalysisSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * L'analyse approfondie, en memoire.
 *
 * **Decochee par defaut**, comme la vraie : un faux qui partirait cochee ferait passer
 * des cas ou l'outillage ne sert pas, pour la mauvaise raison.
 */
class InMemoryDeepAnalysisSettings(initial: Boolean = false) : DeepAnalysisSettings {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<Boolean> = state

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = enabled
    }
}
