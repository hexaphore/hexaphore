package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.AiSettings
import app.hexaphore.domain.ai.FoodRecognizer
import app.hexaphore.domain.ai.RecognitionInput
import app.hexaphore.domain.ai.RecognitionOutcome

/**
 * La fabrique : le seul endroit du projet qui sache qu'il existe plusieurs
 * fournisseurs.
 *
 * L'application n'injecte que `FoodRecognizer`. Aucun écran, aucun cas d'usage et
 * aucun test n'a de raison de nommer un fournisseur — c'est le critère de fin de la
 * tranche, et [docs/12][plan] nomme le contraire comme le signal que l'abstraction a
 * fui.
 *
 * **Un `when` exhaustif plutôt qu'une carte de liaisons Hilt.** La carte serait plus
 * savante et strictement moins sûre : oublier d'y inscrire un fournisseur donne un
 * plantage à l'exécution, sur l'appareil de quelqu'un. Ici, ajouter une entrée à
 * [AiProvider] sans écrire sa classe **ne compile pas**. La vérification
 * d'exhaustivité de Kotlin fait gratuitement le travail qu'une carte demanderait de
 * se rappeler.
 *
 * **La configuration est relue à chaque appel.** Une clé corrigée s'applique donc à
 * l'analyse suivante sans rien redémarrer, et une analyse déjà partie ne change pas
 * de fournisseur en vol.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
internal class ConfiguredRecognizer(private val settings: AiSettings, private val anthropic: ProviderRecognizer) :
    FoodRecognizer {
    override suspend fun recognize(input: RecognitionInput): RecognitionOutcome {
        val configuration = settings.current() ?: return RecognitionOutcome.Failed(AiError.NoProviderConfigured)
        return configuration.provider.recognizer().recognize(input, configuration)
    }

    private fun AiProvider.recognizer(): ProviderRecognizer = when (this) {
        AiProvider.ANTHROPIC -> anthropic
    }
}
