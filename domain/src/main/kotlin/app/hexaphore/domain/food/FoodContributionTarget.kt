package app.hexaphore.domain.food

import app.hexaphore.domain.nutrition.NutrientValues

/**
 * Où reverser une fiche saisie à la main, pour qu'elle cesse de rester sur un seul
 * téléphone.
 *
 * **La première écriture sortante de l'application**, et [01][perimetre] n'en prévoit
 * aucune : tout le reste ne fait que lire. C'est ce qui justifie la prudence de tout
 * ce fichier — ce qui part est public, définitif, et relu par d'autres.
 *
 * La raison d'être est mesurée plutôt que supposée ([D70][decisions]) : Open Food
 * Facts compte 1 257 548 produits en France contre 10 911 en Thaïlande. Hors d'Europe,
 * le produit absent n'est pas l'exception mais le cas courant, et créer la fiche à la
 * main devient la route principale. Contribuer est ce qui empêche la même saisie
 * d'être refaite par chacun.
 *
 * **Aucune exception ne franchit cette frontière**, comme pour [ProductSource] : une
 * panne réseau est une réponse possible du port, pas un accident.
 *
 * [perimetre]: docs/01-perimetre.md
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md
 */
fun interface FoodContributionTarget {
    /**
     * Envoie [contribution] sous l'identité de [account].
     *
     * Le compte est passé à l'appel plutôt que tenu par l'implémentation : c'est un
     * secret de l'utilisateur, et un port qui le garderait obligerait chaque appelant
     * à savoir qu'il en garde un.
     */
    suspend fun contribute(contribution: FoodContribution, account: OffAccount): ContributionOutcome
}

/**
 * L'identité sous laquelle on contribue.
 *
 * **Le compte de l'utilisateur, et non un compte de l'application.** Open Food Facts
 * accepte les deux, mais un compte global d'application aurait son mot de passe dans
 * l'APK — c'est-à-dire publié, puisque le code de ce projet l'est. Le premier qui le
 * lirait écrirait dans la base au nom de tout le monde ([D90][decisions]).
 *
 * L'identifiant est un **nom d'utilisateur** et non une adresse électronique : c'est
 * ce que le service attend, et c'est la confusion la plus courante à l'inscription.
 *
 * [decisions]: docs/11-decisions.md
 */
data class OffAccount(val userId: String, val password: String) {
    /** `true` quand les deux champs sont renseignés. Un compte à moitié saisi n'ouvre rien. */
    val usable: Boolean get() = userId.isNotBlank() && password.isNotBlank()

    /**
     * Ni le mot de passe, ni l'identifiant ne s'impriment.
     *
     * L'identifiant est public sur le site, mais il est **la moitié d'un secret** : le
     * publier dans un journal de plantage réduirait le mot de passe au seul obstacle.
     * C'est la même règle que pour les clés d'IA, appliquée avant qu'un journal ne la
     * trahisse ([D77][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    override fun toString(): String = "OffAccount(redacted)"
}

/**
 * Ce qui part, et rien d'autre.
 *
 * Un type distinct de [Food] parce que **tout ce qu'une fiche porte n'est pas
 * contribuable** : ni les compteurs d'usage, qui ne regardent personne, ni les valeurs
 * complétées par un modèle ([D89][decisions]), qui n'ont été mesurées nulle part. Une
 * base collaborative ne se remplit pas de chiffres qu'aucune étiquette n'a portés.
 *
 * Le construire depuis une fiche est le rôle de [FoodContribution.of], et c'est là que
 * la règle se tient — pas dans l'écran, qui l'oublierait.
 *
 * [decisions]: docs/11-decisions.md
 */
data class FoodContribution(
    val barcode: Barcode,
    val name: String,
    val brand: String?,
    /** Les six teneurs pour 100 g. Celles qui manquent ne partent pas. */
    val per100g: NutrientValues,
    /** Le poids d'une portion, quand la fiche en déclare un. */
    val servingGrams: Double?,
) {
    companion object {
        /**
         * La contribution que cette fiche produirait, ou `null` si elle n'est pas
         * contribuable.
         *
         * **Quatre conditions, et chacune a son refus.** Un code-barres **valide** —
         * [Barcode.of] en vérifie la clé de contrôle, et une suite de chiffres qui
         * n'en est pas un désignerait un produit qui n'existe pas. Une provenance
         * personnelle, parce qu'on ne reverse pas la table de l'ANSES à Open Food
         * Facts et qu'on n'écrit pas par-dessus le travail d'un autre contributeur.
         * Aucune valeur estimée, parce qu'un chiffre inventé qui entre dans une base
         * publique y devient une mesure pour tous ceux qui le liront. Et un nom : c'est
         * le seul champ que le service exige en pratique, et une fiche sans nom n'est
         * pas retrouvable.
         */
        fun of(food: Food): FoodContribution? {
            val barcode = food.sourceRef?.let(Barcode::of)
            val contributable = food.source == FoodSource.CUSTOM &&
                food.estimated.isEmpty() &&
                food.name.isNotBlank()

            return when {
                barcode == null || !contributable -> null
                else -> FoodContribution(
                    barcode = barcode,
                    name = food.name.trim(),
                    brand = food.brand?.trim()?.takeIf { it.isNotEmpty() },
                    per100g = food.per100g,
                    servingGrams = food.defaultServing?.grams ?: food.defaultServingG,
                )
            }
        }
    }
}

/**
 * Ce que le service peut répondre à un envoi.
 *
 * Quatre cas, et ils appellent quatre conduites : réessayer, corriger son compte,
 * attendre, ou renoncer. Les fondre en « ça n'a pas marché » ferait chercher au
 * hasard, et c'est une écriture qu'on ne veut pas relancer à l'aveugle.
 */
sealed interface ContributionOutcome {
    /** La fiche est passée. Elle est publique, et elle ne se reprend pas. */
    data object Sent : ContributionOutcome

    /**
     * Le compte a été refusé.
     *
     * Distinct du réseau parce que réessayer n'y changera rien : il faut corriger
     * l'identifiant ou le mot de passe. C'est aussi le cas d'un identifiant saisi
     * comme une adresse électronique, l'erreur la plus fréquente.
     */
    data object Rejected : ContributionOutcome

    /** La question n'a pas pu être posée : hors ligne, ou le service ne répond pas. */
    data object Unreachable : ContributionOutcome

    /**
     * Le service a répondu, et a refusé la fiche.
     *
     * [reason] est son propre message, repris tel quel. Le traduire demanderait de
     * connaître à l'avance ce qu'il peut refuser ; le taire laisserait devant un échec
     * sans cause. C'est la même règle que pour les fournisseurs d'IA — le service
     * garde la parole ([D78][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    data class Refused(val reason: String) : ContributionOutcome
}
