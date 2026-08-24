package app.hexavore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une fiche d'aliment : ce qu'on peut réutiliser d'une saisie à l'autre.
 *
 * Catalogue unifié des trois provenances — un aliment CIQUAL **copié** la première
 * fois qu'il est réellement consommé, un produit Open Food Facts mis en cache, un
 * aliment créé à la main. Copier les 3 484 lignes de CIQUAL à l'installation
 * gonflerait la base, les sauvegardes et la recherche avec 99 % de contenu jamais
 * utilisé ([docs/07][modele]).
 *
 * **Cette table ne calcule rien.** Une entrée de journal fige ses macros à
 * l'enregistrement et ne les relit jamais d'ici ([D05][decisions]) : modifier une
 * fiche ne peut donc pas réécrire le passé, et c'est vérifié par un test. Le lien
 * `food_entry.food_id` sert à la provenance et au ré-ajout, pas au calcul.
 *
 * **`null` signifie inconnu, jamais zéro**, pour les huit teneurs. La distinction
 * descend jusqu'ici parce que c'est la couche où elle se perdrait sans bruit : un
 * `NOT NULL DEFAULT 0` sur `fiber_100` transformerait 70 aliments sans mesure en
 * 70 aliments sans fibres.
 *
 * [modele]: docs/07-modele-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
@Entity(
    tableName = "food",
    indices = [
        // Unique, et c'est SQLite qui le rend partiel : deux NULL n'y sont jamais
        // egaux, donc autant d'aliments personnels sans reference que voulu, mais un
        // seul par code CIQUAL et un seul par code-barres. Le double scan d'un meme
        // produit ne peut pas creer de doublon.
        Index(value = ["source", "source_ref"], unique = true),
        // « Recents » lit les vingt derniers usages, et rien d'autre ne trie sur
        // cette colonne.
        Index(value = ["last_used_at"]),
    ],
)
data class FoodEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    /** `CIQUAL`, `OFF` ou `CUSTOM`. */
    @ColumnInfo(name = "source")
    val source: String,
    /** Code CIQUAL ou code-barres. `null` pour un aliment personnel sans origine. */
    @ColumnInfo(name = "source_ref")
    val sourceRef: String?,
    @ColumnInfo(name = "name")
    val name: String,
    /**
     * Le nom sous sa forme cherchable — accents retirés, ligatures défaites,
     * minuscules.
     *
     * Stockée et non calculée à la lecture : la recherche compare une saisie
     * normalisée à cette colonne, et normaliser 600 noms à chaque frappe pour
     * respecter un budget de 150 ms serait le seul endroit du parcours à le
     * dépenser sans raison.
     */
    @ColumnInfo(name = "name_search")
    val nameSearch: String,
    @ColumnInfo(name = "brand")
    val brand: String?,
    @ColumnInfo(name = "kcal_100")
    val kcal100: Double?,
    @ColumnInfo(name = "protein_100")
    val protein100: Double?,
    @ColumnInfo(name = "carb_100")
    val carb100: Double?,
    @ColumnInfo(name = "sugar_100")
    val sugar100: Double?,
    @ColumnInfo(name = "fat_100")
    val fat100: Double?,
    @ColumnInfo(name = "fiber_100")
    val fiber100: Double?,
    /** Stockées, non affichées en v1 : la donnée est là, la prendre coûte zéro. */
    @ColumnInfo(name = "saturated_fat_100")
    val saturatedFat100: Double?,
    @ColumnInfo(name = "salt_100")
    val salt100: Double?,
    /** Quantité proposée à l'ouverture de la fiche. `null` vaut 100 g. */
    @ColumnInfo(name = "default_serving_g")
    val defaultServingG: Double?,
    /**
     * `null` veut dire **on ne sait pas**, et pas « solide ».
     *
     * `docs/07` l'annonçait non nullable. Trois états valent mieux que deux pour la
     * même raison que sur les huit teneurs : rien ne dit d'un aliment de la table de
     * l'ANSES s'il est liquide, et un `0` par défaut l'affirmerait.
     */
    @ColumnInfo(name = "is_liquid")
    val isLiquid: Boolean?,
    /**
     * Quand la fiche a été récupérée d'Open Food Facts. `null` pour les autres
     * provenances, qui ne se périment pas.
     *
     * Écrite avant d'avoir un lecteur, contrairement à la règle de [D34][decisions] :
     * un instant qu'on n'a pas noté ne se retrouve pas, et sans elle toutes les fiches
     * mises en cache avant le rafraîchissement de la tranche 6 seraient sans âge.
     *
     * [decisions]: docs/11-decisions.md
     */
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long?,
    /** `null` tant que l'aliment n'a jamais servi : « Récents » filtre là-dessus. */
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long?,
    @ColumnInfo(name = "use_count")
    val useCount: Int,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
