package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.ai.DebugSettings
import app.hexavore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Si l'on enregistre les échanges avec les fournisseurs.
 *
 * **Dans le fichier des réglages d'IA**, et ce n'est pas de la paresse : c'est un
 * réglage d'IA, et effacer ses clés doit l'éteindre avec elles. Quelqu'un qui repart
 * de zéro n'a rien demandé à voir.
 *
 * [enabled] ne suspend pas : c'est un intercepteur HTTP qui pose la question, sur le
 * fil d'OkHttp. La valeur est déjà en mémoire, il n'y a rien à lire sur un disque.
 */
internal class StoredDebugSettings(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : DebugSettings {
    private val state = MutableStateFlow(preferences.getBoolean(DEBUG_EXCHANGES, false))

    override fun observe(): Flow<Boolean> = state

    override fun enabled(): Boolean = state.value

    override suspend fun setEnabled(enabled: Boolean) = withContext(dispatchers.io) {
        preferences.edit { putBoolean(DEBUG_EXCHANGES, enabled) }
        state.value = enabled
    }

    /** Repart éteint, comme sur une installation neuve. */
    internal suspend fun forget() = setEnabled(enabled = false)
}

private const val DEBUG_EXCHANGES = "ai.debug_exchanges"
