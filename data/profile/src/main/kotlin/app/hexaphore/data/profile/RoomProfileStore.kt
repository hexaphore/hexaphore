package app.hexaphore.data.profile

import app.hexaphore.core.database.dao.GoalDao
import app.hexaphore.core.database.dao.ProfileDao
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.profile.Profiles
import app.hexaphore.domain.profile.UserProfile
import app.hexaphore.domain.profile.WeightEntry
import app.hexaphore.domain.profile.WeightLog
import app.hexaphore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le profil, le journal de poids et les objectifs, adossés à Room.
 *
 * Trois ports pour une classe, comme `RoomFoodCatalog` en tient six : la séparation
 * existe pour que **les appelants** ne dépendent que de ce qu'ils utilisent
 * ([docs/06][archi]), pas pour forcer trois objets. L'onboarding écrit les trois d'un
 * bloc, l'accueil ne lit que [Goals].
 *
 * [archi]: docs/06-architecture.md
 */
@Singleton
class RoomProfileStore @Inject constructor(
    private val profiles: ProfileDao,
    private val goalDao: GoalDao,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : Profiles,
    WeightLog,
    Goals {
    override fun observeProfile(): Flow<UserProfile?> =
        profiles.observeProfile().map { it?.toDomain() }.flowOn(dispatchers.io)

    override suspend fun save(profile: UserProfile) = withContext(dispatchers.io) {
        profiles.upsert(profile.toEntity(clock.now().toEpochMilli()))
    }

    override fun observeRecent(limit: Int): Flow<List<WeightEntry>> =
        profiles.observeRecentWeights(limit).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeLatest(): Flow<WeightEntry?> =
        profiles.observeLatestWeight().map { it?.toDomain() }.flowOn(dispatchers.io)

    /**
     * Une pesée par jour, la dernière remplace.
     *
     * La ligne du jour est retirée avant d'écrire la neuve plutôt que mise à jour :
     * l'index unique porte sur `date`, et un `upsert` par clé primaire ne le verrait
     * pas — il échouerait sur la contrainte au lieu de remplacer.
     */
    override suspend fun record(entry: WeightEntry) = withContext(dispatchers.io) {
        profiles.deleteWeightOn(entry.date.toString())
        profiles.upsertWeight(entry.toEntity(ids.next(), clock.now().toEpochMilli()))
    }

    override fun observeGoalOn(date: LocalDate): Flow<Goal?> =
        goalDao.observeGoalOn(date.toString()).map { it?.toDomain() }.flowOn(dispatchers.io)

    override fun observeCurrent(): Flow<Goal?> = goalDao.observeCurrent().map { it?.toDomain() }.flowOn(dispatchers.io)

    override fun observeAll(): Flow<List<Goal>> =
        goalDao.observeAllGoals().map { goals -> goals.map { it.toDomain() } }.flowOn(dispatchers.io)

    override suspend fun replace(goal: Goal) = withContext(dispatchers.io) {
        goalDao.replace(goal.toEntity(clock.now().toEpochMilli()))
    }
}
