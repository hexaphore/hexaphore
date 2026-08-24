package app.hexavore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Ecrites plutot que posees dans l'appel : les bornes d'une migration sont ce qu'on
// verifie en premier quand une chaine ne s'applique pas, et un litteral au milieu
// d'une liste de supertypes ne se voit pas.
private const val FROM_VERSION = 2
private const val TO_VERSION = 3

/**
 * Version 2 → 3 : le profil, le journal de poids et les objectifs versionnés.
 *
 * Trois tables neuves, et **rien d'existant n'est touché**. C'est la meilleure espèce
 * de migration : une base d'avant reste lisible après, aucune colonne ne change de
 * sens, et un journal de six mois traverse sans qu'une ligne soit réécrite.
 *
 * **Aucune donnée par défaut n'est écrite.** Ni profil, ni objectif : une base migrée
 * n'en a pas, et c'est exact — l'utilisateur n'a pas encore répondu aux questions.
 * Inventer un profil « moyen » produirait un objectif que personne n'a demandé, et les
 * journées déjà notées se retrouveraient jugées sur une règle qu'elles n'avaient pas
 * ([D04][decisions]). L'accueil affiche donc ses totaux sans jauge jusqu'à l'onboarding.
 *
 * **L'invariant « au plus un objectif actif » est tenu par un index unique**, pas par
 * une convention d'écriture. SQLite ne compare jamais deux `NULL` comme égaux, donc un
 * index sur `ended_at` seul ne contraindrait rien : la colonne `active_key` vaut `1`
 * tant que l'objectif court et l'identifiant une fois clos, ce qui fait entrer deux
 * objectifs actifs en collision.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/07-modele-de-donnees.md
 */
internal object Migration2To3 : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.createProfile()
        db.createWeightLog()
        db.createGoals()
    }

    private fun SupportSQLiteDatabase.createProfile() = execSQL(
        """
        CREATE TABLE IF NOT EXISTS `profile` (
            `id` TEXT NOT NULL,
            `birth_date` TEXT NOT NULL,
            `sex` TEXT NOT NULL,
            `height_cm` REAL NOT NULL,
            `activity_level` TEXT NOT NULL,
            `unit_system` TEXT NOT NULL,
            `created_at` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )

    private fun SupportSQLiteDatabase.createWeightLog() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS `weight_entry` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `weight_kg` REAL NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weight_entry_date` ON `weight_entry` (`date`)")
    }

    private fun SupportSQLiteDatabase.createGoals() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS `goal` (
                `id` TEXT NOT NULL,
                `started_at` TEXT NOT NULL,
                `ended_at` TEXT,
                `active_key` TEXT NOT NULL,
                `origin` TEXT NOT NULL,
                `strategy` TEXT NOT NULL,
                `target_weight_kg` REAL,
                `target_date` TEXT,
                `kcal` REAL NOT NULL,
                `protein_g` REAL NOT NULL,
                `carb_g` REAL NOT NULL,
                `sugar_g` REAL NOT NULL,
                `fat_g` REAL NOT NULL,
                `fiber_g` REAL NOT NULL,
                `manual_fields` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goal_active_key` ON `goal` (`active_key`)")
        execSQL("CREATE INDEX IF NOT EXISTS `index_goal_started_at` ON `goal` (`started_at`)")
    }
}
