package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.DishId

/**
 * Retire un plat entier du journal, ses lignes avec lui.
 *
 * Distinct de `DeleteEntry`, qui retire **une** ligne et ne supprime le plat que
 * lorsqu'il n'en reste plus. Ici l'intention est directe : ce plat n'a pas eu lieu.
 *
 * Un cas d'usage pour un seul appel de port, et c'est délibéré : un `:feature` ne voit
 * que des cas d'usage, jamais un dépôt concret ([docs/06][archi]). Laisser l'accueil
 * appeler `DiaryRepository` pour cette action-là et un cas d'usage pour les trois
 * autres aurait ouvert la porte par laquelle la règle se perd.
 *
 * [archi]: docs/06-architecture.md
 */
class DeleteDish(private val diary: DiaryRepository) {
    suspend operator fun invoke(id: DishId) = diary.deleteDish(id)
}
