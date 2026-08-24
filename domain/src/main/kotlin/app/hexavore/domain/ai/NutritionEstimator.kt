package app.hexavore.domain.ai

import app.hexavore.domain.nutrition.NutrientValues

/**
 * Le dernier recours : demander au modèle ce que pèsent en nutriments les aliments
 * qu'aucune base ne connaît.
 *
 * **C'est l'exception à la règle la plus structurante du projet.** [docs/05][ia] pose
 * que le modèle identifie et que les bases calculent ; l'étape 4 de [docs/04][sources]
 * ouvre une porte étroite pour les libellés que la résolution n'a pas rejoints — « tofu
 * fumé au sésame » n'est ni dans l'ANSES ni dans le cache d'Open Food Facts, et la
 * ligne arriverait sinon sans énergie, donc non enregistrable.
 *
 * Trois garde-fous tiennent la porte étroite :
 *
 * 1. **Un seul appel groupé**, jamais un par ligne. Cinq lignes non résolues coûtent
 *    une requête, pas cinq.
 * 2. **Rien n'entre au catalogue.** Une estimation n'est pas une référence : elle ne
 *    doit pas remonter dans une recherche, ni servir de base à un autre repas.
 * 3. **La ligne le dit.** Un chiffre inventé qui s'affiche comme un chiffre mesuré est
 *    pire que pas de chiffre du tout.
 *
 * **Un port distinct de [FoodRecognizer]**, malgré le même fournisseur derrière : ce
 * n'est pas la même question, pas le même prompt, pas le même schéma de réponse, et
 * surtout pas la même confiance à accorder au résultat. Les fondre en un seul port
 * ferait porter à « reconnaître » une responsabilité que [docs/05][ia] lui refuse.
 *
 * [ia]: docs/05-ia.md
 * [sources]: docs/04-sources-de-donnees.md
 */
fun interface NutritionEstimator {
    /**
     * Les valeurs pour 100 g de chaque libellé, dans la mesure du possible.
     *
     * Une liste vide en entrée ne doit **pas** partir sur le réseau : c'est le cas
     * courant — toutes les lignes ont été résolues —, et il ne coûte rien.
     */
    suspend fun estimate(labels: List<String>): EstimationOutcome
}

/** Ce qu'une estimation groupée peut répondre. Deux cas, comme la reconnaissance. */
sealed interface EstimationOutcome {
    /**
     * Ce que le modèle a su estimer.
     *
     * [usage] accompagne la réponse pour la même raison que celle de la
     * reconnaissance : cet appel se paie, et le compteur doit pouvoir le dire.
     *
     * **Pas forcément tous les libellés demandés**, et l'appelant ne doit pas le
     * supposer : un modèle qui ne sait pas quoi répondre pour « sauce maison » a raison
     * de se taire, et la ligne reste alors à compléter à la main.
     */
    data class Estimated(val foods: List<EstimatedFood>, val usage: TokenUsage? = null) : EstimationOutcome

    data class Failed(val error: AiError) : EstimationOutcome
}

/**
 * Un libellé et ce que le modèle lui prête, pour 100 g.
 *
 * [label] est **celui qui a été demandé**, à l'identique : c'est ce qui permet de
 * recoller l'estimation à sa ligne. Un modèle qui reformule — « tofu fumé » pour « tofu
 * fumé au sésame » — rend une estimation qu'on ne peut plus rattacher, et elle est
 * écartée plutôt que devinée.
 */
data class EstimatedFood(val label: String, val per100g: NutrientValues)
