package app.hexaphore.di

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.food.FoodStore
import app.hexaphore.domain.food.FoodUsage
import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.profile.Profiles
import app.hexaphore.domain.profile.WeightLog
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.CalculateDailyGoal
import app.hexaphore.domain.usecase.CreateDraft
import app.hexaphore.domain.usecase.DeleteEntry
import app.hexaphore.domain.usecase.GetDaySummary
import app.hexaphore.domain.usecase.GetDishDraft
import app.hexaphore.domain.usecase.LogDish
import app.hexaphore.domain.usecase.RestoreDish
import app.hexaphore.domain.usecase.ReviseGoal
import app.hexaphore.domain.usecase.SaveCustomFood
import app.hexaphore.domain.usecase.UpdateDish
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage.
 *
 * Ils vivent dans `:domain`, qui est du Kotlin pur et ne connaît donc pas Hilt —
 * c'est exactement la propriété qu'on veut. `:app` assemble : il sait quels ports
 * sont liés à quoi, et il monte les cas d'usage par-dessus.
 *
 * Un cas d'usage n'est pas un singleton : il ne porte aucun état, et l'instancier
 * coûte moins que de le retenir.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun getDaySummary(diary: DiaryRepository, goals: Goals, clock: Clock): GetDaySummary =
        GetDaySummary(diary, goals, clock)

    @Provides
    fun calculateDailyGoal(clock: Clock): CalculateDailyGoal = CalculateDailyGoal(clock)

    @Provides
    fun reviseGoal(profiles: Profiles, weights: WeightLog, goals: Goals, clock: Clock, ids: IdGenerator): ReviseGoal =
        ReviseGoal(profiles, weights, goals, clock, ids)

    @Provides
    fun getDishDraft(diary: DiaryRepository, ids: IdGenerator): GetDishDraft = GetDishDraft(diary, ids)

    @Provides
    fun createDraft(clock: Clock, ids: IdGenerator): CreateDraft = CreateDraft(clock, ids)

    @Provides
    fun saveCustomFood(store: FoodStore, ids: IdGenerator): SaveCustomFood = SaveCustomFood(store, ids)

    @Provides
    fun logDish(diary: DiaryRepository, foods: FoodUsage, clock: Clock, ids: IdGenerator): LogDish =
        LogDish(diary, foods, clock, ids)

    @Provides
    fun updateDish(diary: DiaryRepository, ids: IdGenerator): UpdateDish = UpdateDish(diary, ids)

    @Provides
    fun deleteEntry(diary: DiaryRepository): DeleteEntry = DeleteEntry(diary)

    @Provides
    fun restoreDish(diary: DiaryRepository): RestoreDish = RestoreDish(diary)
}
