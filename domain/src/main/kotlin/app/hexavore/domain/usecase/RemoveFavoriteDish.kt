package app.hexavore.domain.usecase

import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.FavoriteDishes

/**
 * Retire un plat de la liste des favoris.
 *
 * **C'est le seul chemin**, et il passe par l'étoile de l'écran de validation
 * ([D62][decisions]). La liste des favoris ne sert qu'à en choisir un ; lui ajouter un
 * geste de suppression aurait fait deux endroits pour la même décision, et deux
 * endroits à tenir d'accord.
 *
 * Les plats du journal qui en venaient **restent**, et perdent seulement leur lien :
 * un journal est un registre d'événements, et le modèle qui a servi à composer un repas
 * n'a pas à emporter le repas en disparaissant. C'est la base qui le garantit, par un
 * `ON DELETE SET NULL`.
 *
 * [decisions]: docs/11-decisions.md
 */
class RemoveFavoriteDish(private val favorites: FavoriteDishes) {
    suspend operator fun invoke(id: FavoriteDishId) = favorites.delete(id)
}
