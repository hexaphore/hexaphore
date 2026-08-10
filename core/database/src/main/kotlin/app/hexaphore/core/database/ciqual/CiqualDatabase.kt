package app.hexaphore.core.database.ciqual

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Une ligne de `ciqual_food`, telle qu'elle est stockée. `null` signifie inconnu. */
data class CiqualFoodRow(
    val code: String,
    val name: String,
    val groupName: String?,
    /** Le rayon du bandeau, sous le nom de l'énumération du domaine. `null` s'il n'en a pas. */
    val category: String?,
    val kcal100: Double?,
    val protein100: Double?,
    val carb100: Double?,
    val sugar100: Double?,
    val fat100: Double?,
    val fiber100: Double?,
    val saturatedFat100: Double?,
    val salt100: Double?,
)

/** Une portion usuelle de `ciqual_serving`. */
data class CiqualServingRow(val label: String, val grams: Double, val isDefault: Boolean)

/**
 * La table de l'ANSES, embarquée en lecture seule.
 *
 * **Pas de Room ici.** Cette base n'est pas gérée par l'application : elle est
 * produite par `:tooling:ciqual-import`, livrée telle quelle et jamais migrée —
 * remplacée en bloc à chaque publication ([docs/07][modele]). Room validerait son
 * schéma contre des entités, ce qui obligerait à décrire en annotations une table
 * virtuelle sans contenu qu'il ne sait pas exprimer, pour ne rien gagner : il n'y a
 * ni écriture, ni migration, ni invalidation à observer.
 *
 * **La copie a lieu une fois.** SQLite a besoin d'un fichier, et un asset d'APK est
 * compressé. Le fichier copié porte l'édition dans son nom : une nouvelle table de
 * l'ANSES produit donc un nouveau nom, la copie se refait toute seule, et les
 * éditions précédentes sont retirées. Comparer des dates de modification aurait
 * échoué au premier appareil dont l'horloge recule.
 *
 * [modele]: docs/07-modele-de-donnees.md
 */
class CiqualDatabase(private val context: Context) {
    private val database: SQLiteDatabase by lazy { open() }

    /**
     * Les aliments dont le nom contient tous les mots de [normalisedQuery], filtrés
     * aux rayons de [categories].
     *
     * La saisie est déjà normalisée par l'appelant, avec la même fonction qui a
     * rempli l'index. C'est la seule règle qui fasse fonctionner cette recherche, et
     * elle ne peut pas être vérifiée d'ici.
     *
     * **Une requête vide et un rayon, c'est le mode parcours** : on liste alors les
     * aliments du rayon sans passer par l'index plein texte. Vide des deux côtés, en
     * revanche, ne rend rien — 3 484 lignes sans critère ne sont pas une réponse.
     *
     * Le filtre est appliqué en SQL et non après coup : sur 3 484 lignes, filtrer en
     * Kotlin obligerait à toutes les lire pour en rendre trente. La règle, elle,
     * reste celle du domaine — c'est `FoodFilter` qui la porte, et le contrat de
     * `FoodSearch` vérifie que les deux disent la même chose.
     */
    fun search(normalisedQuery: String, categories: Set<String>, limit: Int): List<CiqualFoodRow> {
        val browsing = normalisedQuery.isBlank()
        if (browsing && categories.isEmpty()) return emptyList()

        val filter = categories.placeholders()
        val sql = if (browsing) browseSql(filter) else searchSql(filter)
        val arguments = buildList {
            // Chaque mot est un prefixe : « creme bru » doit trouver la creme brulee
            // avant que le second mot soit fini, sinon la recherche ne rend rien
            // pendant qu'on ecrit.
            if (!browsing) add(normalisedQuery.split(' ').filter { it.isNotBlank() }.joinToString(" ") { "$it*" })
            addAll(categories)
            add(limit.toString())
        }

        return database.rawQuery(sql, arguments.toTypedArray()).use { cursor -> cursor.map { it.toFoodRow() } }
    }

    /** Une fiche par son code CIQUAL. */
    fun byCode(code: String): CiqualFoodRow? =
        database.rawQuery("$SELECT_COLUMNS FROM ciqual_food WHERE code = ?", arrayOf(code)).use { cursor ->
            cursor.map { it.toFoodRow() }.firstOrNull()
        }

    /**
     * Les rayons de plusieurs fiches, en une requête.
     *
     * Une fiche copiée dans le catalogue **ne stocke pas** son rayon : il se relit
     * ici, par son code, comme les portions ([D54][decisions]). Une requête par ligne
     * en ferait des centaines pour un seul affichage — d'où le lot.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun categoriesOf(codes: Collection<String>): Map<String, String> {
        if (codes.isEmpty()) return emptyMap()
        val distinct = codes.distinct()
        val sql = "SELECT code, category FROM ciqual_food WHERE code IN (${distinct.joinToString { "?" }})"

        return database.rawQuery(sql, distinct.toTypedArray()).use { cursor ->
            cursor
                .map { it.getString(0) to it.optionalString(1) }
                .mapNotNull { (code, category) -> category?.let { code to it } }
                .toMap()
        }
    }

    /** Les portions usuelles d'un aliment. Vide s'il n'en a aucune : il proposera 100 g. */
    fun servings(code: String): List<CiqualServingRow> = database.rawQuery(SERVINGS_SQL, arrayOf(code)).use { cursor ->
        cursor.map {
            CiqualServingRow(
                label = it.getString(0),
                grams = it.getDouble(1),
                isDefault = it.getInt(2) == 1,
            )
        }
    }

