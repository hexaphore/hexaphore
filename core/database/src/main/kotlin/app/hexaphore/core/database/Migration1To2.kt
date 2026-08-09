package app.hexaphore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 → 2 : le catalogue d'aliments arrive.
 *
 * La première migration réelle du projet. Elle porte la forme que [docs/07][modele]
 * privilégie — **une colonne nullable plutôt qu'une table nouvelle, une table
 * nouvelle plutôt qu'un renommage** — et elle est de la meilleure espèce : rien
 * d'existant ne change de sens, rien n'est réécrit, et une base d'avant la
 * migration reste lisible après.
 *
 * `food_entry.food_id` arrive à `NULL` pour toutes les lignes déjà écrites, et
 * c'est exact : elles ont été tapées à la main, elles ne viennent d'aucune fiche.
 * Une valeur par défaut leur aurait inventé une provenance.
 *
 * **`SET NULL` et surtout pas `CASCADE`** sur le lien vers `food`. Supprimer un
 * aliment personnel ne doit pas amputer l'historique : la ligne de journal survit
 * avec ses macros figées et son nom d'affichage, et c'est le lien de provenance qui
 * se défait ([D05][decisions]).
 *
 * L'ajout de colonne se fait par recopie de table plutôt que par `ALTER TABLE ADD
 * COLUMN ... REFERENCES`. SQLite accepte la seconde forme quand la valeur par
 * défaut est nulle, mais la contrainte de clé étrangère qu'elle enregistre dépend
 * de l'état de `PRAGMA foreign_keys` au moment de l'exécution. La recopie ne dépend
 * de rien : c'est la forme que Room génère lui-même, et celle que le test compare
 * au schéma versionné.
 *
 * [modele]: docs/07-modele-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
internal object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.createFoodCatalogue()
        db.linkEntriesToFoods()
    }

    private fun SupportSQLiteDatabase.createFoodCatalogue() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS `food` (
                `id` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `source_ref` TEXT,
                `name` TEXT NOT NULL,
                `name_search` TEXT NOT NULL,
                `brand` TEXT,
                `kcal_100` REAL,
                `protein_100` REAL,
                `carb_100` REAL,
                `sugar_100` REAL,
                `fat_100` REAL,
                `fiber_100` REAL,
                `saturated_fat_100` REAL,
                `salt_100` REAL,
                `default_serving_g` REAL,
                `last_used_at` INTEGER,
                `use_count` INTEGER NOT NULL,
                `is_favorite` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_source_source_ref` ON `food` (`source`, `source_ref`)")
        execSQL("CREATE INDEX IF NOT EXISTS `index_food_last_used_at` ON `food` (`last_used_at`)")
    }

    private fun SupportSQLiteDatabase.linkEntriesToFoods() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS `food_entry_new` (
                `id` TEXT NOT NULL,
                `dish_id` TEXT NOT NULL,
                `food_id` TEXT,
                `display_name` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `unit` TEXT NOT NULL,
                `grams` REAL NOT NULL,
                `kcal` REAL NOT NULL,
                `protein_g` REAL,
                `carb_g` REAL,
                `sugar_g` REAL,
                `fat_g` REAL,
                `fiber_g` REAL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`dish_id`) REFERENCES `dish`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                FOREIGN KEY(`food_id`) REFERENCES `food`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO `food_entry_new` (
                `id`, `dish_id`, `food_id`, `display_name`, `quantity`, `unit`, `grams`,
                `kcal`, `protein_g`, `carb_g`, `sugar_g`, `fat_g`, `fiber_g`,
                `created_at`, `updated_at`
            )
            SELECT
                `id`, `dish_id`, NULL, `display_name`, `quantity`, `unit`, `grams`,
                `kcal`, `protein_g`, `carb_g`, `sugar_g`, `fat_g`, `fiber_g`,
                `created_at`, `updated_at`
            FROM `food_entry`
            """.trimIndent(),
        )
        execSQL("DROP TABLE `food_entry`")
        execSQL("ALTER TABLE `food_entry_new` RENAME TO `food_entry`")
        execSQL("CREATE INDEX IF NOT EXISTS `index_food_entry_dish_id` ON `food_entry` (`dish_id`)")
        execSQL("CREATE INDEX IF NOT EXISTS `index_food_entry_food_id` ON `food_entry` (`food_id`)")
    }
}
