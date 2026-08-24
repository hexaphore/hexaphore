package app.hexavore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un plat enregistré pour être rejoué.
 *
 * **`name_search` porte l'unicité, pas `name`.** Deux favoris nommés « Petit-déj » et
 * « petit dej » ne se distinguent pas dans une liste, et choisir devient un pari :
 * l'index unique porte donc sur le nom **normalisé** — sans accents, sans casse — par
 * la même fonction que la recherche d'aliments ([D49][decisions]). Le nom affiché, lui,
 * reste tel qu'il a été tapé.
 *
 * L'unicité est doublée : le cas d'usage la vérifie pour pouvoir répondre une phrase,
 * l'index la garantit. Une règle tenue par la seule discipline d'écriture n'en est pas
 * une — c'est le raisonnement de `goal.active_key` ([D55][decisions]), appliqué ici.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/07-modele-de-donnees.md
 */
@Entity(
    tableName = "favorite_dish",
    indices = [
        Index(value = ["name_search"], unique = true),
        Index(value = ["use_count"]),
    ],
)
data class FavoriteDishEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    /** Tel qu'il a été tapé. C'est lui qu'on lit dans la liste. */
    @ColumnInfo(name = "name")
    val name: String,
    /** Normalisé. C'est lui qui décide de l'unicité et que la recherche compare. */
    @ColumnInfo(name = "name_search")
    val nameSearch: String,
    @ColumnInfo(name = "use_count")
    val useCount: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/**
 * Une ligne de plat favori.
 *
 * **Pas d'identifiant propre** : la clé est `(favorite_id, position)`. Un composant
 * n'existe pas hors de son favori et n'est jamais désigné seul ; lui inventer un
 * identifiant aurait produit une colonne que personne ne lit, et la position devait de
 * toute façon être stockée pour que l'ordre des lignes survive.
 *
 * `food_id` en `SET NULL` : supprimer un aliment personnel **délie** les favoris qui le
 * citaient au lieu de les amputer. Les six valeurs enregistrées ici prennent alors le
 * relais — c'est la raison pour laquelle elles sont écrites même quand une fiche est
 * citée ([D62][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Entity(
    tableName = "favorite_component",
    primaryKeys = ["favorite_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteDishEntity::class,
            parentColumns = ["id"],
            childColumns = ["favorite_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["favorite_id"]), Index(value = ["food_id"])],
)
data class FavoriteComponentEntity(
    @ColumnInfo(name = "favorite_id")
    val favoriteId: String,
    /** L'ordre des lignes dans le plat. Une liste qui se réordonne à chaque rejeu déroute. */
    @ColumnInfo(name = "position")
    val position: Int,
    /** La fiche citée, ou `NULL` pour une ligne tapée à la main — ou dont la fiche a disparu. */
    @ColumnInfo(name = "food_id")
    val foodId: String?,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    @ColumnInfo(name = "unit")
    val unit: String,
    @ColumnInfo(name = "grams")
    val grams: Double,
    /** Les six valeurs au jour de l'enregistrement. `NULL` reste inconnu, jamais zéro. */
    @ColumnInfo(name = "kcal")
    val kcal: Double?,
    @ColumnInfo(name = "protein_g")
    val proteinG: Double?,
    @ColumnInfo(name = "carb_g")
    val carbG: Double?,
    @ColumnInfo(name = "sugar_g")
    val sugarG: Double?,
    @ColumnInfo(name = "fat_g")
    val fatG: Double?,
    @ColumnInfo(name = "fiber_g")
    val fiberG: Double?,
)
