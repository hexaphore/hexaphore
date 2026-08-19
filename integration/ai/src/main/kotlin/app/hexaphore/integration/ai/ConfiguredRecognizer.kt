package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiConfiguration
import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.AiProbe
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.AiSettings
import app.hexaphore.domain.ai.EstimationOutcome
import app.hexaphore.domain.ai.FoodRecognizer
import app.hexaphore.domain.ai.NutritionEstimator
import app.hexaphore.domain.ai.ProbeOutcome
import app.hexaphore.domain.ai.RecognitionInput
import app.hexaphore.domain.ai.RecognitionOutcome
import app.hexaphore.domain.ai.VisionSupport

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
internal class ConfiguredRecognizer(
    private val settings: AiSettings,
    private val anthropic: ProviderRecognizer,
    private val gemini: ProviderRecognizer,
    private val openAi: ProviderRecognizer,
    private val compatible: ProviderRecognizer,
) : FoodRecognizer,
    NutritionEstimator,
    AiProbe {
    override suspend fun recognize(input: RecognitionInput): RecognitionOutcome {
        val configuration = settings.current() ?: return RecognitionOutcome.Failed(AiError.NoProviderConfigured)
        return configuration.provider.recognizer().recognize(input, configuration)
    }

    /**
     * L'étape 4, routée comme le reste.
     *
     * **Sans configuration, aucun appel** — et surtout aucune estimation : une ligne
     * non résolue reste alors à compléter à la main, ce qui est exactement ce qu'elle
     * était avant. Le repli ne doit pas devenir la raison pour laquelle une clé
     * manquante se voit.
     *
     * Une liste vide ne part jamais non plus : c'est le cas courant — tout a été
     * résolu — et il ne coûte rien.
     */
    override suspend fun estimate(labels: List<String>): EstimationOutcome = when {
        labels.isEmpty() -> EstimationOutcome.Estimated(emptyList())
        else ->
            settings
                .current()
                ?.let { configuration -> configuration.provider.recognizer().estimate(labels, configuration) }
                ?: EstimationOutcome.Failed(AiError.NoProviderConfigured)
    }

    /**
     * **Le sondage est une vraie reconnaissance**, sur une phrase minuscule.
     *
     * C'est ce qui le rend digne de confiance : il emprunte exactement le chemin
     * qu'empruntera la première photo — même prompt, même schéma, même parseur, même
     * traduction des codes. Un appel spécial, plus léger, aurait pu réussir là où
     * l'analyse échoue, et un bouton « Tester » qui dit oui à tort est pire que pas de
     * bouton du tout.
     *
     * Il utilise la configuration **du formulaire** et non celle qui est enregistrée :
     * tester après avoir écrit reviendrait à enregistrer une clé fausse pour découvrir
     * qu'elle est fausse.
     */
    override suspend fun probe(configuration: AiConfiguration): ProbeOutcome =
        when (val outcome = configuration.provider.recognizer().recognize(PROBE, configuration)) {
            is RecognitionOutcome.Recognized -> configuration.provider.reachable()
            is RecognitionOutcome.Failed -> outcome.error.toProbe(configuration.provider)
        }

    /**
     * **Six entrées, quatre implémentations.** Les quatre derniers fournisseurs
     * parlent le même protocole et ne diffèrent que par un réglage — le schéma que
     * l'un accepte et que les autres refusent —, porté par deux instances de la même
     * classe. C'est ici, et nulle part ailleurs, qu'on sait laquelle va à qui.
     */
    private fun AiProvider.recognizer(): ProviderRecognizer = when (this) {
        AiProvider.ANTHROPIC -> anthropic
        AiProvider.GEMINI -> gemini
        AiProvider.OPENAI -> openAi
        AiProvider.DEEPSEEK, AiProvider.MISTRAL, AiProvider.COMPATIBLE -> compatible
    }
}

/**
 * Ce qu'une issue d'analyse dit de la **configuration**, qui n'est pas la même
 * question.
 *
 * Une réponse illisible ou vide est un échec de l'analyse et une **réussite** du
 * sondage : le fournisseur a répondu, donc la clé est bonne et le modèle existe. Les
 * confondre ferait échouer le test pour une phrase que le modèle a mal comprise, et
 * enverrait l'utilisateur corriger une clé qui n'a rien.
 */
private fun AiError.toProbe(provider: AiProvider): ProbeOutcome = when (this) {
    AiError.Unparseable, AiError.NothingRecognized -> provider.reachable()
    else -> ProbeOutcome.Failed(this)
}

/**
 * La capacité vision, telle qu'on la connaît.
 *
 * Pour les fournisseurs dont toute la gamme lit les images, elle est **su** et non
 * détectée. Pour les autres, il faudra joindre une image minuscule au sondage, et ce
 * sera le travail de la livraison qui les apporte : l'écrire maintenant serait deviner
 * pour des fournisseurs qui n'existent pas encore.
 */
private fun AiProvider.reachable() = ProbeOutcome.Reachable(vision = vision == VisionSupport.ALWAYS)

/** Aussi court que possible : le sondage se paie, et il se paie souvent. */
private val PROBE = RecognitionInput.Text("un verre d'eau")
