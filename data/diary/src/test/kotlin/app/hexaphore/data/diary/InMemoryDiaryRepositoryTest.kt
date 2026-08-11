package app.hexaphore.data.diary

import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.domain.diary.DiaryRepository

/**
 * Le même contrat, joué sur le faux.
 *
 * Il vit dans ce module plutôt que dans `:core:testing` pour que les deux
 * implémentations soient **compilées et exécutées côte à côte**, sous la même
 * commande et dans le même rapport.
 */
class InMemoryDiaryRepositoryTest : DiaryContract() {
    override fun journal(): DiaryRepository = InMemoryDiaryRepository()
}
