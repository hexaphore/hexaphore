package app.hexaphore.data.settings

import app.hexaphore.core.testing.InMemoryAiCredentials
import app.hexaphore.domain.ai.AiCredentials

/**
 * Le même contrat, joué sur l'implémentation en mémoire.
 *
 * Côte à côte avec [StoredAiCredentialsTest] dans le même rapport : une propriété que
 * le faux s'autoriserait à ne pas tenir devient une ligne rouge à côté d'une verte, et
 * non une découverte sur l'appareil.
 */
class InMemoryAiCredentialsTest : AiCredentialsContract() {
    override fun store(): AiCredentialsView {
        val memoire = InMemoryAiCredentials()
        return object : AiCredentialsView, AiCredentials by memoire {
            override suspend fun current() = memoire.current()
        }
    }
}
