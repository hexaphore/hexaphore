package app.hexaphore.tooling.ciqual

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types

/**
 * L'écriture de `ciqual.db`, la base livrée en lecture seule dans les assets.
 *
 * Elle n'est jamais migrée : à chaque publication de l'ANSES, elle est remplacée en
 * bloc ([docs/07][modele]). C'est ce qui autorise à faire évoluer ce schéma sans
 * cérémonie — contrairement à `hexaphore.db`, où la moindre colonne demande une
 * migration et son test.
 *
 * [modele]: docs/07-modele-de-donnees.md
 */
internal class CiqualDatabaseWriter(private val target: File) {
    fun write(foods: List<CiqualFood>, servings: List<CiqualServing>) {
        target.parentFile.mkdirs()
        target.delete()

        DriverManager.getConnection("jdbc:sqlite:${target.absolutePath}").use { connection ->
            connection.autoCommit = false
            connection.createSchema()
            connection.insertFoods(foods)
            connection.insertServings(servings)
            connection.commit()
            // Une base livree dans un APK est lue et jamais ecrite : la compacter
            // retire l'espace laisse par les index construits au fur et a mesure.
            // VACUUM refuse de s'executer dans une transaction, d'ou le retour a
            // l'autovalidation.
            connection.autoCommit = true
            connection.createStatement().use { it.execute("VACUUM") }
        }
    }

    private fun Connection.createSchema() = createStatement().use { statement ->
        SCHEMA.forEach(statement::executeUpdate)
    }

    /**
     * Le `rowid` est attribué **explicitement**, et c'est ce qui relie l'index au
     * catalogue.
     *
     * L'index est sans contenu : il ne stocke que les positions des mots, et rend
     * un `docid`. Ce `docid` n'a de sens que s'il désigne la même ligne que le
     * `rowid` de `ciqual_food` — le laisser attribuer par SQLite des deux côtés
     * marcherait par coïncidence, jusqu'au jour où une insertion échoue au milieu.
     */
    private fun Connection.insertFoods(foods: List<CiqualFood>) {
        batch(INSERT_FOOD, foods.withIndex()) { (index, food) ->
            integer(index + 1)
            text(food.code)
            text(food.name)
            text(SearchText.normalise(food.name))
            text(food.groupName)
            Nutrient.entries.forEach { real(food[it]) }
        }
        batch(INSERT_FTS, foods.withIndex()) { (index, food) ->
            integer(index + 1)
            text(SearchText.normalise(food.name))
        }
    }

    private fun Connection.insertServings(servings: List<CiqualServing>) {
        batch(INSERT_SERVING, servings) { serving ->
            text(serving.code)
            text(serving.label)
            real(serving.grams)
            integer(if (serving.isDefault) 1 else 0)
        }
    }

    private fun <T> Connection.batch(sql: String, rows: Iterable<T>, bind: Binding.(T) -> Unit) {
        prepareStatement(sql).use { statement ->
            rows.forEach { row ->
                Binding(statement).bind(row)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /**
     * Liaison positionnelle, sans écrire de rang.
     *
     * JDBC numérote ses paramètres à partir de 1, et une insertion de treize
     * colonnes devient treize littéraux qu'un ajout de colonne décale tous. Le rang
     * est donc compté ici : l'ordre des appels **est** l'ordre des colonnes.
     */
    private class Binding(private val statement: PreparedStatement) {
        private var position = 0

        fun text(value: String?) = statement.setString(++position, value)

        fun integer(value: Int) = statement.setInt(++position, value)

        /**
         * `setNull` et non `setDouble(0.0)` : la colonne reste `NULL`, et `NULL`
         * veut dire inconnu. C'est la règle du projet, tenue une couche plus bas.
         */
        fun real(value: Double?) {
            val parameter = ++position
            if (value == null) statement.setNull(parameter, Types.REAL) else statement.setDouble(parameter, value)
        }
    }

    internal companion object {
        /** `rowid`, `code`, `name`, `name_search`, `group_name`, puis les teneurs. */
        private const val FIXED_COLUMNS = 5

        val SCHEMA =
            listOf(
                """
                CREATE TABLE ciqual_food (
                    rowid INTEGER PRIMARY KEY,
                    code TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    name_search TEXT NOT NULL,
                    group_name TEXT,
                    ${Nutrient.entries.joinToString(",\n                    ") { "${it.column} REAL" }}
                )
                """.trimIndent(),
                // Index sans contenu, tokenizer `simple`.
                //
                // Sans contenu parce qu'on n'en attend qu'un `docid` : le nom
                // affiche vient de ciqual_food, et dupliquer 3 484 libelles dans
                // l'index doublerait sa taille pour rien.
                //
                // `simple` parce que name_search est deja de l'ASCII minuscule
                // separe par des espaces (voir SearchText) : unicode61 y ferait
                // exactement le meme decoupage, en ajoutant une dependance a une
                // extension dont la presence sur Android ne se verifie pas ici.
                """
                CREATE VIRTUAL TABLE ciqual_fts USING fts4(name_search, content="", tokenize=simple)
                """.trimIndent(),
                """
                CREATE TABLE ciqual_serving (
                    code TEXT NOT NULL,
                    label TEXT NOT NULL,
                    grams REAL NOT NULL,
                    is_default INTEGER NOT NULL
                )
                """.trimIndent(),
                "CREATE INDEX index_ciqual_serving_code ON ciqual_serving(code)",
            )

        // Les colonnes de teneur et l'ordre de liaison viennent de la meme liste :
        // reordonner Nutrient reordonne les deux ensemble, ou aucune des deux.
        val INSERT_FOOD =
            buildString {
                append("INSERT INTO ciqual_food (rowid, code, name, name_search, group_name")
                Nutrient.entries.forEach { append(", ").append(it.column) }
                append(") VALUES (")
                append(List(FIXED_COLUMNS + Nutrient.entries.size) { "?" }.joinToString())
                append(")")
            }

        const val INSERT_FTS = "INSERT INTO ciqual_fts (docid, name_search) VALUES (?, ?)"

        const val INSERT_SERVING = "INSERT INTO ciqual_serving (code, label, grams, is_default) VALUES (?, ?, ?, ?)"
    }
}
