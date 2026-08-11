package app.hexaphore.data.profile

import app.hexaphore.core.database.entity.GoalEntity
import app.hexaphore.core.database.entity.ProfileEntity
import app.hexaphore.core.database.entity.WeightEntryEntity
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.goal.GoalId
import app.hexaphore.domain.goal.GoalOrigin
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import app.hexaphore.domain.profile.UnitSystem
import app.hexaphore.domain.profile.UserProfile
import app.hexaphore.domain.profile.WeightEntry
import java.time.LocalDate

/**
 * La correspondance entre le schéma et le domaine.
 *
 * **Les énumérations sont lues par leur nom, avec un repli explicite.** Une base
 * écrite par une version plus récente peut nommer une valeur que celle-ci ne connaît
 * pas ; planter à la lecture rendrait le profil inaccessible et l'application avec.
 * Le repli est choisi pour être le moins engageant : le niveau d'activité le plus bas
 * sous-estime la dépense plutôt que de la surestimer, et un sexe non reconnu applique
 * la moyenne des deux formules — exactement ce que « je préfère ne pas répondre »
 * demande déjà.
 */
internal fun ProfileEntity.toDomain() = UserProfile(
    birthDate = LocalDate.parse(birthDate),
    sex = sex.toSex(),
    heightCm = heightCm,
    activityLevel = activityLevel.toActivityLevel(),
    unitSystem = UnitSystem.entries.firstOrNull { it.name == unitSystem } ?: UnitSystem.METRIC,
)

internal fun UserProfile.toEntity(now: Long) = ProfileEntity(
    birthDate = birthDate.toString(),
    sex = sex.name,
    heightCm = heightCm,
    activityLevel = activityLevel.name,
    unitSystem = unitSystem.name,
    createdAt = now,
    updatedAt = now,
)

private fun String.toSex(): Sex = Sex.entries.firstOrNull { it.name == this } ?: Sex.UNSPECIFIED

private fun String.toActivityLevel(): ActivityLevel =
    ActivityLevel.entries.firstOrNull { it.name == this } ?: ActivityLevel.SEDENTARY

internal fun WeightEntryEntity.toDomain() = WeightEntry(date = LocalDate.parse(date), weightKg = weightKg)

internal fun WeightEntry.toEntity(id: String, now: Long) =
    WeightEntryEntity(id = id, date = date.toString(), weightKg = weightKg, createdAt = now)

internal fun GoalEntity.toDomain() = Goal(
    id = GoalId(id),
    startedAt = LocalDate.parse(startedAt),
    endedAt = endedAt?.let(LocalDate::parse),
    origin = GoalOrigin.entries.firstOrNull { it.name == origin } ?: GoalOrigin.CALCULATED,
    strategy = GoalStrategy.entries.firstOrNull { it.name == strategy } ?: GoalStrategy.MAINTAIN,
    targetWeightKg = targetWeightKg,
    targetDate = targetDate?.let(LocalDate::parse),
    daily = DailyGoal(
        kcal = kcal,
        protein = proteinG,
        carbs = carbG,
        sugars = sugarG,
        fat = fatG,
        fiber = fiberG,
    ),
)

internal fun Goal.toEntity(now: Long) = GoalEntity(
    id = id.value,
    startedAt = startedAt.toString(),
    endedAt = endedAt?.toString(),
    // C'est cette colonne, et non `ended_at`, qui porte l'invariant : deux NULL ne se
    // heurtent jamais en SQLite, donc un index unique sur la date de fin ne
    // contraindrait rien.
    activeKey = if (endedAt == null) GoalEntity.ACTIVE else id.value,
    origin = origin.name,
    strategy = strategy.name,
    targetWeightKg = targetWeightKg,
    targetDate = targetDate?.toString(),
    kcal = daily.kcal,
    proteinG = daily.protein,
    carbG = daily.carbs,
    sugarG = daily.sugars,
    fatG = daily.fat,
    fiberG = daily.fiber,
    createdAt = now,
)
