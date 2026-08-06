package app.hexaphore.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le mécanisme de migration, éprouvé avant d'être utile.
 *
 * À la version 1 il n'y a rien à migrer, et ce test paraît donc trivial. C'est
 * exactement pour cela qu'il existe maintenant : le jour où trois semaines de repas
 * sont sur un vrai téléphone, il est trop tard pour prendre l'habitude. La première
 * vraie migration s'ajoutera à une chaîne déjà en place, comparée à un schéma déjà
 * versionné, par un test déjà écrit.
 *
 * Il tourne sous Robolectric et non sur un appareil : un test de migration qu'il
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
        helper.createDatabase(TEST_DATABASE, HexaphoreDatabase.VERSION).close()

        // `validateDroppedTables = true` : une table oubliee dans une migration
        // future doit faire echouer ce test, pas survivre en silence.
        //
        // L'operateur d'etalement copie le tableau ; sur une chaine de migrations
        // qui en compte zero aujourd'hui et quelques-unes plus tard, l'argument de
        // performance ne s'applique pas, et l'API de Room n'offre pas d'autre voie.
        @Suppress("SpreadOperator")
        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            HexaphoreDatabase.VERSION,
            true,
            *HexaphoreDatabase.MIGRATIONS.toTypedArray(),
        )
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
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
