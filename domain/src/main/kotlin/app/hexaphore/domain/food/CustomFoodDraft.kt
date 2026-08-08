package app.hexaphore.domain.food

import app.hexaphore.domain.nutrition.NutrientValues

/**
 * Ce que le formulaire d'aliment personnel manipule.
 *
 * Distinct de [Food] pour la même raison que [DraftLine][app.hexaphore.domain.diary.DraftLine]
 * est distinct de [FoodEntry][app.hexaphore.domain.diary.FoodEntry] : une fiche en
 * cours de saisie n'a pas encore d'identifiant, et ses champs sont incomplets par
 * nature. Réutiliser [Food] obligerait à lui inventer un identifiant avant qu'elle
 * existe, donc à ne plus distinguer les deux états.
 *
 * @see docs/04-sources-de-donnees.md
 */
data class CustomFoodDraft(
    /** `null` pour une création ; renseigné quand on modifie une fiche existante. */
    val id: FoodId? = null,
    val name: String = "",
    val brand: String = "",
    /** Les six teneurs pour 100 g. Seule l'énergie est exigée. */
    val per100g: NutrientValues = NutrientValues(),
    /** Quantité proposée à l'ouverture. `null` vaut 100 g. */
    val defaultServingG: Double? = null,
) {
    /**
     * `true` quand la fiche peut être enregistrée.
     *
     * Un nom et une énergie, rien de plus. Les cinq autres valeurs restent
     * facultatives, et un champ laissé vide veut dire « inconnu » : c'est la règle
     * du projet, et l'appliquer ici évite qu'un utilisateur pressé remplisse des
     * zéros pour pouvoir valider.
     */
    val complete: Boolean get() = name.isNotBlank() && per100g.kcal != null
}
