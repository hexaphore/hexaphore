package app.hexavore.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Une seule requête, et c'est ce qui justifie la classe.
 *
 * Elle interroge `food_entry`, donc le journal, pour répondre d'une fiche : elle
 * n'appartient franchement ni à [FoodDao] ni à [DiaryDao]. Elle a vécu dans le
 * premier, où elle laissait croire que le catalogue savait compter ses citations —
 * et c'est cette illusion qui rendait le faux du catalogue impossible à écrire
 * honnêtement ([D71][decisions]).
 *
 * Le seuil de fonctions de detekt a posé la question du placement — [DiaryDao] est à
 * dix — mais la réponse ne vient pas de lui : une classe pour une requête nomme la
 * couture au lieu de la cacher, comme `RoomBarcodeLookup` pour `source_ref`.
 *
 * [decisions]: docs/11-decisions.md
 */
@Dao
interface FoodCitationsDao {
    /** Combien d'entrées de journal citent cet aliment, pour prévenir avant de supprimer. */
    @Query("SELECT COUNT(*) FROM food_entry WHERE food_id = :id")
    suspend fun countFor(id: String): Int
}
