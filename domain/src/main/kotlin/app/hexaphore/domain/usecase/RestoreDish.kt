package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.Dish

/**
 * Remet un plat tel qu'il était.
 *
 * C'est l'annulation d'une suppression. Le choix qu'elle traduit : supprimer
 * vraiment, puis savoir revenir en arrière — plutôt que retarder la suppression
 * pendant les cinq secondes du `Snackbar`. Une suppression différée disparaît si
 * le processus est tué entre-temps, et l'utilisateur retrouve alors une ligne qu'il
 * croyait supprimée. Ici, le pire cas est l'inverse : la ligne est bien partie, et
 * c'est ce que l'écran montrait déjà.
 *
 * Le plat est restauré en entier parce que la suppression a pu emporter le plat
 * lui-même, quand la ligne était la dernière.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
class RestoreDish(private val diary: DiaryRepository) {
    suspend operator fun invoke(dish: Dish) {
        diary.save(dish)
    }
}
