package app.hexavore.tooling.ciqual

import app.hexavore.domain.food.SearchText
import app.hexavore.domain.nutrition.Macro
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
 * cérémonie — contrairement à `hexavore.db`, où la moindre colonne demande une
 * migration et son test.
 *
 * [modele]: docs/07-modele-de-donnees.md
 */
internal class CiqualDatabaseWriter(private val target: File) {
    fun write(
        foods: List<CiqualFood>,
        servings: List<CiqualServing>,
        shortNames: List<CiqualShortName>,
        completions: List<CiqualCompletion> = emptyList(),
    ) {
        target.parentFile.mkdirs()
        target.delete()

        DriverManager.getConnection("jdbc:sqlite:${target.absolutePath}").use { connection ->
            connection.autoCommit = false
            connection.createSchema()
            connection.insertFoods(
                foods,
                shortNames.associate { it.code to it.shortName },
                completions.groupBy { it.code },
            )
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
    private fun Connection.insertFoods(
        foods: List<CiqualFood>,
        shortNames: Map<String, String>,
        completions: Map<String, List<CiqualCompletion>>,
    ) {
        batch(INSERT_FOOD, foods.withIndex()) { (index, food) ->
            integer(index + 1)
            text(food.code)
            text(food.name)
            text(SearchText.normalise(food.name))
            // `NULL` quand aucun titre court n'a ete ecrit pour ce code, et c'est le
            // cas courant : un libelle deja lisible n'en recoit pas. L'affichage
            // retombe alors sur le libelle d'origine, ce qui est le comportement
            // d'avant cette colonne.
            text(shortNames[food.code])
            text(food.groupName)
            // Le nom de l'enumeration du domaine, et non un entier : une
            // renumerotation silencieuse rangerait les poissons dans les desserts,
            // et la base est lue par une version de l'application qui n'est pas
            // forcement celle qui l'a ecrite.
            text(food.category?.name)
            Nutrient.entries.forEach { real(food[it]) }
            // **Les completions dans leurs propres colonnes.** Melees aux mesures,
            // un nouvel import de la table de l'ANSES les ecraserait -- ou pire, les
            // prendrait pour des mesures. La lecture prefere toujours l'originale,
            // et c'est cette separation qui le rend possible.
            val estimated = completions[food.code].orEmpty().associate { it.macro.nutrient to it.value }
            ESTIMATED_NUTRIENTS.forEach { real(estimated[it]) }
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
        /** `rowid`, `code`, `name`, `name_search`, `short_name`, `group_name`, `category`, puis les teneurs. */
        private const val FIXED_COLUMNS = 7

        /**
         * Les six teneurs qu'une complétion peut porter.
         *
         * Les huit colonnes de [Nutrient] moins les deux que l'application n'affiche
         * pas : compléter des acides gras saturés que personne ne regarde serait une
         * dépense pour une valeur inventée que rien ne vérifierait jamais.
         *
         * L'ordre vient de [Macro], et il est **le même** pour le schéma et pour la
         * liaison — la seule façon de ne pas décaler six colonnes le jour où on en
         * ajoute une.
         */
        val ESTIMATED_NUTRIENTS: List<Nutrient> = Macro.entries.map { it.nutrient }

        val SCHEMA =
            listOf(
                """
                CREATE TABLE ciqual_food (
                    rowid INTEGER PRIMARY KEY,
                    code TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    name_search TEXT NOT NULL,
                    -- Le titre court, quand un libelle en valait la peine. NULL
                    -- signifie « le libelle d'origine suffit », jamais « pas encore
                    -- traite » : la generation ne demande que les libelles longs.
                    short_name TEXT,
                    group_name TEXT,
                    category TEXT,
                    ${Nutrient.entries.joinToString(",\n                    ") { "${it.column} REAL" }},
                    -- Les teneurs completees par un modele, dans leurs propres
                    -- colonnes. **Jamais melees aux mesures** : un nouvel import de
                    -- la table de l'ANSES les ecraserait, ou pire les prendrait pour
                    -- des mesures. Six et non huit : on ne complete que ce que
                    -- l'application affiche.
                    ${ESTIMATED_NUTRIENTS.joinToString(",\n                    ") { "${it.column}_est REAL" }}
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
                // Le mode parcours -- une pastille, champ vide -- balaie la table
                // entiere sans passer par l'index plein texte. Sans cet index, c'est
                // 3 484 lignes lues pour en rendre trente.
                "CREATE INDEX index_ciqual_food_category ON ciqual_food(category)",
            )

        // Les colonnes de teneur et l'ordre de liaison viennent de la meme liste :
        // reordonner Nutrient reordonne les deux ensemble, ou aucune des deux.
        val INSERT_FOOD =
            buildString {
                append("INSERT INTO ciqual_food (rowid, code, name, name_search, short_name, group_name, category")
                Nutrient.entries.forEach { append(", ").append(it.column) }
                ESTIMATED_NUTRIENTS.forEach { append(", ").append(it.column).append("_est") }
                append(") VALUES (")
                append(List(FIXED_COLUMNS + Nutrient.entries.size + ESTIMATED_NUTRIENTS.size) { "?" }.joinToString())
                append(")")
            }

        const val INSERT_FTS = "INSERT INTO ciqual_fts (docid, name_search) VALUES (?, ?)"

        const val INSERT_SERVING = "INSERT INTO ciqual_serving (code, label, grams, is_default) VALUES (?, ?, ?, ?)"
    }
}
