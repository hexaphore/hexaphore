package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.EntryDraft
import app.hexaphore.domain.diary.toEntries
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.time.Clock

/**
 * Enregistre un brouillon comme un **nouveau** plat.
 *
 * L'heure vient de l'horloge injectée et non du brouillon : c'est elle qui situe le
 * plat dans la journée, et un écran resté ouvert vingt minutes ne doit pas
 * l'enregistrer à l'heure où il a été ouvert.
 *
 * La journée, elle, vient du brouillon. Les deux ne se déduisent pas l'une de
 * l'autre : on note à 0 h 30 un dîner qui appartient à la veille.
 *
 * @see docs/06-architecture.md
 */
class LogDish(private val diary: DiaryRepository, private val clock: Clock, private val ids: IdGenerator) {
    /**
     * @throws IllegalArgumentException si le brouillon n'est pas complet. L'écran
     *   empêche le cas ; la vérification est là pour qu'un futur appelant ne
     *   puisse pas écrire une ligne à moitié saisie sans s'en apercevoir.
     */
    suspend operator fun invoke(draft: EntryDraft): DishId {
        require(draft.saveable) { "Brouillon incomplet : chaque ligne demande un nom, une quantite et une energie." }

        val id = DishId(ids.next())
        diary.save(
            Dish(
                id = id,
                date = draft.date,
                source = draft.source,
                loggedAt = clock.now(),
                entries = draft.toEntries(id, ids),
            ),
        )
        return id
    }
}
