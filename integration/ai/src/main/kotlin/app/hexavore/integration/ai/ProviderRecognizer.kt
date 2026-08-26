package app.hexavore.integration.ai

import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.CatalogueTool
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome

/**
 * Ce qu'un fournisseur doit savoir faire, et rien de plus.
 *
 * **La configuration est un paramètre et non une dépendance de construction.** Elle
 * change quand l'utilisateur corrige sa clé ou son modèle, alors que l'objet vit
 * aussi longtemps que l'application : la passer au constructeur obligerait à
 * reconstruire les six fournisseurs à chaque modification d'un réglage, et à trouver
 * qui s'en charge.
 *
 * C'est aussi ce qui garde ce contrat **hors du domaine** : `FoodRecognizer` ne parle
 * pas de clés d'API, et ne doit pas commencer.
 *
 * **Deux méthodes et non deux interfaces**, alors que le domaine, lui, en déclare bien
 * deux — `FoodRecognizer` et `NutritionEstimator`. La division qui compte est celle du
 * domaine : elle dit que reconnaître et estimer ne sont pas la même question, et
 * n'appellent pas la même confiance. Ici, en dessous, c'est le **même** fournisseur,
 * la même clé, la même pile HTTP et la même traduction des codes ; les séparer aurait
 * fait deux objets par fournisseur — douze — pour un seul appel HTTP de différence.
 *
 * @see docs/05-ia.md § Ajouter un fournisseur
 */
internal interface ProviderRecognizer {
    suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome

    /**
     * L'étape 4 de [docs/04][sources] : ce que valent, pour 100 g, des libellés
     * qu'aucune base ne connaît.
     *
     * Un appel de plus, avec un autre prompt et un autre schéma — mais la même route,
     * la même clé et le même parseur tolérant.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome

    /**
     * La même reconnaissance, mais le modèle interroge le catalogue avant de conclure.
     *
     * **Une méthode et non un drapeau sur [recognize]** : ce sont deux conversations
     * différentes — l'une envoie une requête et lit une réponse, l'autre tient un
     * historique sur plusieurs tours. Les fondre aurait mis un `if` au début d'une
     * fonction dont les deux moitiés n'ont rien en commun.
     *
     * **Le défaut rend un échec**, et c'est ce qui permet à un fournisseur sans
     * outillage d'exister : l'appelant retombe alors sur l'analyse ordinaire, ce qui
     * est exactement ce qu'on veut d'un mode qui n'est pas disponible.
     */
    suspend fun deepRecognize(
        input: RecognitionInput,
        configuration: AiConfiguration,
        catalogue: CatalogueTool,
    ): RecognitionOutcome = RecognitionOutcome.Failed(AiError.NothingRecognized)
}
