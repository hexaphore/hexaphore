package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiConfiguration
import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.ApiKey
import app.hexaphore.domain.ai.ProbeOutcome
import app.hexaphore.domain.ai.Recognition
import app.hexaphore.domain.ai.RecognitionInput
import app.hexaphore.domain.ai.RecognitionOutcome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `le sondage marche sans rien d enregistre`() = runTest {
        // C'est tout l'interet du bouton : on eprouve ce qui est dans le formulaire,
        // avant d'ecrire. Lire les reglages ici obligerait a enregistrer une cle
        // fausse pour decouvrir qu'elle est fausse.
        val recognizer = ConfiguredRecognizer(settings = { null }, anthropic = record { })

        assertEquals(ProbeOutcome.Reachable(vision = true), recognizer.probe(CONFIGURATION))
    }

    @Test
    fun `une reponse illisible reste une configuration valide`() = runTest {
        // Le fournisseur a repondu : la cle est bonne et le modele existe. Echouer ici
        // enverrait quelqu'un corriger une cle qui n'a rien.
        val recognizer = ConfiguredRecognizer(
            settings = { null },
            anthropic = { _, _ -> RecognitionOutcome.Failed(AiError.Unparseable) },
        )

        assertEquals(ProbeOutcome.Reachable(vision = true), recognizer.probe(CONFIGURATION))
    }

    @Test
    fun `une cle refusee fait echouer le sondage`() = runTest {
        val recognizer = ConfiguredRecognizer(
            settings = { null },
            anthropic = { _, _ -> RecognitionOutcome.Failed(AiError.InvalidKey) },
        )

        assertEquals(ProbeOutcome.Failed(AiError.InvalidKey), recognizer.probe(CONFIGURATION))
    }

    @Test
    fun `le sondage emprunte le chemin d une vraie analyse`() = runTest {
        // Un appel special, plus leger, aurait pu reussir la ou l'analyse echoue. Un
        // bouton « Tester » qui dit oui a tort est pire que pas de bouton.
        var input: RecognitionInput? = null
        val recognizer = ConfiguredRecognizer(
            settings = { null },
            anthropic = { received, _ ->
                input = received
                RecognitionOutcome.Recognized(Recognition(items = emptyList()))
            },
        )

        recognizer.probe(CONFIGURATION)

        assertTrue(input is RecognitionInput.Text, "le sondage doit passer par le contrat de reconnaissance")
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
