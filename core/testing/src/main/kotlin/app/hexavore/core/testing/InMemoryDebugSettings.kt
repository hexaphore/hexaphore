package app.hexavore.core.testing

import app.hexavore.domain.ai.DebugSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * L'enregistrement des echanges, en memoire.
 *
 * **Eteint par defaut**, comme le vrai : un faux qui partirait allume ferait passer
 * des cas ou la journalisation ne coute rien, pour la mauvaise raison.
 */
class InMemoryDebugSettings(initial: Boolean = false) : DebugSettings {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<Boolean> = state

    override fun enabled(): Boolean = state.value

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = enabled
    }
}
