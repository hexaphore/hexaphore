package app.hexaphore.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les migrations, éprouvées sur des données et pas seulement sur un schéma.
 *
 * Le mécanisme existait avant d'avoir quoi que ce soit à migrer, et c'était tout
 * son intérêt : la première vraie migration s'y ajoute au lieu de l'inaugurer.
 *
 * Ces tests partent d'une base de version 1 **peuplée**, migrent, et relisent le
 * contenu. Vérifier que la migration ne plante pas ne prouverait rien : une
 * migration qui perd une colonne, réécrit un `NULL` en `0` ou vide une table
 * réussit parfaitement de ce point de vue.
 *
 * Ils tournent sous Robolectric et non sur un appareil : un test de migration qu'il
 * faut brancher un téléphone pour exécuter est un test qu'on n'exécute pas.
 *
 * @see docs/12-plan-de-developpement.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class MigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            HexaphoreDatabase::class.java,
        )

    @Test
    fun `le schema vivant correspond a celui qui est versionne`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate()
    }

    @Test
    fun `un journal ecrit avant la migration se relit apres`() {
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT display_name, quantity, kcal FROM food_entry WHERE id = 'e1'").use { row ->
                assertTrue("la ligne de journal a disparu", row.moveToFirst())
                assertEquals("Riz basmati cuit", row.getString(0))
                assertEquals(150.0, row.getDouble(1), 0.0)
                assertEquals(232.0, row.getDouble(2), 0.0)
            }
        }
    }

    @Test
    fun `une valeur inconnue reste inconnue apres la migration`() {
        // La faute qu'une recopie de table rend facile : remplir les colonnes
        // nullables avec un defaut. Trois mois de journal deviendraient trois mois
        // sans fibres, et rien ne le dirait.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT fiber_g, protein_g FROM food_entry WHERE id = 'e1'").use { row ->
                row.moveToFirst()
                assertTrue("les fibres ont ete remplies par un defaut", row.isNull(0))
                assertEquals(4.8, row.getDouble(1), 0.0)
            }
        }
    }

    @Test
    fun `une ligne d avant le catalogue ne se voit pas attribuer de provenance`() {
        // NULL est exact : ces lignes ont ete tapees a la main, elles ne viennent
        // d'aucune fiche. Leur en inventer une fausserait le seul usage de cette
        // colonne, qui est de dire d'ou vient un chiffre.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT food_id FROM food_entry WHERE id = 'e1'").use { row ->
                row.moveToFirst()
                assertTrue("food_id devrait etre nul", row.isNull(0))
            }
        }
    }

    @Test
    fun `le plat et son lien de suppression survivent`() {
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.execSQL("PRAGMA foreign_keys = ON")
            database.execSQL("DELETE FROM dish WHERE id = 'd1'")

            database.query("SELECT COUNT(*) FROM food_entry").use { row ->
                row.moveToFirst()
                assertEquals("la cascade dish -> food_entry a ete perdue", 0, row.getInt(0))
            }
        }
    }

    @Test
    fun `supprimer un aliment ne supprime pas les lignes qui le citaient`() {
        // Le pendant du precedent, et l'invariant du projet : un journal est un
        // registre d'evenements. Supprimer un aliment personnel defait la
        // provenance, il n'ampute pas l'historique.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.execSQL("PRAGMA foreign_keys = ON")
            database.seedFood()
            database.execSQL("UPDATE food_entry SET food_id = 'f1' WHERE id = 'e1'")

            database.execSQL("DELETE FROM food WHERE id = 'f1'")

            database.query("SELECT display_name, kcal, food_id FROM food_entry WHERE id = 'e1'").use { row ->
                assertTrue("la ligne a ete supprimee avec l aliment", row.moveToFirst())
                assertEquals("Riz basmati cuit", row.getString(0))
                assertEquals(232.0, row.getDouble(1), 0.0)
                assertTrue("le lien de provenance aurait du se defaire", row.isNull(2))
            }
        }
    }

    @Test
    fun `deux aliments personnels sans reference cohabitent`() {
        // L'index unique porte sur (source, source_ref), et deux NULL ne sont jamais
        // egaux en SQLite : c'est ce qui le rend partiel sans clause WHERE. Un seul
        // aliment par code CIQUAL, mais autant d'aliments personnels que voulu.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedFood(id = "f1", source = "CUSTOM", reference = null)
            database.seedFood(id = "f2", source = "CUSTOM", reference = null)

            database.query("SELECT COUNT(*) FROM food").use { row ->
                row.moveToFirst()
                assertEquals(2, row.getInt(0))
            }
        }
    }

    @Test
    fun `un meme code CIQUAL ne peut pas entrer deux fois`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedFood(id = "f1", source = "CIQUAL", reference = "13039")
            val duplicate = runCatching { database.seedFood(id = "f2", source = "CIQUAL", reference = "13039") }

            assertTrue("le doublon aurait du etre refuse", duplicate.isFailure)
        }
    }

    // --- Version 2 -> 3 : profil, poids, objectifs versionnes ------------------

    @Test
    fun `un journal ecrit avant la tranche 4 se relit apres`() {
        // Trois tables neuves et rien qui bouge : c'est la meilleure espece de
        // migration, et c'est cette propriete-la qu'on eprouve.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT display_name, kcal, fiber_g FROM food_entry WHERE id = 'e1'").use { row ->
                assertTrue("la ligne de journal a disparu", row.moveToFirst())
                assertEquals("Riz basmati cuit", row.getString(0))
                assertEquals(232.0, row.getDouble(1), 0.0)
                assertTrue("les fibres ont ete remplies par un defaut", row.isNull(2))
            }
        }
    }

    @Test
    fun `la migration n invente ni profil ni objectif`() {
        // Un profil « moyen » produirait un objectif que personne n'a demande, et les
        // journees deja notees se retrouveraient jugees sur une regle qu'elles
        // n'avaient pas. L'absence est la reponse exacte.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            assertEquals("un profil a ete invente", 0, database.count("profile"))
            assertEquals("un objectif a ete invente", 0, database.count("goal"))
            assertEquals("une pesee a ete inventee", 0, database.count("weight_entry"))
        }
    }

    @Test
    fun `deux objectifs actifs sont refuses par la base`() {
        // L'invariant de D04, tenu par un index unique et non par une convention
        // d'ecriture. Deux NULL ne se heurtent jamais en SQLite : c'est `active_key`
        // qui les fait entrer en collision.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedGoal(id = "g1", startedAt = "2026-01-01", endedAt = null)
            val doublon = runCatching { database.seedGoal(id = "g2", startedAt = "2026-06-01", endedAt = null) }

            assertTrue("le second objectif actif aurait du etre refuse", doublon.isFailure)
        }
    }

    @Test
    fun `un objectif clos laisse la place a un nouveau`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedGoal(id = "g1", startedAt = "2026-01-01", endedAt = null)
            database.execSQL("UPDATE goal SET ended_at = '2026-06-01', active_key = id WHERE ended_at IS NULL")
            database.seedGoal(id = "g2", startedAt = "2026-06-01", endedAt = null)

            assertEquals(2, database.count("goal"))
        }
    }

    @Test
    fun `une journee est comparee a l objectif actif ce jour-la`() {
        // La requete que D04 achete. Le 15 mai releve du premier objectif, le 15
        // juillet du second, et la borne de fin est exclue : le 1er juin appartient
        // au nouveau.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedGoal(id = "g1", startedAt = "2026-01-01", endedAt = "2026-06-01", kcal = 2500.0)
            database.seedGoal(id = "g2", startedAt = "2026-06-01", endedAt = null, kcal = 2200.0)

            assertEquals(2500.0, database.goalKcalOn("2026-05-15"), 0.0)
            assertEquals(2200.0, database.goalKcalOn("2026-06-01"), 0.0)
            assertEquals(2200.0, database.goalKcalOn("2026-07-15"), 0.0)
        }
    }

    @Test
    fun `une journee anterieure au premier objectif n en a aucun`() {
        // Et non l'objectif courant applique retroactivement : une journee notee
        // avant qu'un objectif existe n'a rien a quoi se comparer.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedGoal(id = "g1", startedAt = "2026-01-01", endedAt = null)

            database.query(GOAL_ON_SQL, arrayOf("2025-12-31")).use { row ->
                assertEquals("une journee d avant le premier objectif en a recu un", 0, row.count)
            }
        }
    }

    @Test
    fun `une seule pesee par jour, la derniere remplace`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedWeight(id = "w1", date = "2026-08-10", kg = 88.0)
            val doublon = runCatching { database.seedWeight(id = "w2", date = "2026-08-10", kg = 87.5) }

            assertTrue("deux pesees le meme jour auraient du etre refusees", doublon.isFailure)
        }
    }

    @Test
    fun `le profil est une ligne unique`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedProfile()
            val doublon = runCatching { database.seedProfile() }

            assertTrue("un second profil aurait du etre refuse", doublon.isFailure)
        }
    }

    // --- Outillage ------------------------------------------------------------

    /**
     * `validateDroppedTables = true` : une table oubliée dans une migration future
     * doit faire échouer ce test, pas survivre en silence.
     *
     * L'opérateur d'étalement copie le tableau ; sur une chaîne qui compte une
     * migration, l'argument de performance ne s'applique pas, et l'API de Room
     * n'offre pas d'autre voie.
     */
    @Suppress("SpreadOperator")
    private fun migrate(): SupportSQLiteDatabase = helper.runMigrationsAndValidate(
        TEST_DATABASE,
        HexaphoreDatabase.VERSION,
        true,
        *HexaphoreDatabase.MIGRATIONS.toTypedArray(),
    )

    /** Un plat et sa ligne, écrits comme la version 1 les écrivait. */
    private fun SupportSQLiteDatabase.seedVersionOne() {
        execSQL(
            """
            INSERT INTO dish (id, date, source, logged_at, created_at, updated_at)
            VALUES ('d1', '2026-08-08', 'MANUAL', 1000, 1000, 1000)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO food_entry (
                id, dish_id, display_name, quantity, unit, grams,
                kcal, protein_g, carb_g, sugar_g, fat_g, fiber_g, created_at, updated_at
            ) VALUES (
                'e1', 'd1', 'Riz basmati cuit', 150.0, 'G', 150.0,
                232.0, 4.8, 49.5, 0.1, 0.5, NULL, 1000, 1000
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.seedFood(
        id: String = "f1",
        source: String = "CUSTOM",
        reference: String? = null,
    ) {
        execSQL(
            """
            INSERT INTO food (
                id, source, source_ref, name, name_search, brand,
                kcal_100, protein_100, carb_100, sugar_100, fat_100, fiber_100,
                saturated_fat_100, salt_100, default_serving_g,
                last_used_at, use_count, is_favorite, created_at, updated_at
            ) VALUES (
                ?, ?, ?, 'Riz basmati cuit', 'riz basmati cuit', NULL,
                155.0, 3.2, 33.0, 0.1, 0.3, NULL,
                NULL, NULL, NULL,
                NULL, 0, 0, 1000, 1000
            )
            """.trimIndent(),
            arrayOf(id, source, reference),
        )
    }

    private fun SupportSQLiteDatabase.count(table: String): Int = query("SELECT COUNT(*) FROM $table").use { row ->
        row.moveToFirst()
        row.getInt(0)
    }

    private fun SupportSQLiteDatabase.goalKcalOn(date: String): Double =
        query(GOAL_ON_SQL, arrayOf(date, date)).use { row ->
            assertTrue("aucun objectif pour le $date", row.moveToFirst())
            row.getDouble(0)
        }

    @Suppress("LongParameterList")
    private fun SupportSQLiteDatabase.seedGoal(
        id: String,
        startedAt: String,
        endedAt: String?,
        kcal: Double = 2500.0,
    ) {
        execSQL(
            """
            INSERT INTO goal (
                id, started_at, ended_at, active_key, origin, strategy,
                target_weight_kg, target_date,
                kcal, protein_g, carb_g, sugar_g, fat_g, fiber_g, manual_fields, created_at
            ) VALUES (?, ?, ?, ?, 'CALCULATED', 'LOSE', 80.0, '2027-02-08', ?, 144.0, 312.0, 63.0, 70.0, 35.0, '', 1000)
            """.trimIndent(),
            // `active_key` vaut 1 tant que l'objectif court, l'identifiant une fois
            // clos : c'est ce qui fait entrer deux objectifs actifs en collision.
            arrayOf(id, startedAt, endedAt, if (endedAt == null) "1" else id, kcal),
        )
    }

    private fun SupportSQLiteDatabase.seedWeight(id: String, date: String, kg: Double) {
        execSQL(
            "INSERT INTO weight_entry (id, date, weight_kg, created_at) VALUES (?, ?, ?, 1000)",
            arrayOf(id, date, kg),
        )
    }

    private fun SupportSQLiteDatabase.seedProfile() {
        execSQL(
            """
            INSERT INTO profile (id, birth_date, sex, height_cm, activity_level, unit_system, created_at, updated_at)
            VALUES ('singleton', '1991-03-04', 'MALE', 182.0, 'MODERATE', 'METRIC', 1000, 1000)
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"

        const val GOAL_ON_SQL =
            """
            SELECT kcal FROM goal
            WHERE started_at <= ? AND (ended_at IS NULL OR ended_at > ?)
            ORDER BY started_at DESC LIMIT 1
            """
    }
}

/**
 * Version d'Android simulée par Robolectric.
 *
 * Choisie parmi celles que la version de Robolectric embarque, et non alignée sur
 * `compileSdk` : ce test porte sur SQLite et sur le schéma, pas sur une API dont la
 * version importerait.
 */
internal const val ROBOLECTRIC_SDK = 33
