package app.hexavore.tooling.ciqual

import app.hexavore.domain.food.FoodCategory
import app.hexavore.domain.food.SearchText
import app.hexavore.domain.nutrition.Macro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Ce que la base générée doit savoir faire, éprouvé sur la base elle-même.
 *
 * Ces tests ne portent pas sur du code Kotlin mais sur un fichier SQLite : c'est
 * lui qui part dans l'APK, et c'est de lui que dépendent les deux critères de fin
 * de tranche — « creme brulee » trouve « crème brûlée », et `null` ne se confond
 * jamais avec `0`.
 */
class CiqualDatabaseWriterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `creme brulee trouve creme brulee`() {
        // Le critere de fin de tranche, verifie de bout en bout : normalisation a
        // l'ecriture, normalisation a la lecture, et le tokenizer entre les deux.
        val results = write(CREME, POMME).use { it.search("creme brulee") }

        assertEquals(listOf("Crème brûlée"), results)
    }

    @Test
    fun `une saisie accentuee trouve la meme chose`() {
        val results = write(CREME, POMME).use { it.search("crème brûlée") }

        assertEquals(listOf("Crème brûlée"), results)
    }

    @Test
    fun `un mot du milieu suffit`() {
        // FTS indexe des mots, pas des prefixes de libelle : « brulee » seul doit
        // rendre la creme, sans quoi il faudrait taper les libelles CIQUAL depuis
        // leur premier mot.
        val results = write(CREME, POMME).use { it.search("brulee") }

        assertEquals(listOf("Crème brûlée"), results)
    }

    @Test
    fun `une valeur inconnue reste NULL et jamais zero`() {
        // Le piege de la tranche, tenu jusque dans la colonne SQL. Un 0.0 ici
        // remonterait en « 0 g de fibres » a l'ecran, ce que personne ne pourrait
        // distinguer d'une mesure.
        write(POMME).use { connection ->
            assertNull(connection.nutrient("13039", Nutrient.FIBER))
            assertEquals(54.0, connection.nutrient("13039", Nutrient.KCAL))
        }
    }

    @Test
    fun `une teneur nulle reste zero, parce que c est une mesure`() {
        write(POMME).use { assertEquals(0.0, it.nutrient("13039", Nutrient.FAT)) }
    }

    @Test
    fun `l index designe la meme ligne que le catalogue`() {
        // Le docid de l'index sans contenu et le rowid du catalogue sont attribues
        // separement : s'ils divergeaient, une recherche rendrait le bon nombre de
        // resultats avec les mauvais aliments. C'est le genre d'erreur qu'on ne
        // voit qu'en lisant les noms.
        write(CREME, POMME, THE).use { connection ->
            assertEquals(listOf("Thé infusé"), connection.search("the"))
            assertEquals(listOf("Pomme, chair et peau, crue"), connection.search("pomme"))
        }
    }

    @Test
    fun `les portions suivent leur aliment`() {
        val servings =
            listOf(
                CiqualServing("13039", "1 pomme moyenne", grams = 150.0, isDefault = true),
                CiqualServing("13039", "1 quartier", grams = 40.0, isDefault = false),
            )

        write(foods = listOf(POMME), servings = servings).use { connection ->
            val rows = connection.servings("13039")
            assertEquals(listOf("1 pomme moyenne" to true, "1 quartier" to false), rows)
        }
    }

    @Test
    fun `un aliment sans portion n en a aucune`() {
        write(POMME).use { assertTrue(it.servings("13039").isEmpty()) }
    }

    @Test
    fun `le rayon est ecrit sous le nom de l enumeration du domaine`() {
        // Et non sous un entier : la base est lue par une version de l'application
        // qui n'est pas forcement celle qui l'a ecrite, et une renumerotation
        // rangerait les poissons dans les desserts sans que rien ne le dise.
        write(POMME, CREME).use { connection ->
            assertEquals("FRUITS", connection.category("13039"))
            assertEquals("PRODUITS_LAITIERS", connection.category("39213"))
        }
    }

    @Test
    fun `un aliment sans rayon garde une colonne nulle`() {
        // Comme pour les teneurs : l'absence est une reponse. Une chaine vide ou un
        // rayon par defaut le ferait sortir sous une pastille au hasard.
        write(THE).use { assertNull(it.category("18066"), "un the n'a aucun des huit rayons") }
    }

    @Test
    fun `le titre court est ecrit a cote du libelle, sans le remplacer`() {
        // Le libelle d'origine ne bouge jamais : c'est lui qui relie la fiche a sa
        // source, et c'est sur lui que la recherche compare. Le titre court est une
        // seconde colonne, jamais une reecriture de la premiere.
        val titre = CiqualShortName("13039", "Pomme crue")

        write(foods = listOf(POMME), shortNames = listOf(titre)).use { connection ->
            assertEquals("Pomme crue", connection.shortName("13039"))
            assertEquals("Pomme, chair et peau, crue", connection.name("13039"))
        }
    }

    @Test
    fun `un aliment sans titre court garde une colonne nulle`() {
        // L'absence est une reponse : « le libelle se lit tres bien tel quel », et
        // non « pas encore traite ». Une chaine vide obligerait chaque lecture a
        // distinguer les deux.
        write(POMME, CREME).use { assertNull(it.shortName("13039"), "aucun titre n'a ete ecrit pour elle") }
    }

    @Test
    fun `le titre court d un aliment ne deborde pas sur un autre`() {
        // Les titres sont lies par code, pas par position : une liaison
        // positionnelle collerait le titre de la pomme sur la creme des que l'ordre
        // des deux listes differerait.
        val titre = CiqualShortName("39213", "Creme brulee")

        write(foods = listOf(POMME, CREME), shortNames = listOf(titre)).use { connection ->
            assertEquals("Creme brulee", connection.shortName("39213"))
            assertNull(connection.shortName("13039"))
        }
    }

    @Test
    fun `une teneur completee va dans sa propre colonne, jamais dans celle de la mesure`() {
        // **La regle qui commande toute cette passe.** Melees, un nouvel import de la
        // table de l'ANSES ecraserait les completions -- ou pire, les prendrait pour
        // des mesures.
        val completion = CiqualCompletion("13039", Macro.FIBER, 2.4)

        write(foods = listOf(POMME), completions = listOf(completion)).use { connection ->
            assertEquals(2.4, connection.estimatedNutrient("13039", Nutrient.FIBER))
            assertEquals(POMME[Nutrient.FIBER], connection.nutrient("13039", Nutrient.FIBER))
        }
    }

    @Test
    fun `sans completion, les colonnes d estimation restent nulles`() {
        // `NULL` veut dire « rien n'a ete complete », et c'est le cas de 91 % de la
        // table. Un zero y affirmerait qu'un modele a repondu zero.
        write(POMME, CREME).use { assertNull(it.estimatedNutrient("13039", Nutrient.KCAL)) }
    }

    @Test
    fun `la completion d un aliment ne deborde pas sur un autre`() {
        // Liees par code **et** par teneur : une liaison positionnelle collerait
        // l'estimation de la pomme sur la creme des que l'ordre differerait.
        val completion = CiqualCompletion("39213", Macro.CALORIES, 300.0)

        write(foods = listOf(POMME, CREME), completions = listOf(completion)).use { connection ->
            assertEquals(300.0, connection.estimatedNutrient("39213", Nutrient.KCAL))
            assertNull(connection.estimatedNutrient("13039", Nutrient.KCAL))
        }
    }

    @Test
    fun `chaque completion va dans la colonne de sa propre teneur`() {
        // Sans ce cas, une liaison qui ecrirait toutes les valeurs dans la premiere
        // colonne passerait les trois precedents.
        val completions = listOf(
            CiqualCompletion("13039", Macro.CALORIES, 52.0),
            CiqualCompletion("13039", Macro.FIBER, 2.4),
        )

        write(foods = listOf(POMME), completions = completions).use { connection ->
            assertEquals(52.0, connection.estimatedNutrient("13039", Nutrient.KCAL))
            assertEquals(2.4, connection.estimatedNutrient("13039", Nutrient.FIBER))
            assertNull(connection.estimatedNutrient("13039", Nutrient.PROTEIN))
        }
    }

    @Test
    fun `le mode parcours rend les aliments d un rayon`() {
        // Une pastille, aucun mot : la requete ne passe pas par l'index plein texte.
        write(POMME, CREME, THE).use { connection ->
            assertEquals(listOf("Pomme, chair et peau, crue"), connection.browse("FRUITS"))
            assertTrue(connection.browse("SNACKS").isEmpty())
        }
    }

    // --- Outillage ------------------------------------------------------------

    private fun write(vararg foods: CiqualFood): Connection = write(foods.toList(), emptyList())

    private fun write(
        foods: List<CiqualFood>,
        servings: List<CiqualServing> = emptyList(),
        shortNames: List<CiqualShortName> = emptyList(),
        completions: List<CiqualCompletion> = emptyList(),
    ): Connection {
        val file = File(directory.toFile(), "ciqual.db")
        CiqualDatabaseWriter(file).write(foods, servings, shortNames, completions)
        return DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
    }

    private fun Connection.search(query: String): List<String> {
        val sql =
            """
            SELECT f.name FROM ciqual_fts x JOIN ciqual_food f ON f.rowid = x.docid
            WHERE x.name_search MATCH ? ORDER BY f.code
            """.trimIndent()
        return prepareStatement(sql).use { statement ->
            statement.setString(1, SearchText.normalise(query))
            statement.executeQuery().use { rows ->
                generateSequence { rows.takeIf { it.next() }?.getString(1) }.toList()
            }
        }
    }

    private fun Connection.nutrient(code: String, nutrient: Nutrient): Double? =
        prepareStatement("SELECT ${nutrient.column} FROM ciqual_food WHERE code = ?").use { statement ->
            statement.setString(1, code)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getDouble(1).takeUnless { rows.wasNull() }
            }
        }

    /** La teneur **completee**, lue dans la colonne qui lui est propre. */
    private fun Connection.estimatedNutrient(code: String, nutrient: Nutrient): Double? =
        prepareStatement("SELECT ${nutrient.column}_est FROM ciqual_food WHERE code = ?").use { statement ->
            statement.setString(1, code)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getDouble(1).takeUnless { rows.wasNull() }
            }
        }

    private fun Connection.category(code: String): String? = textColumn("category", code)

    private fun Connection.shortName(code: String): String? = textColumn("short_name", code)

    private fun Connection.name(code: String): String? = textColumn("name", code)

    private fun Connection.textColumn(column: String, code: String): String? =
        prepareStatement("SELECT $column FROM ciqual_food WHERE code = ?").use { statement ->
            statement.setString(1, code)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getString(1)
            }
        }

    private fun Connection.browse(category: String): List<String> =
        prepareStatement("SELECT name FROM ciqual_food WHERE category = ? ORDER BY code").use { statement ->
            statement.setString(1, category)
            statement.executeQuery().use { rows ->
                generateSequence { rows.takeIf { it.next() }?.getString(1) }.toList()
            }
        }

    private fun Connection.servings(code: String): List<Pair<String, Boolean>> = prepareStatement(
        "SELECT label, is_default FROM ciqual_serving WHERE code = ? ORDER BY rowid",
    ).use { statement ->
        statement.setString(1, code)
        statement.executeQuery().use { rows ->
            generateSequence { rows.takeIf { it.next() }?.let { it.getString(1) to (it.getInt(2) == 1) } }.toList()
        }
    }

    private companion object {
        val POMME =
            CiqualFood(
                code = "13039",
                name = "Pomme, chair et peau, crue",
                groupName = "fruits",
                category = FoodCategory.FRUITS,
                // Pas de fibres : l'inconnu est l'absence de cle, pas une valeur.
                // Les lipides, eux, sont mesures a zero.
                nutrients = mapOf(Nutrient.KCAL to 54.0, Nutrient.FAT to 0.0),
            )

        val CREME =
            CiqualFood(
                code = "39213",
                name = "Crème brûlée",
                groupName = "desserts",
                category = FoodCategory.PRODUITS_LAITIERS,
                nutrients = mapOf(Nutrient.KCAL to 269.0),
            )

        /** Sans rayon : toutes les fiches n'en ont pas, et la colonne doit rester nulle. */
        val THE =
            CiqualFood(
                code = "18066",
                name = "Thé infusé",
                groupName = "boissons",
                category = null,
                nutrients = mapOf(Nutrient.KCAL to 1.0),
            )
    }
}