    private fun open(): SQLiteDatabase {
        val target = File(context.filesDir, FILE_NAME)
        if (!target.exists()) copyFromAssets(target)
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun copyFromAssets(target: File) {
        // Un fichier temporaire puis un renommage : une copie interrompue -- batterie
        // vide, processus tue -- laisserait sinon une base tronquee que la prochaine
        // ouverture prendrait pour valide.
        val partial = File(target.parentFile, "$FILE_NAME.partial")
        context.assets.open(ASSET_NAME).use { input -> partial.outputStream().use(input::copyTo) }
        check(partial.renameTo(target)) { "Copie de $ASSET_NAME impossible vers ${target.path}" }
        retireOlderEditions(target)
    }

    private fun retireOlderEditions(current: File) {
        current.parentFile
            ?.listFiles { file -> file.name.startsWith(FILE_PREFIX) && file != current }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val ASSET_NAME = "ciqual.db"

        /**
         * L'édition de la table, dans le nom du fichier copié.
         *
         * À changer en même temps que l'archive de `tooling/ciqual/`. C'est ce qui
         * déclenche la recopie sur un appareil déjà installé.
         */
        const val EDITION = "2025-11-03"

        /**
         * La révision du **schéma**, indépendante de l'édition de l'ANSES.
         *
         * Sans elle, ajouter une colonne sans que l'ANSES ait republié laisserait le
         * nom du fichier inchangé : un appareil déjà installé garderait sa copie, et
         * la première requête sur la colonne neuve échouerait chez lui seulement —
         * jamais ici, où l'installation est toujours fraîche. C'est exactement le
         * genre de défaut que ce projet paie deux fois, et il se règle par un entier.
         *
         * À incrémenter dès que `CiqualDatabaseWriter.SCHEMA` change, ou que
         * `CiqualCategories` réarbitre un rayon.
         */
        const val REVISION = 2
        const val FILE_PREFIX = "ciqual-"
        const val FILE_NAME = "$FILE_PREFIX$EDITION-r$REVISION.db"

        const val SELECT_COLUMNS =
            """
            SELECT code, name, group_name, category, kcal_100, protein_100, carb_100,
                   sugar_100, fat_100, fiber_100, saturated_fat_100, salt_100
            """

        /**
         * La clause de rayon, ou rien du tout.
         *
         * Les `?` sont comptés ici et liés positionnellement : écrire les valeurs
         * dans la requête serait une concaténation de chaînes dans du SQL, et
         * l'habitude est ce qui compte, pas le fait que celles-ci viennent d'une
         * énumération fermée.
         */
        fun Set<String>.placeholders(): String = if (isEmpty()) "" else " AND category IN (${joinToString { "?" }})"

        fun searchSql(filter: String) =
            """
            $SELECT_COLUMNS
            FROM ciqual_food
            JOIN ciqual_fts ON ciqual_food.rowid = ciqual_fts.docid
            WHERE ciqual_fts MATCH ?$filter
            LIMIT ?
            """

        /**
         * Le mode parcours : un rayon, aucun mot.
         *
         * `name_search` en ordre plutôt que le `rowid` : sans tri, SQLite rend les
         * lignes dans l'ordre de l'index, ce qui donnerait toujours les mêmes trente
         * premières et ferait paraître le rayon minuscule. Le classement final reste
         * celui de `FoodRanking`, qui fait remonter ce qu'on mange vraiment.
         */
        fun browseSql(filter: String) =
            """
            $SELECT_COLUMNS
            FROM ciqual_food
            WHERE 1 = 1$filter
            ORDER BY LENGTH(name_search), name_search
            LIMIT ?
            """

        const val SERVINGS_SQL = "SELECT label, grams, is_default FROM ciqual_serving WHERE code = ? ORDER BY rowid"
    }
}

/** Parcourt un curseur et le referme, ce qu'aucune API d'Android ne fait pour nous. */
private fun <T> Cursor.map(transform: (Cursor) -> T): List<T> =
    generateSequence { takeIf { it.moveToNext() } }.map(transform).toList()

private fun Cursor.toFoodRow(): CiqualFoodRow {
    var column = 0
    return CiqualFoodRow(
        code = getString(column++),
        name = getString(column++),
        groupName = optionalString(column++),
        category = optionalString(column++),
        kcal100 = optionalDouble(column++),
        protein100 = optionalDouble(column++),
        carb100 = optionalDouble(column++),
        sugar100 = optionalDouble(column++),
        fat100 = optionalDouble(column++),
        fiber100 = optionalDouble(column++),
        saturatedFat100 = optionalDouble(column++),
        salt100 = optionalDouble(column),
    )
}

/**
 * `null` reste `null`.
 *
 * `getDouble` rend `0.0` sur une colonne nulle, et c'est exactement la confusion que
 * tout le projet évite : les fibres inconnues du pain deviendraient zéro gramme de
 * fibres, sans qu'aucune couche au-dessus puisse le savoir.
 */
private fun Cursor.optionalDouble(column: Int): Double? = if (isNull(column)) null else getDouble(column)

private fun Cursor.optionalString(column: Int): String? = if (isNull(column)) null else getString(column)
