package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.notice.KeyRejection
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.notice.NoticeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Lesquelles des quatre pastilles sont allumées.
 *
 * **Une clé par pastille, nommée d'après elle**, plutôt qu'un entier de bits ou une
 * liste sérialisée. Un fichier de préférences se relit à l'œil quand on cherche un
 * défaut, et `notice.weight_stale=false` dit ce qu'il veut dire là où `notices=5`
 * demande de compter en binaire.
 *
 * **Absent vaut allumé.** Une pastille éteinte d'office ne serait découverte par
 * personne ; le réglage existe pour celui que l'une d'elles agace, pas pour lui cacher
 * les trois autres. C'est aussi ce qui fait qu'une cinquième pastille ajoutée plus tard
 * s'allume sans migration.
 */
internal class StoredNoticeSettings(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : NoticeSettings {
    private val enabled = MutableStateFlow(preferences.readEnabled())

    override fun observe(): Flow<Set<Notice>> = enabled

    override suspend fun setEnabled(notice: Notice, enabled: Boolean) = withContext(dispatchers.io) {
        preferences.edit { putBoolean(notice.key(), enabled) }
        // Relire plutot que modifier l'ensemble en memoire : l'affichage montre le
        // disque, comme partout ailleurs dans ce module.
        this@StoredNoticeSettings.enabled.value = preferences.readEnabled()
    }

    /** Oublie les quatre réglages. Appelé par l'effacement, comme les autres magasins. */
    internal suspend fun forget() = withContext(dispatchers.io) {
        preferences.edit { Notice.entries.forEach { remove(it.key()) } }
        enabled.value = preferences.readEnabled()
    }
}

/**
 * Le souvenir d'une clé refusée, dans le fichier des réglages d'IA.
 *
 * **Le même fichier que les clés**, et ce n'est pas de la paresse : effacer ses réglages
 * d'IA doit emporter le souvenir d'un refus avec eux, puisqu'il porte sur une clé qui
 * n'existe plus. Un fichier séparé aurait laissé survivre la pastille à la disparition
 * de ce qu'elle désigne.
 */
internal class StoredKeyRejection(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : KeyRejection {
    private val rejected = MutableStateFlow(preferences.getBoolean(KEY_REJECTED, false))

    override fun observe(): Flow<Boolean> = rejected

    override suspend fun note() = write(true)

    override suspend fun clear() = write(false)

    private suspend fun write(value: Boolean) = withContext(dispatchers.io) {
        preferences.edit { putBoolean(KEY_REJECTED, value) }
        rejected.value = value
    }
}

/**
 * Le nom de la préférence d'une pastille.
 *
 * Dérivé de l'énumération et non écrit à la main : une cinquième pastille ne peut pas
 * arriver sans sa clé, et deux ne peuvent pas partager la même.
 */
private fun Notice.key(): String = "notice." + name.lowercase()

private fun SharedPreferences.readEnabled(): Set<Notice> =
    Notice.entries.filterTo(mutableSetOf()) { getBoolean(it.key(), true) }

private const val KEY_REJECTED = "ai.key_rejected"
