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
 * **Ce que l'utilisateur a marqué vit dans [FoodMarksDao]** — récents, favoris, et
 * l'écriture qui les alimente. Ce DAO-ci décrit le catalogue ; celui-là porte ce
 * qu'on y a inscrit à l'usage.
 *
 * @see docs/04-sources-de-donnees.md
 */
@Dao
interface FoodDao {
    /**
     * Un flux, et c'est ce qui fait réagir la liste de résultats.
     *
     * Room ré-exécute cette requête à chaque écriture sur `food` : épingler,
     * supprimer, verser une fiche au catalogue. Une `suspend fun` rendait un
     * instantané, et l'écran restait sur des résultats démentis par le catalogue
     * jusqu'à ce qu'on relance la recherche ([D53][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    /**
     * **Sans `LIMIT`, et c'est délibéré.** Le `LIKE` balaie déjà la table entière ;
     * tronquer par `use_count` avant le classement écarterait un nom qui correspond
     * mieux au profit d'un aliment simplement plus fréquent, et le filtre par rayon
     * s'applique lui aussi après. Le catalogue local est borné par construction — une
     * ligne par aliment réellement ouvert ([D51][decisions]) — là où la table de
     * l'ANSES, elle, garde sa limite en SQL.
     *
     * Une requête vide rend tout le catalogue : c'est le mode parcours, où seule une
     * pastille filtre.
     */
    @Query("SELECT * FROM food WHERE name_search LIKE '%' || :normalisedQuery || '%'")
    fun observeSearch(normalisedQuery: String): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun byId(id: String): FoodEntity?

    /** L'unicité de `(source, source_ref)` rend ce couple équivalent à une clé. */
    @Query("SELECT * FROM food WHERE source = :source AND source_ref = :reference")
    suspend fun byReference(source: String, reference: String): FoodEntity?

    /**
     * La fiche que ce code-barres désigne, s'il y en a une.
     *
     * **`CIQUAL` est exclue et ce n'est pas une précaution.** `source_ref` y porte un
     * code de la table de l'ANSES, qui compte cinq chiffres là où un code-barres en
     * compte huit ou treize : la collision est improbable aujourd'hui et resterait un
     * pari, alors que la clause l'écarte pour de bon. Ce sont deux espaces de noms
     * différents rangés dans une même colonne.
     *
     * **Un aliment personnel passe devant un produit en cache.** Les deux peuvent
     * porter le même code — on crée la fiche hors ligne, on rescanne connecté — et
     * l'index unique porte sur le couple, donc les laisse coexister. Ce que
     * l'utilisateur a saisi lui-même gagne, comme dans les résultats de recherche.
     */
    @Query(
        """
        SELECT * FROM food
        WHERE source_ref = :barcode AND source <> 'CIQUAL'
        ORDER BY CASE source WHEN 'CUSTOM' THEN 0 ELSE 1 END
        LIMIT 1
        """,
    )
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Upsert
    suspend fun upsert(food: FoodEntity)

    @Query("DELETE FROM food WHERE id = :id")
    suspend fun delete(id: String)
}
