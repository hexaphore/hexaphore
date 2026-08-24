package app.hexavore.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import app.hexavore.core.database.entity.FoodEntity
import kotlinx.coroutines.flow.Flow

/**
 * Ce que l'utilisateur a **marqué** sur une fiche : l'avoir mangée, l'avoir épinglée.
 *
 * Séparé de `FoodDao`, qui atteignait le seuil de fonctions de detekt. La coupure
 * n'est pas mécanique : ces quatre requêtes sont exactement les trois ports que le
 * domaine sépare déjà — `RecentFoods`, `FavoriteFoods`, et le côté écriture de
 * `FoodUsage`. `FoodDao` garde ce qui décrit le catalogue ; celui-ci porte ce qu'on
 * y a inscrit à l'usage.
 *
 * Les deux DAO adressent la même table, et ce n'est pas un problème : un DAO est une
 * façon de poser des questions, pas un propriétaire de table.
 *
 * @see docs/10-qualite-et-livraison.md
 */
@Dao
interface FoodMarksDao {
    /**
     * Les aliments déjà utilisés, du plus récent au plus ancien.
     *
     * `last_used_at IS NOT NULL` n'est pas une précaution : une fiche créée et
     * jamais servie n'a rien à faire dans « Récents », et l'y faire figurer à la
     * date de création mélangerait deux informations différentes. C'est aussi ce qui
     * garde un produit **scanné puis reposé sur l'étagère** hors de cette liste.
     */
    @Query("SELECT * FROM food WHERE last_used_at IS NOT NULL ORDER BY last_used_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE is_favorite = 1 ORDER BY name")
    fun observeFavorites(): Flow<List<FoodEntity>>

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
}
