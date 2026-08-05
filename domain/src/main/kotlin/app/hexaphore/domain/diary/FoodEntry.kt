package app.hexaphore.domain.diary

import app.hexaphore.domain.nutrition.Macros

/** Identifiant d'une ligne de journal. UUIDv4 généré côté application. */
@JvmInline
value class EntryId(val value: String)

/**
 * Un aliment d'un plat.
 *
 * Ses [macros] sont **figées** à l'enregistrement, et non recalculées depuis
 * l'aliment d'origine. Un journal alimentaire est un registre d'événements : ce qui
 * est écrit est écrit. Sans cela, un fabricant qui reformule son produit réécrirait
 * un journal vieux de six mois, et supprimer un aliment amputerait l'historique.
 *
 * [displayName] est figé pour la même raison : la ligne doit rester lisible même
 * si l'aliment qui l'a produite disparaît.
 *
 * Aucune source ici : elle appartient au plat ([Dish.source]). Une ligne n'entre
 * jamais dans le journal toute seule.
 *
 * @see docs/11-decisions.md — D05, D32
 */
data class FoodEntry(
    val id: EntryId,
    val dishId: DishId,
    val displayName: String,
    /** Quantité telle que saisie, dans l'unité choisie par l'utilisateur. */
    val quantity: Double,
    /** Unité affichée. La conversion en grammes est faite à la saisie. */
    val unit: String,
    /** Quantité convertie. C'est elle qui sert à tout calcul. */
    val grams: Double,
    val macros: Macros,
)
