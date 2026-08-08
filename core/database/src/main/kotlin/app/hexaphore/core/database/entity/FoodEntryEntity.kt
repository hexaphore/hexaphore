package app.hexaphore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un aliment d'un plat, avec ses macros **figées**.
 *
 * Les six valeurs sont copiées à l'enregistrement plutôt que recalculées depuis la
 * fiche de l'aliment. Un journal alimentaire est un registre d'événements : sans
 * cela, un fabricant qui reformule son produit réécrirait un journal vieux de six
 * mois, et supprimer un aliment amputerait l'historique.
 *
 * **`null` signifie inconnu, jamais zéro** pour les cinq macros hors calories. La
 * distinction est portée jusqu'ici parce que c'est la seule couche où elle peut
 * être perdue sans que rien ne le signale : un `NOT NULL DEFAULT 0` sur ces
 * colonnes fausserait des mois de journal en silence.
 *
 * Aucune source ici : elle appartient au plat. Une ligne n'entre jamais dans le
 * journal toute seule.
 *
 * @see docs/07-modele-de-donnees.md
 * @see docs/11-decisions.md — D05, D32
 */
@Entity(
    tableName = "food_entry",
    foreignKeys = [
        ForeignKey(
            entity = DishEntity::class,
            parentColumns = ["id"],
            childColumns = ["dish_id"],
            // Supprimer un plat supprime ses lignes : une ligne orpheline n'a
            // aucune existence dans le domaine.
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            // SET NULL et surtout pas CASCADE : supprimer un aliment personnel ne
            // doit pas amputer l'historique. La ligne survit avec ses macros figees
            // et son nom d'affichage ; c'est le lien de provenance qui se defait.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["dish_id"]), Index(value = ["food_id"])],
)
data class FoodEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "dish_id")
    val dishId: String,
    /**
     * L'aliment d'où vient cette ligne, ou `null` si elle a été tapée à la main.
     *
     * **Provenance, pas source de calcul.** Il sert à ré-ajouter la même chose et à
     * compter les usages ; les six macros, elles, sont figées dans cette ligne et
     * ne se relisent jamais d'ici.
     */
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
    @ColumnInfo(name = "kcal")
    val kcal: Double,
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
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
