package app.hexaphore.di

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.FavoriteDishes
import app.hexaphore.domain.food.FoodStore
import app.hexaphore.domain.food.FoodUsage
import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.CreateDraft
import app.hexaphore.domain.usecase.DeleteDish
import app.hexaphore.domain.usecase.DeleteEntry
import app.hexaphore.domain.usecase.GetDaySummary
import app.hexaphore.domain.usecase.GetDishDraft
import app.hexaphore.domain.usecase.LogDish
import app.hexaphore.domain.usecase.RestoreDish
import app.hexaphore.domain.usecase.SaveCustomFood
import app.hexaphore.domain.usecase.SaveDraft
import app.hexaphore.domain.usecase.UpdateDish
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage du **journal**.
 *
 * Ils vivent dans `:domain`, qui est du Kotlin pur et ne connaît donc pas Hilt —
 * c'est exactement la propriété qu'on veut. `:app` assemble : il sait quels ports
 * sont liés à quoi, et il monte les cas d'usage par-dessus.
 *
 * Un cas d'usage n'est pas un singleton : il ne porte aucun état, et l'instancier
 * coûte moins que de le retenir.
 *
 * **Séparé de [GoalUseCaseModule]** parce qu'un seul module atteignait le seuil de
 * fonctions de detekt. La réponse du projet est de découper selon ce que les choses
 * sont, pas de relever le seuil ([docs/10][qualite]) — et il se trouve que la coupure
 * naturelle existait déjà : ces cas d'usage parlent de plats, les autres d'objectifs.
 *
 * [qualite]: docs/10-qualite-et-livraison.md
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun getDaySummary(diary: DiaryRepository, goals: Goals, clock: Clock): GetDaySummary =
        GetDaySummary(diary, goals, clock)

    @Provides
    fun getDishDraft(diary: DiaryRepository, ids: IdGenerator): GetDishDraft = GetDishDraft(diary, ids)

    @Provides
    fun createDraft(clock: Clock, ids: IdGenerator): CreateDraft = CreateDraft(clock, ids)

    @Provides
    fun saveCustomFood(store: FoodStore, ids: IdGenerator): SaveCustomFood = SaveCustomFood(store, ids)

    @Provides
    fun logDish(
        diary: DiaryRepository,
        foods: FoodUsage,
        favorites: FavoriteDishes,
        clock: Clock,
        ids: IdGenerator,
    ): LogDish = LogDish(diary, foods, favorites, clock, ids)

    @Provides
    fun updateDish(diary: DiaryRepository, ids: IdGenerator): UpdateDish = UpdateDish(diary, ids)

    @Provides
    fun saveDraft(log: LogDish, update: UpdateDish): SaveDraft = SaveDraft(log, update)

    @Provides
    fun deleteEntry(diary: DiaryRepository): DeleteEntry = DeleteEntry(diary)

    @Provides
    fun deleteDish(diary: DiaryRepository): DeleteDish = DeleteDish(diary)

    @Provides
    fun restoreDish(diary: DiaryRepository): RestoreDish = RestoreDish(diary)
}
