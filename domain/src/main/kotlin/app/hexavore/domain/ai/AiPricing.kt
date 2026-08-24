package app.hexavore.domain.ai

import java.time.LocalDate

/**
 * Ce que coûte un million de jetons, chez qui, et **à quelle date on l'a relevé**.
 *
 * [docs/05][ia] veut une estimation « à partir d'une table de tarifs embarquée », et
 * exige dans la même phrase qu'elle soit **datée et signalée comme indicative** : les
 * tarifs changent, et une estimation périmée présentée comme exacte serait pire que pas
 * d'estimation. La date est donc une donnée de premier plan, affichée à côté du
 * montant — pas un commentaire.
 *
 * **Les prix ne sont pas écrits de mémoire.** Ils viennent des pages de tarifs des
 * fournisseurs, relevées le jour dit. C'est la même règle que pour les identifiants de
 * modèles, et elle a déjà démenti ma mémoire trois fois ([D79][decisions],
 * [D81][decisions]).
 *
 * **Un modèle absent de la table n'a pas de prix, et l'écran le dit** plutôt que
 * d'inventer une moyenne. C'est le cas de tout modèle que l'utilisateur saisit
 * lui-même, et de tous ceux du fournisseur « compatible » — dont personne ne peut
 * connaître le tarif, puisque personne ne sait quel service il désigne.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
object AiPricing {
    /** Le jour du relevé. Affiché avec l'estimation, jamais caché dans un commentaire. */
    val ASSESSED_ON: LocalDate = LocalDate.of(2026, 8, 19)

    /**
     * Le tarif d'un modèle, ou `null`.
     *
     * **Deux prudences volontaires**, et elles vont dans le même sens — ne jamais
     * annoncer moins cher que la réalité :
     *
     * - Sonnet 5 est en tarif d'introduction jusqu'au 31 août 2026 ; c'est le tarif
     *   **plein** qui est inscrit, parce qu'une estimation qui expire sans prévenir
     *   ment le lendemain.
     * - DeepSeek facture moitié prix hors des heures pleines ; ce sont les **heures
     *   pleines** qui sont inscrites, parce que l'application ne sait pas à quelle
     *   heure l'appel est parti et que se tromper vers le haut se pardonne.
     */
    fun priceOf(model: String): ModelPrice? = PRICES[model]

    private val PRICES: Map<String, ModelPrice> = mapOf(
        // Anthropic
        "claude-opus-5" to ModelPrice(input = 5.0, output = 25.0),
        "claude-sonnet-5" to ModelPrice(input = 3.0, output = 15.0),
        "claude-haiku-4-5" to ModelPrice(input = 1.0, output = 5.0),
        // Google Gemini
        "gemini-3.5-flash-lite" to ModelPrice(input = 0.30, output = 2.50),
        "gemini-3.7-flash" to ModelPrice(input = 0.75, output = 3.75),
        "gemini-2.5-flash" to ModelPrice(input = 0.30, output = 2.50),
        // OpenAI
        "gpt-5.6-luna" to ModelPrice(input = 0.20, output = 1.20),
        "gpt-5.6-terra" to ModelPrice(input = 2.0, output = 12.0),
        "gpt-5.6-sol" to ModelPrice(input = 5.0, output = 30.0),
        // DeepSeek, en heures pleines
        "deepseek-v4-flash" to ModelPrice(input = 0.44, output = 1.32),
        "deepseek-v4-pro" to ModelPrice(input = 1.32, output = 3.96),
        // Mistral
        "mistral-small-2603" to ModelPrice(input = 0.15, output = 0.60),
        "mistral-medium-3505" to ModelPrice(input = 1.50, output = 7.50),
        "mistral-large-2512" to ModelPrice(input = 0.50, output = 1.50),
    )
}

/**
 * Le tarif d'un modèle, **en dollars par million de jetons**.
 *
 * [docs/05][ia] parle d'euros ; les cinq fournisseurs facturent en dollars. Convertir
 * demanderait un taux de change que l'application n'a aucun moyen de connaître — il
 * faudrait l'inventer, le figer, et le voir vieillir plus vite que les tarifs
 * eux-mêmes. Une estimation dans la devise où la facture tombe est vérifiable ; une
 * conversion à un taux inventé ne l'est pas.
 *
 * [ia]: docs/05-ia.md
 */
data class ModelPrice(val input: Double, val output: Double)

/**
 * Ce qu'un compte a coûté, en dollars — ou `null` si le modèle n'a pas de tarif connu.
 *
 * `null` et non zéro : un modèle inconnu de la table a bien coûté quelque chose, et
 * afficher `0,00 $` en face de deux mille jetons serait un mensonge tranquille.
 */
fun AiUsageEntry.estimatedCost(): Double? = AiPricing.priceOf(model)?.let { price ->
    (input * price.input + output * price.output) / TOKENS_PER_PRICE_UNIT
}

/** Les tarifs s'annoncent par million de jetons ; les compteurs, à l'unité. */
private const val TOKENS_PER_PRICE_UNIT = 1_000_000
