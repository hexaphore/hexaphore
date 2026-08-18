package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiConfiguration
import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.ApiKey
import app.hexaphore.domain.ai.Recognition
import app.hexaphore.domain.ai.RecognitionInput
import app.hexaphore.domain.ai.RecognitionOutcome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * La fabrique, sans réseau : ce qu'elle décide n'a rien à voir avec ce qu'un
 * fournisseur répond.
 *
 * Deux règles seulement, et la première est celle qui rend les boutons IA utilisables
 * sans clé : **une absence de configuration ne doit atteindre aucun fournisseur.**
 * Appeler quand même rendrait une erreur d'authentification là où il n'y a
 * simplement rien de configuré — et enverrait une requête payante dans le vide.
 */
class ConfiguredRecognizerTest {
    @Test
    fun `sans configuration, aucun fournisseur n est appele`() = runTest {
        var seen: AiConfiguration? = null
        val recognizer = ConfiguredRecognizer(settings = { null }, anthropic = record { seen = it })

        val outcome = recognizer.recognize(RecognitionInput.Text("un jus"))

        assertEquals(RecognitionOutcome.Failed(AiError.NoProviderConfigured), outcome)
        assertNull(seen, "une analyse sans cle ne doit pas partir sur le reseau")
    }

    @Test
    fun `la configuration courante accompagne l appel`() = runTest {
        var seen: AiConfiguration? = null
        val recognizer = ConfiguredRecognizer(settings = { CONFIGURATION }, anthropic = record { seen = it })

        recognizer.recognize(RecognitionInput.Text("un jus"))

        assertEquals(CONFIGURATION, seen)
    }

    private fun record(onCall: (AiConfiguration) -> Unit) = ProviderRecognizer { _, configuration ->
        onCall(configuration)
        RecognitionOutcome.Recognized(Recognition(items = emptyList()))
    }

    private companion object {
        val CONFIGURATION = AiConfiguration(
            provider = AiProvider.ANTHROPIC,
            apiKey = ApiKey("sk-ant-de-test"),
            model = "claude-opus-5",
            baseUrl = "https://api.anthropic.com/",
        )
    }
}
