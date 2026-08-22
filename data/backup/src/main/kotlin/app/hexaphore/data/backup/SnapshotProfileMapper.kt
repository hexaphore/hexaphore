package app.hexaphore.data.backup

import app.hexaphore.domain.goal.AdjustmentSetup
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.goal.GoalId
import app.hexaphore.domain.goal.GoalOrigin
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import app.hexaphore.domain.profile.UnitSystem
import app.hexaphore.domain.profile.UserProfile
import java.time.LocalDate

/**
 * Le profil, les objectifs, et l'état de l'adaptation.
 *
 * Séparé des deux autres mappeurs parce que ce sont trois sujets : ce qui décrit la
 * personne, ce qu'elle a mangé, et ce que son catalogue contient. Ensemble, ils
 * faisaient un fichier de dix-neuf fonctions qu'on ne relit pas.
 */
internal fun UserProfile.toDto() = ProfileDto(
    birthDate = birthDate.toString(),
    sex = sex.name,
    heightCm = heightCm,
    activityLevel = activityLevel.name,
    unitSystem = unitSystem.name,
)

internal fun ProfileDto.toDomain() = UserProfile(
    birthDate = LocalDate.parse(birthDate),
    sex = Sex.entries.firstOrNull { it.name == sex } ?: Sex.UNSPECIFIED,
    heightCm = heightCm,
    activityLevel = ActivityLevel.entries.firstOrNull { it.name == activityLevel } ?: ActivityLevel.MODERATE,
    unitSystem = UnitSystem.entries.firstOrNull { it.name == unitSystem } ?: UnitSystem.METRIC,
)

internal fun Goal.toDto() = GoalDto(
    id = id.value,
    startedAt = startedAt.toString(),
    endedAt = endedAt?.toString(),
    origin = origin.name,
    strategy = strategy.name,
    targetWeightKg = targetWeightKg,
    targetDate = targetDate?.toString(),
    kcal = daily.kcal,
    protein = daily.protein,
    carbs = daily.carbs,
    sugars = daily.sugars,
    fat = daily.fat,
    fiber = daily.fiber,
)

internal fun GoalDto.toDomain() = Goal(
    id = GoalId(id),
    startedAt = LocalDate.parse(startedAt),
    endedAt = endedAt?.let(LocalDate::parse),
    origin = GoalOrigin.entries.firstOrNull { it.name == origin } ?: GoalOrigin.CALCULATED,
    strategy = GoalStrategy.entries.firstOrNull { it.name == strategy } ?: GoalStrategy.MAINTAIN,
    targetWeightKg = targetWeightKg,
    targetDate = targetDate?.let(LocalDate::parse),
    daily = DailyGoal(kcal = kcal, protein = protein, carbs = carbs, sugars = sugars, fat = fat, fiber = fiber),
)

internal fun AdjustmentSetup.toDto() = AdjustmentDto(
    enabled = enabled,
    lastAcceptedOn = lastAcceptedOn?.toString(),
    lastIgnoredOn = lastIgnoredOn?.toString(),
)

internal fun AdjustmentDto.toDomain() = AdjustmentSetup(
    enabled = enabled,
    lastAcceptedOn = lastAcceptedOn?.let(LocalDate::parse),
    lastIgnoredOn = lastIgnoredOn?.let(LocalDate::parse),
)
