package app.hexaphore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.goal.AdjustmentSettings
import app.hexaphore.domain.goal.AdjustmentSetup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Ce que l'utilisateur a répondu aux suggestions d'ajustement.
 *
 * **Son propre fichier**, comme le compte Open Food Facts a le sien : effacer ses
 * réglages d'IA ne doit pas réactiver une adaptation qu'on avait désactivée, ni
 * effacer la trace d'un refus — deux sujets sans rapport ([D86][decisions]).
 *
 * **Rien de chiffré.** Ce ne sont pas des secrets, ce sont des traces de décision, et
 * le chiffrement coûterait une lecture de Keystore par ouverture de l'accueil.
 *
 * Les dates sont rangées en ISO. C'est ce que fait déjà le journal, et son ordre
 * lexicographique est son ordre chronologique — ici on ne trie rien, mais une date
 * qu'on peut relire à l'œil dans un fichier de préférences vaut mieux qu'un entier.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/03-nutrition-calculs.md
 */
internal class StoredAdjustmentSettings(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : AdjustmentSettings {
    private val setup = MutableStateFlow(preferences.readAdjustment())

    override fun observe(): Flow<AdjustmentSetup> = setup

    override suspend fun accepted(on: LocalDate) = write {
        preferences.edit { putString(LAST_ACCEPTED, on.toString()) }
    }

    override suspend fun ignored(on: LocalDate) = write {
        preferences.edit { putString(LAST_IGNORED, on.toString()) }
    }

    override suspend fun stop() = write {
        preferences.edit { putBoolean(ENABLED, false) }
    }

    /** Écrit hors du fil principal, puis **relit** : l'affichage montre le disque. */
    private suspend fun write(block: () -> Unit) = withContext(dispatchers.io) {
        block()
        setup.value = preferences.readAdjustment()
    }
}

/**
 * L'état complet, relu depuis les préférences.
 *
 * **Une date illisible vaut aucune date**, et non aujourd'hui : une valeur corrompue ne
 * doit pas faire taire l'adaptation pour deux semaines, ni la faire parler alors qu'on
 * venait de répondre. L'absence est le seul état que ce chiffré permette d'affirmer.
 */
private fun SharedPreferences.readAdjustment() = AdjustmentSetup(
    enabled = getBoolean(ENABLED, true),
    lastAcceptedOn = getString(LAST_ACCEPTED, null)?.toLocalDateOrNull(),
    lastIgnoredOn = getString(LAST_IGNORED, null)?.toLocalDateOrNull(),
)

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private const val ENABLED = "adjustment.enabled"
private const val LAST_ACCEPTED = "adjustment.last_accepted"
private const val LAST_IGNORED = "adjustment.last_ignored"
