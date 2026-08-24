package app.hexavore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Ecrites plutot que posees dans l'appel : les bornes d'une migration sont ce qu'on
// verifie en premier quand une chaine ne s'applique pas, et un litteral au milieu
// d'une liste de supertypes ne se voit pas.
private const val FROM_VERSION = 3
private const val TO_VERSION = 4

/**
 * Version 3 → 4 : `goal.manual_fields` disparaît.
 *
 * Le verrou par compteur devient un **mode** porté par `origin` ([D60][decisions]) : un
 * objectif est calculé ou il est saisi à la main, et il n'y a plus d'objectif calculé
 * contenant trois compteurs figés. La colonne n'a donc plus rien à décrire.
 *
 * **La table est recréée, et ce n'est pas un excès de prudence.** `ALTER TABLE … DROP
 * COLUMN` n'existe dans SQLite que depuis la version 3.35, livrée avec Android 14 ; le
 * projet descend à `minSdk 26`, où l'instruction échoue à l'exécution. Le chemin qui
 * marche partout est celui-ci : table neuve, copie, échange, index reconstruits.
 *
 * **Aucun objectif n'est perdu ni réécrit.** Les objectifs existants sont tous
 * `CALCULATED` — l'écran qui aurait pu en écrire un manuel n'a jamais atteint un
 * appareil —, donc `origin` reste exact et il n'y a rien à traduire. Une base où un
 * objectif portait des compteurs fixés garderait ses six chiffres tels quels : ce sont
 * eux qui comptent, et ils sont dans les colonnes voisines.
 *
 * **Les clés étrangères sont laissées où elles sont** : aucune table ne référence
 * `goal`, et la recréation ne peut donc casser aucun lien.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/07-modele-de-donnees.md
 */
internal object Migration3To4 : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `goal_new` (
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
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )

        // Les colonnes sont nommees une a une plutot que par un SELECT * : c'est ce
        // qui fait echouer la migration si le schema d'origine n'est pas celui qu'on
        // croit, au lieu de decaler silencieusement une valeur d'une colonne.
        db.execSQL(
            """
            INSERT INTO `goal_new` (
                `id`, `started_at`, `ended_at`, `active_key`, `origin`, `strategy`,
                `target_weight_kg`, `target_date`, `kcal`, `protein_g`, `carb_g`,
                `sugar_g`, `fat_g`, `fiber_g`, `created_at`
            )
            SELECT
                `id`, `started_at`, `ended_at`, `active_key`, `origin`, `strategy`,
                `target_weight_kg`, `target_date`, `kcal`, `protein_g`, `carb_g`,
                `sugar_g`, `fat_g`, `fiber_g`, `created_at`
            FROM `goal`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `goal`")
        db.execSQL("ALTER TABLE `goal_new` RENAME TO `goal`")

        // Les index ne survivent pas au renommage d'une table neuve : ils portaient sur
        // l'ancienne, qui vient d'etre supprimee. L'unique sur `active_key` est ce qui
        // tient l'invariant « au plus un objectif actif » (D55) -- l'oublier ici le
        // ferait disparaitre chez ceux qui migrent, et chez eux seulement.
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goal_active_key` ON `goal` (`active_key`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_started_at` ON `goal` (`started_at`)")
    }
}
