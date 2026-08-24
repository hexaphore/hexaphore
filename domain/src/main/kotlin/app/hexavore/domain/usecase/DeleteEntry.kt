package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.EntryId

/**
 * Retire une ligne du journal.
 *
 * **Un plat vidé de sa dernière ligne est supprimé, pas laissé vide.** Un plat sans
 * contenu s'afficherait à l'accueil avec son heure et sa pastille de source, à zéro
 * calorie — indiscernable d'une saisie réelle qui n'apporterait rien. Or ce n'est
 * pas ce qui s'est passé : la saisie a été annulée, et un journal doit pouvoir le
 * dire en ne montrant rien.
 *
 * Le plat est passé en entier plutôt que désigné par l'identifiant de sa ligne :
 * l'appelant l'a déjà sous la main, et c'est ce qui évite au port une méthode
 * « trouve le plat qui contient cette ligne » dont personne d'autre n'aurait usage.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
class DeleteEntry(private val diary: DiaryRepository) {
    suspend operator fun invoke(dish: Dish, entryId: EntryId) {
        val remaining = dish.entries.filterNot { it.id == entryId }
        if (remaining.isEmpty()) diary.deleteDish(dish.id) else diary.deleteEntry(entryId)
    }
}
