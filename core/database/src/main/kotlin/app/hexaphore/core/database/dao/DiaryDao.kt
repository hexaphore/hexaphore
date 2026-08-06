package app.hexaphore.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import app.hexaphore.core.database.entity.DishEntity
import app.hexaphore.core.database.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

/** Un plat et ses lignes, tels que Room sait les assembler en une lecture. */
data class DishWithEntries(
    @Embedded val dish: DishEntity,
    @Relation(parentColumn = "id", entityColumn = "dish_id")
    val entries: List<FoodEntryEntity>,
)

/**
 * Accès au journal alimentaire.
 *
 * Les lectures rendent un [Flow] : Room notifie sur invalidation, donc aucun écran
 * n'a à se rafraîchir lui-même. Les écritures sont `suspend` — un port d'écriture
 * qui rendrait un flux mélangerait commande et observation.
 *
 * **Aucun `SUM` ici.** Agréger en SQL traiterait `NULL` comme absent, ce qui est
 * correct arithmétiquement mais perd l'information qu'une valeur manquait. Les
 * lignes remontent donc entières, et c'est le domaine qui totalise en retenant
 * quels totaux sont minorés.
 *
 * @see docs/11-decisions.md — D29
 */
@Dao
interface DiaryDao {
    @Transaction
    @Query("SELECT * FROM dish WHERE date = :date ORDER BY logged_at ASC")
    fun observeDay(date: String): Flow<List<DishWithEntries>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDish(dish: DishEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<FoodEntryEntity>)

    /**
     * Enregistre un plat et ses lignes en une seule transaction.
     *
     * Sans transaction, une coupure entre les deux insertions laisserait un plat
     * sans contenu — visible à l'accueil, à zéro calorie, sans qu'on puisse le
     * distinguer d'une saisie réelle.
     */
    @Transaction
    suspend fun insertDishWithEntries(dish: DishEntity, entries: List<FoodEntryEntity>) {
        insertDish(dish)
        insertEntries(entries)
    }

    @Query("SELECT COUNT(*) FROM dish")
    suspend fun countDishes(): Int
}
