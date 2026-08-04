package app.hexaphore.domain.diary

import java.time.LocalDate

/**
 * Identifiant d'un repas.
 *
 * UUIDv4 généré côté application, et non un entier auto-incrémenté : un compteur
 * rendrait toute fusion de sauvegardes impossible et interdirait des identifiants
 * stables entre appareils.
 */
@JvmInline
value class MealId(val value: String)

/**
 * Les repas d'une journée.
 *
 * Nommés plutôt que chronologiques : c'est ce qui donne des sous-totaux et des
 * repas favoris réutilisables, qui sont le principal levier de rapidité de saisie.
 */
enum class MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,

    /** Repas ajouté par l'utilisateur. Son nom vit dans [Meal.customName]. */
    CUSTOM,
}

/**
 * Un repas d'une journée donnée.
 *
 * Créé **paresseusement**, à la première entrée qu'il contient. Une journée sans
 * saisie ne produit donc aucun repas — c'est ce qui permet de distinguer « rien
 * mangé de noté » de « journée à zéro », distinction dont dépendent le calendrier
 * et l'algorithme d'adaptation hebdomadaire.
 *
 * @see docs/07-modele-de-donnees.md
 */
data class Meal(
    val id: MealId,
    val date: LocalDate,
    val type: MealType,
    /** Renseigné pour [MealType.CUSTOM] seulement. */
    val customName: String?,
    val sortIndex: Int,
)
