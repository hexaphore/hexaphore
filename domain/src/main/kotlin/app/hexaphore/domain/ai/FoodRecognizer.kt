package app.hexaphore.domain.ai

/**
 * Extrait une liste d'aliments consommés à partir d'une photo ou d'une description.
 *
 * **Il ne produit aucune valeur nutritionnelle**, et c'est la décision la plus
 * structurante de [docs/05][ia] : le modèle identifie et estime des quantités, le
 * résolveur calcule les macros à partir de bases traçables. Ce découpage rend les
 * résultats reproductibles — deux analyses de la même assiette donnent les mêmes
 * calories —, sourçables, et moins coûteux.
 *
 * **Une fonction, pas un objet à quinze méthodes.** Photo et texte partagent le même
 * contrat, donc le même pipeline, le même écran de validation et les mêmes tests.
 * Ajouter la dictée vocale reviendra à ajouter une variante de [RecognitionInput].
 *
 * **Aucune exception ne franchit cette frontière** : une clé refusée, un quota
 * épuisé ou une réponse illisible sont des réponses possibles du port, pas des
 * accidents. Elles ont leurs cas dans [AiError] — le raisonnement de [D63][decisions]
 * pour `ProductSource`, appliqué une seconde fois.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
fun interface FoodRecognizer {
    suspend fun recognize(input: RecognitionInput): RecognitionOutcome
}

/** Ce qu'on soumet au modèle. Deux formes aujourd'hui, le même pipeline derrière. */
sealed interface RecognitionInput {
    /**
     * Une photo du repas, et ce que l'utilisateur veut préciser.
     *
     * **Pas une `data class`, et ce n'est pas un oubli.** Une `data class` qui porte
     * un `ByteArray` fabrique une égalité fausse : elle compare les références du
     * tableau, donc deux photos identiques ne sont jamais égales et une même photo
     * l'est toujours. Personne n'a besoin de comparer des photos ; personne ne doit
     * croire qu'il le peut.
     *
     * [note] est le levier de précision le moins coûteux qui existe — « l'assiette
     * fait 24 cm », « la sauce est allégée » — parce qu'un modèle voit mal les
     * quantités sans référence d'échelle ([docs/05][ia] § Limites assumées).
     *
     * [ia]: docs/05-ia.md
     */
    class Photo(val jpeg: ByteArray, val note: String?) : RecognitionInput

    /** Une phrase : « un bol de riz et deux œufs ». */
    data class Text(val description: String) : RecognitionInput
}

/**
 * Ce que la reconnaissance peut répondre.
 *
 * Deux cas et non un `Result` : [kotlin.Result] exige une `Throwable` en échec, ce
 * qui obligerait [AiError] à être une exception. Or ces issues sont **attendues** —
 * un quota épuisé n'est pas un accident de programmation — et [docs/06][architecture]
 * § L interdit qu'une implémentation en lève une. C'est la forme de `ProductLookup`,
 * pour la même raison.
 *
 * [architecture]: docs/06-architecture.md
 */
sealed interface RecognitionOutcome {
    /** Au moins une ligne exploitable. Jamais zéro : voir [AiError.NothingRecognized]. */
    data class Recognized(val recognition: Recognition) : RecognitionOutcome

    data class Failed(val error: AiError) : RecognitionOutcome
}
