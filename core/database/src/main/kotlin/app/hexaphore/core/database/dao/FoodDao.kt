package app.hexaphore.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.hexaphore.core.database.entity.FoodEntity
import kotlinx.coroutines.flow.Flow

/**
 * Accès au catalogue d'aliments.
 *
 * **La recherche se fait en `LIKE`, sans index plein texte.** Le catalogue local
 * compte quelques centaines de fiches — celles qui ont réellement servi — contre
 * 3 484 pour CIQUAL, qui a le sien. Une seconde table FTS demanderait ses triggers
 * de synchronisation et un second chemin de normalisation à tenir aligné, pour
 * balayer six cents chaînes courtes. Le `LIKE` donne en prime la correspondance
 * en milieu de mot, ce qu'un index de mots ne fait pas, et c'est ce qu'on attend
 * d'une liste personnelle.
 *
 * `name_search` est comparée telle quelle : l'appelant lui présente une saisie déjà
 * normalisée par la même fonction qui a rempli la colonne.
 *
 * @see docs/04-sources-de-donnees.md
 */
@Dao
interface FoodDao {
    @Query(
        "SELECT * FROM food WHERE name_search LIKE '%' || :normalisedQuery || '%' ORDER BY use_count DESC LIMIT :limit",
    )
    suspend fun search(normalisedQuery: String, limit: Int): List<FoodEntity>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun byId(id: String): FoodEntity?

    /** L'unicité de `(source, source_ref)` rend ce couple équivalent à une clé. */
    @Query("SELECT * FROM food WHERE source = :source AND source_ref = :reference")
    suspend fun byReference(source: String, reference: String): FoodEntity?

    /**
     * Les aliments déjà utilisés, du plus récent au plus ancien.
     *
     * `last_used_at IS NOT NULL` n'est pas une précaution : une fiche créée et
     * jamais servie n'a rien à faire dans « Récents », et l'y faire figurer à la
     * date de création mélangerait deux informations différentes.
     */
    @Query("SELECT * FROM food WHERE last_used_at IS NOT NULL ORDER BY last_used_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE is_favorite = 1 ORDER BY name")
    fun observeFavorites(): Flow<List<FoodEntity>>

    @Upsert
    suspend fun upsert(food: FoodEntity)

    /**
     * Marque un usage.
     *
     * En SQL et non en lecture-modification-écriture : deux saisies rapprochées
     * perdraient un incrément, et `use_count` sert au classement de la recherche.
     */
    @Query("UPDATE food SET last_used_at = :usedAt, use_count = use_count + 1, updated_at = :usedAt WHERE id = :id")
    suspend fun markUsed(id: String, usedAt: Long)

    @Query("UPDATE food SET is_favorite = :favorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("DELETE FROM food WHERE id = :id")
    suspend fun delete(id: String)

    /** Combien d'entrées de journal citent cet aliment, pour prévenir avant de supprimer. */
    @Query("SELECT COUNT(*) FROM food_entry WHERE food_id = :id")
    suspend fun usageCount(id: String): Int
}
