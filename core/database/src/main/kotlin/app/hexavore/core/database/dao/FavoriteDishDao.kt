package app.hexavore.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import app.hexavore.core.database.entity.FavoriteComponentEntity
import app.hexavore.core.database.entity.FavoriteDishEntity
import kotlinx.coroutines.flow.Flow

/** Un favori et ses composants, tels que Room sait les assembler en une lecture. */
data class FavoriteWithComponents(
    @Embedded val favorite: FavoriteDishEntity,
    @Relation(parentColumn = "id", entityColumn = "favorite_id")
    val components: List<FavoriteComponentEntity>,
)

/**
 * Accès aux plats favoris.
 *
 * Les composants remontent avec leur favori et sont **triés à la lecture** par le
 * mapper : `@Relation` ne garantit aucun ordre, et une liste de lignes qui se
 * réordonne d'un rejeu à l'autre déroute sans jamais produire d'erreur.
 *
 * @see docs/07-modele-de-donnees.md
 */
@Dao
interface FavoriteDishDao {
    /** Les plus utilisés d'abord, puis par nom : deux favoris jamais rejoués gardent un ordre stable. */
    @Transaction
    @Query("SELECT * FROM favorite_dish ORDER BY use_count DESC, name_search ASC")
    fun observeAll(): Flow<List<FavoriteWithComponents>>

    @Transaction
    @Query("SELECT * FROM favorite_dish WHERE id = :id")
    suspend fun byId(id: String): FavoriteWithComponents?

    /**
     * Un autre favori porte-t-il déjà ce nom ?
     *
     * La comparaison porte sur le nom **normalisé**, et `:excluding` laisse un favori
     * se renommer sans entrer en collision avec lui-même. `IS NOT` plutôt que `<>` :
     * en SQLite, `id <> NULL` ne vaut jamais vrai, donc une création — où l'exclusion
     * est nulle — n'aurait comparé aucune ligne et n'aurait jamais rien trouvé.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_dish WHERE name_search = :nameSearch AND id IS NOT :excluding)")
    suspend fun nameTaken(nameSearch: String, excluding: String?): Boolean

    /**
     * Écrit un favori et **remplace** entièrement ses composants, en une transaction.
     *
     * Les composants sont supprimés avant d'être réécrits plutôt que mis à jour :
     * renommer un favori en retirant une ligne laisserait sinon l'ancienne en place,
     * à une position que plus rien n'occupe.
     */
    @Transaction
    suspend fun save(favorite: FavoriteDishEntity, components: List<FavoriteComponentEntity>) {
        upsert(favorite)
        deleteComponentsOf(favorite.id)
        upsertComponents(components)
    }

    @Upsert
    suspend fun upsert(favorite: FavoriteDishEntity)

    @Upsert
    suspend fun upsertComponents(components: List<FavoriteComponentEntity>)

    @Query("DELETE FROM favorite_component WHERE favorite_id = :favoriteId")
    suspend fun deleteComponentsOf(favoriteId: String)

    @Query("DELETE FROM favorite_dish WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE favorite_dish SET use_count = use_count + 1 WHERE id = :id")
    suspend fun markUsed(id: String)

    /**
     * Détache les plats déjà enregistrés qui citaient ce favori.
     *
     * **Ici et non dans `DiaryDao`**, bien que la requête écrive dans `dish` : ce
     * qu'elle répond est une question sur le **favori** — que deviennent ceux qui le
     * citaient quand il change. Le seuil de fonctions a forcé le choix ; il tombait
     * juste.
     *
     * Un `UPDATE` et non une relecture suivie de réécritures : ce qui change est une
     * colonne, les lignes des plats ne sont pas touchées, et la base fait le travail
     * en une passe quel qu'en soit le nombre.
     */
    @Query("UPDATE dish SET favorite_id = NULL WHERE favorite_id = :favorite")
    suspend fun unlinkFromDishes(favorite: String)
}
