package app.hexaphore.core.testing

import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.AiUsageEntry
import app.hexaphore.domain.ai.AiUsageLog
import app.hexaphore.domain.ai.TokenUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Le compteur d'usage, en mémoire.
 *
 * Il **additionne vraiment**, comme celui qui écrit dans les préférences : un faux qui
 * se contenterait de retenir le dernier appel laisserait passer une régression sur
 * l'accumulation, qui est tout ce que ce port promet.
 */
class InMemoryAiUsage : AiUsageLog {
    private val entries = MutableStateFlow<List<AiUsageEntry>>(emptyList())

    /** Ce qui a été enregistré, dans l'ordre : ce que les cas de routage inspectent. */
    val recorded = mutableListOf<AiUsageEntry>()

    override suspend fun record(provider: AiProvider, model: String, usage: TokenUsage?) {
        val before = entries.value.firstOrNull { it.provider == provider && it.model == model }
        val after = AiUsageEntry(
            provider = provider,
            model = model,
            calls = (before?.calls ?: 0) + 1,
            input = (before?.input ?: 0) + (usage?.input ?: 0),
            output = (before?.output ?: 0) + (usage?.output ?: 0),
        )

        recorded += after
        entries.value = entries.value.filterNot { it.provider == provider && it.model == model } + after
    }

    override fun observe(): Flow<List<AiUsageEntry>> = entries
}
