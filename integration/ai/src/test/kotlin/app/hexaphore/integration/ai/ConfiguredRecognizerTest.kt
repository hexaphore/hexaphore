package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiConfiguration
import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.AiSettings
import app.hexaphore.domain.ai.ApiKey
import app.hexaphore.domain.ai.EstimationOutcome
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
        val recognizer = factory(settings = { null }, anthropic = record { seen = it })

        val outcome = recognizer.recognize(RecognitionInput.Text("un jus"))

        assertEquals(RecognitionOutcome.Failed(AiError.NoProviderConfigured), outcome)
        assertNull(seen, "une analyse sans cle ne doit pas partir sur le reseau")
    }

    @Test
    fun `la configuration courante accompagne l appel`() = runTest {
        var seen: AiConfiguration? = null
        val recognizer = factory(settings = { CONFIGURATION }, anthropic = record { seen = it })

        recognizer.recognize(RecognitionInput.Text("un jus"))

        assertEquals(CONFIGURATION, seen)
    }

    @Test
    fun `le sondage marche sans rien d enregistre`() = runTest {
        // C'est tout l'interet du bouton : on eprouve ce qui est dans le formulaire,
        // avant d'ecrire. Lire les reglages ici obligerait a enregistrer une cle
        // fausse pour decouvrir qu'elle est fausse.
        val recognizer = factory(anthropic = record { })

        assertEquals(ProbeOutcome.Reachable(vision = true), recognizer.probe(CONFIGURATION))
    }

    @Test
    fun `une reponse illisible reste une configuration valide`() = runTest {
        // Le fournisseur a repondu : la cle est bonne et le modele existe. Echouer ici
        // enverrait quelqu'un corriger une cle qui n'a rien.
        val recognizer = factory(anthropic = recognizing { RecognitionOutcome.Failed(AiError.Unparseable) })

        assertEquals(ProbeOutcome.Reachable(vision = true), recognizer.probe(CONFIGURATION))
    }

    @Test
    fun `une cle refusee fait echouer le sondage`() = runTest {
        val recognizer = factory(anthropic = recognizing { RecognitionOutcome.Failed(AiError.InvalidKey) })

        assertEquals(ProbeOutcome.Failed(AiError.InvalidKey), recognizer.probe(CONFIGURATION))
    }

    @Test
    fun `le sondage emprunte le chemin d une vraie analyse`() = runTest {
        // Un appel special, plus leger, aurait pu reussir la ou l'analyse echoue. Un
        // bouton « Tester » qui dit oui a tort est pire que pas de bouton.
        var input: RecognitionInput? = null
        val recognizer = factory(
            anthropic = recognizing { received ->
                input = received
                RecognitionOutcome.Recognized(Recognition(items = emptyList()))
            },
        )

        recognizer.probe(CONFIGURATION)

        assertTrue(input is RecognitionInput.Text, "le sondage doit passer par le contrat de reconnaissance")
    }

    @Test
    fun `chaque entree atteint l implementation qui lui revient`() = runTest {
        // La regle que le `when` de la fabrique porte, et la seule chose qu'il decide.
        // Router vers la mauvaise donnerait une cle refusee sur une cle parfaitement
        // valide -- et, entre les quatre derniers, un schema envoye a qui le refuse.
        val attendu = mapOf(
            AiProvider.ANTHROPIC to "anthropic",
            AiProvider.GEMINI to "gemini",
            AiProvider.OPENAI to "openAi",
            AiProvider.DEEPSEEK to "compatible",
            AiProvider.MISTRAL to "compatible",
            AiProvider.COMPATIBLE to "compatible",
        )

        // Toutes les entrees, et non celles qu'on a pensees : une entree ajoutee sans
        // branche ne compile pas, mais une entree ajoutee **avec** la mauvaise branche
        // compile tres bien.
        assertEquals(AiProvider.entries.toSet(), attendu.keys, "un fournisseur sans attente")

        attendu.forEach { (provider, implementation) ->
            var atteint: String? = null
            val recognizer = factory(
                anthropic = mark("anthropic") { atteint = it },
                gemini = mark("gemini") { atteint = it },
                openAi = mark("openAi") { atteint = it },
                compatible = mark("compatible") { atteint = it },
            )

            recognizer.probe(CONFIGURATION.copy(provider = provider))

            assertEquals(implementation, atteint, "mauvaise implementation pour $provider")
        }
    }

    @Test
    fun `une liste vide n atteint aucun fournisseur`() = runTest {
        // Le cas courant : toutes les lignes ont ete resolues. Un appel qui partirait
        // quand meme ne rendrait rien et se paierait.
        val recognizer = factory(settings = { CONFIGURATION })

        assertEquals(EstimationOutcome.Estimated(emptyList()), recognizer.estimate(emptyList()))
    }

    @Test
    fun `sans configuration, aucune estimation ne part`() = runTest {
        // Le repli ne doit pas devenir la raison pour laquelle une cle manquante se
        // voit : la ligne reste a completer a la main, comme avant l'appel.
        val recognizer = factory(settings = { null })

        assertEquals(
            EstimationOutcome.Failed(AiError.NoProviderConfigured),
            recognizer.estimate(listOf("sauce maison")),
        )
    }

    @Test
    fun `l estimation emprunte le meme routage que l analyse`() = runTest {
        // Sinon une cle valide chez l'un partirait chez l'autre, et le 401 accuserait
        // la cle.
        var atteint: String? = null
        val recognizer = factory(
            settings = { CONFIGURATION.copy(provider = AiProvider.MISTRAL) },
            compatible = estimating("compatible") { atteint = it },
        )

        recognizer.estimate(listOf("sauce maison"))

        assertEquals("compatible", atteint)
    }

    /** Un fournisseur qui ne sait qu'estimer, pendant du [recognizing] ci-dessous. */
    private fun estimating(implementation: String, onCall: (String) -> Unit) = object : ProviderRecognizer {
        override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome =
            error("ce cas ne parle pas de la reconnaissance")

        override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome {
            onCall(implementation)
            return EstimationOutcome.Estimated(emptyList())
        }
    }

    private fun mark(implementation: String, onCall: (String) -> Unit) = recognizing {
        onCall(implementation)
        RecognitionOutcome.Recognized(Recognition(items = emptyList()))
    }

    /**
     * Un fournisseur qui ne sait que reconnaitre.
     *
     * `ProviderRecognizer` porte deux methodes depuis l'etape 4, et ces cas ne parlent
     * que de la premiere. L'estimation echoue donc bruyamment : un cas de routage qui
     * l'atteindrait sans le vouloir doit s'en apercevoir.
     */
    private fun recognizing(onRecognize: suspend (RecognitionInput) -> RecognitionOutcome) =
        object : ProviderRecognizer {
            override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration) = onRecognize(input)

            override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome =
                error("ce cas ne parle pas de l estimation")
        }

    /**
     * La fabrique, avec des fournisseurs qui echouent bruyamment par defaut.
     *
     * Chaque cas ne nomme que ceux qu'il attend ; les autres crient s'ils sont
     * atteints, plutot que de rendre une reponse plausible sur la mauvaise branche.
     */
    private fun factory(
        settings: AiSettings = AiSettings { null },
        anthropic: ProviderRecognizer = unused(),
        gemini: ProviderRecognizer = unused(),
        openAi: ProviderRecognizer = unused(),
        compatible: ProviderRecognizer = unused(),
    ) = ConfiguredRecognizer(settings, anthropic, gemini, openAi, compatible)

    /**
     * Un fournisseur que ces cas ne doivent jamais atteindre.
     *
     * Il echoue bruyamment plutot que de rendre une reponse plausible : la fabrique
     * qui se tromperait de branche donnerait sinon un test vert sur le mauvais
     * fournisseur.
     */
    private fun unused() = object : ProviderRecognizer {
        override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome =
            error("la fabrique s est trompee de fournisseur")

        override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome =
            error("la fabrique s est trompee de fournisseur")
    }

    private fun record(onCall: (AiConfiguration) -> Unit) = object : ProviderRecognizer {
        override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome {
            onCall(configuration)
            return RecognitionOutcome.Recognized(Recognition(items = emptyList()))
        }

        override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome =
            error("ce cas ne parle pas de l estimation")
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
