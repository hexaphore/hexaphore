package app.hexaphore.domain.food

import app.hexaphore.domain.nutrition.NutrientValues
import java.time.Instant

/** Identifiant d'une fiche d'aliment. UUIDv4 généré côté application. */
@JvmInline
value class FoodId(val value: String)

/**
 * D'où vient une fiche.
 *
 * Une énumération et non un booléen « personnel ou pas » : les trois provenances se
 * comportent différemment. Un aliment CIQUAL ne se modifie pas, un produit Open Food
 * Facts se rafraîchit, un aliment personnel se supprime.
 */
enum class FoodSource {
    /** Table de l'ANSES, embarquée. Copiée dans le catalogue au premier usage. */
    CIQUAL,

    /** Produit récupéré par code-barres, mis en cache définitivement. */
    OFF,

    /** Créé par l'utilisateur. */
    CUSTOM,
}

/**
 * Une portion nommée : « 1 pomme moyenne », « 1 tranche ».
 *
 * Elle appartient à une fiche, et c'est pour ça qu'elle n'existait pas avant cette
 * tranche : demander à l'utilisateur de définir lui-même ce que pèse une tranche
 * est exactement le travail qu'on veut lui épargner ([D42][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
data class FoodServing(val label: String, val grams: Double, val isDefault: Boolean = false)

/**
 * Une fiche d'aliment : ce qu'on réutilise d'une saisie à l'autre.
 *
 * **Elle ne décide de rien dans le journal.** Une entrée fige ses macros à
 * l'enregistrement et ne relit jamais cette fiche ([D05][decisions]) : la modifier
 * ne peut pas réécrire le passé, et la supprimer ne peut pas l'amputer. Le lien
 * `FoodEntry.foodId` sert à la provenance et au ré-ajout.
 *
 * [per100g] porte six valeurs dont l'énergie peut manquer — voir [NutrientValues].
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/07-modele-de-donnees.md
 */
data class Food(
    val id: FoodId,
    val source: FoodSource,
    /** Code CIQUAL ou code-barres. `null` pour un aliment personnel sans origine. */
    val sourceRef: String? = null,
    val name: String,
    val brand: String? = null,
    /**
     * Le rayon, quand la fiche en a un.
     *
     * `null` pour un aliment personnel, pour un produit scanné, et pour les lignes de
     * l'ANSES qui n'entrent dans aucune des huit cases du bandeau — voir
     * [FoodCategory]. Ce n'est pas un trou à combler : « ne pas avoir de rayon » est
     * une réponse, et la seule honnête pour une huile ou une soupe.
     */
    val category: FoodCategory? = null,
    val per100g: NutrientValues,
    val servings: List<FoodServing> = emptyList(),
    /** Quantité proposée à l'ouverture. `null` vaut 100 g. */
    val defaultServingG: Double? = null,
    /** `null` tant que l'aliment n'a jamais servi. C'est ce que « Récents » filtre. */
    val lastUsedAt: Instant? = null,
    val useCount: Int = 0,
    val favorite: Boolean = false,
) {
    /** La portion proposée par défaut, s'il y en a une. */
    val defaultServing: FoodServing? get() = servings.firstOrNull { it.isDefault }

    /**
     * Ce qu'une fiche modifiable a de particulier.
     *
     * Seul un aliment personnel se modifie et se supprime. Un aliment CIQUAL est une
     * référence publiée ; un produit Open Food Facts est un cache qui se rafraîchit.
     */
    val editable: Boolean get() = source == FoodSource.CUSTOM
}
