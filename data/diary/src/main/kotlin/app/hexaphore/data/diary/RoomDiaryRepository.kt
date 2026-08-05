package app.hexaphore.data.diary

import app.hexaphore.core.database.dao.DiaryDao
import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.Dish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Le journal, lu depuis Room.
 *
 * Seconde implémentation du port `DiaryRepository`, après celle en mémoire. Le
 * passage de l'une à l'autre n'a demandé qu'une ligne dans le module Hilt de
 * `:app`, et aucun appelant ne s'en est aperçu — c'est cette propriété, et non le
 * nombre de modules, qui dit si le découpage tient.
 *
 * Aucune agrégation ici : le DAO rend les lignes entières et le domaine totalise.
 * Un `SUM` en SQL traiterait `NULL` comme absent, ce qui est arithmétiquement juste
 * mais perdrait l'information qu'une valeur manquait.
 *
 * @see docs/06-architecture.md
 * @see docs/11-decisions.md — D29
 */
class RoomDiaryRepository @Inject constructor(private val dao: DiaryDao) : DiaryRepository {
    override fun observeDay(date: LocalDate): Flow<List<Dish>> =
        dao.observeDay(date.toString()).map { dishes -> dishes.map { it.toDomain() } }
}
