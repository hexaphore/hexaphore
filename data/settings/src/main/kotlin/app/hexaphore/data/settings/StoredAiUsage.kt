package app.hexaphore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.AiUsageEntry
import app.hexaphore.domain.ai.AiUsageLog
import app.hexaphore.domain.ai.TokenUsage
import app.hexaphore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Le compteur d'usage, dans les préférences, à côté des clés.
 *
 * **Pas de Room**, et c'est un choix qui se justifie par ce que la donnée est : trois
 * entiers par couple fournisseur-modèle, jamais interrogés autrement qu'en bloc, et
 * qui n'ont ni date ni relation. Une table aurait apporté une migration, un DAO et un
 * schéma exporté pour une somme que trois clés portent aussi bien.
 *
 * **Chaque compte tient dans une seule clé**, sous la forme `appels,entrée,sortie`.
 * Trois clés séparées auraient pu se désynchroniser entre deux écritures ; une seule
 * ligne s'écrit d'un coup.
 *
 * Le modèle peut contenir n'importe quel caractère — l'utilisateur le saisit —, d'où
 * un séparateur qui n'apparaît dans aucun identifiant de modèle connu et une lecture
 * qui découpe **une seule fois**, par la gauche.
 */
internal class StoredAiUsage(private val preferences: SharedPreferences, private val dispatchers: DispatcherProvider) :
    AiUsageLog {
    override suspend fun record(provider: AiProvider, model: String, usage: TokenUsage?) {
        val key = keyOf(provider, model)
        val before = preferences.getString(key, null).toCounts()

        preferences.edit {
            putString(
                key,
                listOf(
                    before.calls + 1,
                    before.input + (usage?.input ?: 0),
                    before.output + (usage?.output ?: 0),
                ).joinToString(separator = FIELD_SEPARATOR.toString()),
            )
        }
    }

    /**
     * Le compte, et ce qu'il devient quand il change.
     *
     * Un flux plutôt qu'une lecture : l'écran des réglages est ouvert pendant qu'on
     * appuie sur « Tester », et un compteur qui ne bougerait qu'à la réouverture
     * laisserait croire que l'essai n'a rien coûté.
     */
    override fun observe(): Flow<List<AiUsageEntry>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(readAll()) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        send(readAll())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.flowOn(dispatchers.io)

    /**
     * Tout ce qui est rangé, trié du plus consommé au moins.
     *
     * Une clé illisible — écrite par une version antérieure, ou corrompue — est
     * **ignorée** plutôt que de faire tomber l'écran : un compteur est une information
     * de confort, et personne ne doit perdre l'accès à ses réglages pour ça.
     */
    private fun readAll(): List<AiUsageEntry> = preferences.all.keys
        .filter { it.startsWith(USAGE_PREFIX) }
        .mapNotNull { key -> entryOf(key) }
        .sortedByDescending { it.input + it.output }

    private fun entryOf(key: String): AiUsageEntry? = key
        .removePrefix(USAGE_PREFIX)
        .splitOnce()
        ?.let { (provider, model) ->
            AiProvider.entries.firstOrNull { it.name == provider }?.let { known ->
                val counts = preferences.getString(key, null).toCounts()
                AiUsageEntry(known, model, counts.calls, counts.input, counts.output)
            }
        }

    private fun keyOf(provider: AiProvider, model: String) = USAGE_PREFIX + provider.name + FIELD_SEPARATOR + model

    private fun String.splitOnce(): Pair<String, String>? {
        val cut = indexOf(FIELD_SEPARATOR)
        return if (cut <= 0) null else substring(0, cut) to substring(cut + 1)
    }

    private fun String?.toCounts(): Counts {
        val parts = this?.split(FIELD_SEPARATOR)?.mapNotNull { it.toIntOrNull() }.orEmpty()
        return if (parts.size == COUNT_FIELDS) Counts(parts[0], parts[1], parts[2]) else Counts()
    }

    private data class Counts(val calls: Int = 0, val input: Int = 0, val output: Int = 0)
}

private const val USAGE_PREFIX = "usage/"
private const val FIELD_SEPARATOR = ','
private const val COUNT_FIELDS = 3
