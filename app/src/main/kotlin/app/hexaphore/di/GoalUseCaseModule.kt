package app.hexaphore.di

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.goal.AdjustmentSettings
import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.profile.Profiles
import app.hexaphore.domain.profile.WeightLog
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.CalculateDailyGoal
import app.hexaphore.domain.usecase.GetWeightTrend
import app.hexaphore.domain.usecase.RecordWeight
import app.hexaphore.domain.usecase.RespondToAdjustment
import app.hexaphore.domain.usecase.ReviseGoal
import app.hexaphore.domain.usecase.SuggestGoalAdjustment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage de l'**objectif**.
 *
 * Même raisonnement que [DomainModule], dont il a été séparé : les cas d'usage du
 * journal et ceux de l'objectif ne se lisent pas ensemble et ne changent pas pour les
 * mêmes raisons. La tranche 7 y a ajouté le journal de poids, qui appartient au
 * même sujet : la courbe se lit contre la trajectoire annoncée par l'objectif.
 */
@Module
@InstallIn(SingletonComponent::class)
object GoalUseCaseModule {
    @Provides
    fun calculateDailyGoal(clock: Clock): CalculateDailyGoal = CalculateDailyGoal(clock)

    @Provides
    fun getWeightTrend(weights: WeightLog, goals: Goals): GetWeightTrend = GetWeightTrend(weights, goals)

    @Provides
    fun recordWeight(weights: WeightLog, clock: Clock): RecordWeight = RecordWeight(weights, clock)

    /**
     * L'adaptation hebdomadaire.
     *
     * Cinq sources, et chacune répond à une des conditions : les pesées donnent la
     * pente, le journal l'adhérence, l'objectif le cap annoncé, le profil la dépense
     * dont les garde-fous ont besoin, et les réglages le silence après une réponse.
     */
    @Suppress("LongParameterList")
    @Provides
    fun suggestGoalAdjustment(
        weights: WeightLog,
        diary: DiaryRepository,
        goals: Goals,
        profiles: Profiles,
        settings: AdjustmentSettings,
        clock: Clock,
    ): SuggestGoalAdjustment = SuggestGoalAdjustment(weights, diary, goals, profiles, settings, clock)

    @Provides
    fun respondToAdjustment(
        goals: Goals,
        settings: AdjustmentSettings,
        ids: IdGenerator,
        clock: Clock,
    ): RespondToAdjustment = RespondToAdjustment(goals, settings, ids, clock)

    @Provides
    fun reviseGoal(profiles: Profiles, weights: WeightLog, goals: Goals, clock: Clock, ids: IdGenerator): ReviseGoal =
        ReviseGoal(profiles, weights, goals, clock, ids)
}
