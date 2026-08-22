package app.hexaphore.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.hexaphore.core.database.entity.GoalEntity
import app.hexaphore.core.database.entity.ProfileEntity
import app.hexaphore.core.database.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

/** Le profil unique et le journal de poids. */
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = :id")
    fun observeProfile(id: String = ProfileEntity.SINGLETON): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    /**
     * La dernière pesée du **jour le plus récent**.
     *
     * C'est elle qui sert au calcul de l'objectif : le poids actuel est la dernière
     * pesée connue, ou celle de l'onboarding.
     */
    @Query("SELECT * FROM weight_entry ORDER BY date DESC LIMIT 1")
    fun observeLatestWeight(): Flow<WeightEntryEntity?>

    /** Croissant : c'est l'ordre de la courbe, qui se lit de gauche a droite. */
    @Query("SELECT * FROM weight_entry ORDER BY date ASC")
    fun observeAllWeights(): Flow<List<WeightEntryEntity>>

    /** `REPLACE` par l'index unique sur `date` : la dernière pesée du jour remplace. */
    @Upsert
    suspend fun upsertWeight(entry: WeightEntryEntity)

    @Query("DELETE FROM weight_entry WHERE date = :date")
    suspend fun deleteWeightOn(date: String)
}

/**
 * Les objectifs, et le seul chemin d'écriture qui préserve l'invariant.
 *
 * @see docs/03-nutrition-calculs.md
 */
@Dao
interface GoalDao {
    /**
     * L'objectif qui couvre cette journée.
     *
     * Borne de début incluse, borne de fin **exclue** : le jour où un objectif est
     * remplacé appartient au nouveau. Sans cette convention, une journée relèverait de
     * deux objectifs et le résumé dépendrait de l'ordre de lecture.
     */
    @Query(
        """
        SELECT * FROM goal
        WHERE started_at <= :date AND (ended_at IS NULL OR ended_at > :date)
        ORDER BY started_at DESC LIMIT 1
        """,
    )
    fun observeGoalOn(date: String): Flow<GoalEntity?>

    /**
     * Toutes les versions, la plus recente d'abord.
     *
     * Pour qu'un calendrier de trente jours choisisse en memoire plutot que de poser
     * trente fois la meme question. La liste est courte : un objectif se revise, il
     * ne se cree pas tous les jours.
     */
    @Query("SELECT * FROM goal ORDER BY started_at DESC")
    fun observeAllGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal WHERE ended_at IS NULL LIMIT 1")
    fun observeCurrent(): Flow<GoalEntity?>

    /**
     * Clôt l'objectif courant et écrit le nouveau, **en une transaction**.
     *
     * Entre les deux écritures, il y aurait soit deux objectifs actifs — que l'index
     * unique refuserait — soit aucun, et une lecture concurrente rendrait `null` pour
     * une journée qui a bien un objectif. C'est aussi pour ça que la clôture précède
     * l'insertion et non l'inverse.
     */
    @Transaction
    suspend fun replace(goal: GoalEntity) {
        closeCurrent(goal.startedAt)
        insert(goal)
    }

    @Query("UPDATE goal SET ended_at = :date, active_key = id WHERE ended_at IS NULL")
    suspend fun closeCurrent(date: String)

    @Upsert
    suspend fun insert(goal: GoalEntity)
}
