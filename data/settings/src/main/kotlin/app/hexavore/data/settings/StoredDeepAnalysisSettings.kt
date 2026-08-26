package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.ai.DeepAnalysisSettings
import app.hexavore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Si l'utilisateur veut que le modèle interroge le catalogue.
 *
 * **Dans le fichier des réglages d'IA**, comme le mode debug et pour la même raison :
 * c'est une façon d'analyser, et effacer ses clés doit la remettre à zéro.
 *
 * [enabled] ne suspend pas : c'est `StoredAiCredentials.current()` qui pose la question,
 * au moment de construire la configuration d'un appel, et la valeur est déjà en mémoire.
 */
internal class StoredDeepAnalysisSettings(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : DeepAnalysisSettings {
    private val state = MutableStateFlow(preferences.getBoolean(DEEP_ANALYSIS, false))

    override fun observe(): Flow<Boolean> = state

    /** L'état courant, sans suspendre. */
    fun enabled(): Boolean = state.value

    override suspend fun setEnabled(enabled: Boolean) = withContext(dispatchers.io) {
        preferences.edit { putBoolean(DEEP_ANALYSIS, enabled) }
        state.value = enabled
    }

    /** Repart décochée, comme sur une installation neuve. */
    internal suspend fun forget() = setEnabled(enabled = false)
}

private const val DEEP_ANALYSIS = "ai.deep_analysis"
