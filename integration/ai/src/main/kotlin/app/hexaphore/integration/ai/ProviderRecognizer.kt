package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiConfiguration
import app.hexaphore.domain.ai.EstimationOutcome
import app.hexaphore.domain.ai.RecognitionInput
import app.hexaphore.domain.ai.RecognitionOutcome

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
}
