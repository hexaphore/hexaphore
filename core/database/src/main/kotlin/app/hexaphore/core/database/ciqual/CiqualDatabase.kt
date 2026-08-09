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
     * Les aliments dont le nom contient tous les mots de [normalisedQuery].
     *
     * La saisie est déjà normalisée par l'appelant, avec la même fonction qui a
     * rempli l'index. C'est la seule règle qui fasse fonctionner cette recherche, et
     * elle ne peut pas être vérifiée d'ici.
     */
    fun search(normalisedQuery: String, limit: Int): List<CiqualFoodRow> {
        if (normalisedQuery.isBlank()) return emptyList()
        // Chaque mot est un prefixe : « creme bru » doit trouver la creme brulee
        // avant que le second mot soit fini, sinon la recherche ne rend rien
        // pendant qu'on ecrit.
        val match = normalisedQuery.split(' ').filter { it.isNotBlank() }.joinToString(" ") { "$it*" }

        return database.rawQuery(SEARCH_SQL, arrayOf(match, limit.toString())).use { cursor ->
            cursor.map { it.toFoodRow() }
        }
    }

    /** Une fiche par son code CIQUAL. */
    fun byCode(code: String): CiqualFoodRow? =
        database.rawQuery("$SELECT_COLUMNS FROM ciqual_food WHERE code = ?", arrayOf(code)).use { cursor ->
            cursor.map { it.toFoodRow() }.firstOrNull()
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
        const val FILE_PREFIX = "ciqual-"
        const val FILE_NAME = "$FILE_PREFIX$EDITION.db"

        const val SELECT_COLUMNS =
            """
            SELECT code, name, group_name, kcal_100, protein_100, carb_100,
                   sugar_100, fat_100, fiber_100, saturated_fat_100, salt_100
            """

        const val SEARCH_SQL =
            """
            $SELECT_COLUMNS
            FROM ciqual_food
            JOIN ciqual_fts ON ciqual_food.rowid = ciqual_fts.docid
            WHERE ciqual_fts MATCH ?
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
