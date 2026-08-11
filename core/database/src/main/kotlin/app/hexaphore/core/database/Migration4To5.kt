package app.hexaphore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Ecrites plutot que posees dans l'appel : les bornes d'une migration sont ce qu'on
// verifie en premier quand une chaine ne s'applique pas, et un litteral au milieu
// d'une liste de supertypes ne se voit pas.
private const val FROM_VERSION = 4
private const val TO_VERSION = 5

/**
 * Version 4 → 5 : les plats favoris, et le lien qui les relie au journal.
 *
 * Deux tables neuves, et une colonne sur `dish` — `favorite_id`, qui dit de quel favori
 * un plat a été rejoué. C'est elle qui rallume l'étoile en rouvrant un plat, et elle
 * seule qui rend « ce plat est un favori » vérifiable ([D62][decisions]).
 *
 * **`dish` est recréée, et c'est le point délicat de cette migration.** Ajouter une
 * colonne se fait bien en `ALTER TABLE ADD COLUMN`, mais pas une **clé étrangère** :
 * SQLite ne sait pas en ajouter une après coup. Or `dish` est référencée par
 * `food_entry` en `CASCADE`, et supprimer une table pour la recréer casserait ce lien
 * si les contraintes étaient actives pendant l'opération.
 *
 * Room exécute ses migrations avec `PRAGMA foreign_keys = FALSE`, puis rétablit et
 * lance `foreign_key_check`. C'est ce qui rend le `DROP` / `RENAME` sûr ici : pendant
 * la migration, `food_entry` référence un nom de table, et ce nom existe de nouveau à
 * la fin. **Le test de la cascade `dish → food_entry` couvre déjà ce cas** et tombe si
 * l'ordre est modifié — c'est lui qui garde la propriété, pas ce commentaire.
 *
 * **Aucun favori n'est inventé.** Une base migrée n'en a pas, et c'est exact : ils se
 * créent à l'étoile, un par un.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/07-modele-de-donnees.md
 */
internal object Migration4To5 : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.createFavorites()
        db.linkDishesToFavorites()
    }

    private fun SupportSQLiteDatabase.createFavorites() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorite_dish` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `name_search` TEXT NOT NULL,
                `use_count` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        // L'unicite porte sur le nom normalise : « Petit-dej » et « petit dej » ne se
        // distinguent pas dans une liste, donc la base les refuse.
        execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_dish_name_search` ON `favorite_dish` (`name_search`)",
        )
        execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_dish_use_count` ON `favorite_dish` (`use_count`)")

        execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorite_component` (
                `favorite_id` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `food_id` TEXT,
                `display_name` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `unit` TEXT NOT NULL,
                `grams` REAL NOT NULL,
                `kcal` REAL,
                `protein_g` REAL,
                `carb_g` REAL,
                `sugar_g` REAL,
                `fat_g` REAL,
                `fiber_g` REAL,
                PRIMARY KEY(`favorite_id`, `position`),
                FOREIGN KEY(`favorite_id`) REFERENCES `favorite_dish`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`food_id`) REFERENCES `food`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        execSQL(
            "CREATE INDEX IF NOT EXISTS `index_favorite_component_favorite_id` ON `favorite_component` (`favorite_id`)",
        )
        execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_component_food_id` ON `favorite_component` (`food_id`)")
    }

    private fun SupportSQLiteDatabase.linkDishesToFavorites() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dish_new` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `logged_at` INTEGER NOT NULL,
                `favorite_id` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`favorite_id`) REFERENCES `favorite_dish`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )

        // Les colonnes sont nommees une a une plutot que par un SELECT * : c'est ce
        // qui fait echouer la migration si le schema d'origine n'est pas celui qu'on
        // croit, au lieu de decaler silencieusement une valeur d'une colonne. Aucun
        // plat existant ne vient d'un favori -- il n'y en avait pas -- donc la
        // colonne neuve reste nulle, et c'est exact.
        execSQL(
            """
            INSERT INTO `dish_new` (`id`, `date`, `source`, `logged_at`, `created_at`, `updated_at`)
            SELECT `id`, `date`, `source`, `logged_at`, `created_at`, `updated_at` FROM `dish`
            """.trimIndent(),
        )

        execSQL("DROP TABLE `dish`")
        execSQL("ALTER TABLE `dish_new` RENAME TO `dish`")

        // Les index ne survivent pas au renommage d'une table neuve. Celui sur
        // (date, logged_at) porte la lecture la plus frequente de l'application.
        execSQL("CREATE INDEX IF NOT EXISTS `index_dish_date_logged_at` ON `dish` (`date`, `logged_at`)")
        execSQL("CREATE INDEX IF NOT EXISTS `index_dish_favorite_id` ON `dish` (`favorite_id`)")
    }
}
