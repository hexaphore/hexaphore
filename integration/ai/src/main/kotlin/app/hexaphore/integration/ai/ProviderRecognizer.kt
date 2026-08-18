package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiConfiguration
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
 * @see docs/05-ia.md § Ajouter un fournisseur
 */
internal fun interface ProviderRecognizer {
    suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome
}
