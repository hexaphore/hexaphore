package app.hexavore.core.testing

import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.AiSetup
import app.hexavore.domain.ai.ProviderCredentials
import app.hexavore.domain.ai.activeConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Les réglages d'IA en mémoire.
 *
 * Ce n'est pas une béquille de test : c'est la **première implémentation** des deux
 * ports, celle contre laquelle l'écran est écrit avant que le stockage chiffré
 * n'arrive. Basculer vers lui ne change qu'une liaison Hilt.
 *
 * **Une seule classe pour deux ports, comme le vrai.** [AiSettings] est la facette que
 * le résolveur voit ; la séparation existe pour ses appelants, pas pour multiplier les
 * objets.
 */
class InMemoryAiCredentials(initial: AiSetup = AiSetup()) :
    AiCredentials,
    AiSettings {
    private val setup = MutableStateFlow(initial)

    override fun observe(): Flow<AiSetup> = setup

    override suspend fun save(provider: AiProvider, credentials: ProviderCredentials) {
        setup.value =
            setup.value.copy(active = provider, credentials = setup.value.credentials + (provider to credentials))
    }

    override suspend fun forget(provider: AiProvider) {
        val remaining = setup.value.credentials - provider
        setup.value = AiSetup(
            active = setup.value.active.takeIf { it != provider },
            credentials = remaining,
        )
    }

    override suspend fun activate(provider: AiProvider) {
        if (provider in setup.value.credentials) setup.value = setup.value.copy(active = provider)
    }

    override suspend fun current(): AiConfiguration? = setup.value.activeConfiguration()
}
