package app.hexaphore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Ecrites plutot que posees dans l'appel : les bornes d'une migration sont ce qu'on
// verifie en premier quand une chaine ne s'applique pas, et un litteral au milieu
// d'une liste de supertypes ne se voit pas.
private const val FROM_VERSION = 5
private const val TO_VERSION = 6

/**
 * Version 5 → 6 : le cache d'Open Food Facts prend date.
 *
 * Deux colonnes sur `food` — `is_liquid` et `fetched_at` — annoncées par [07][modele]
 * et différées par [D50][decisions] jusqu'à la tranche qui les remplit.
 *
 * **La table n'est pas recréée, et c'est la première migration de ce projet à s'en
 * dispenser.** `ALTER TABLE … ADD COLUMN` existe dans toutes les versions de SQLite ;
 * ce sont la suppression d'une colonne (3 → 4) et l'ajout d'une clé étrangère
 * (4 → 5) qui obligeaient à la recopie. Les deux index de `food` — l'unique sur
 * `(source, source_ref)` et celui sur `last_used_at` — ne sont donc **pas** touchés,
 * puisque rien ne les renomme. Un test de comportement l'affirme quand même : c'est
 * la propriété qui compte, et la prochaine migration de cette table pourrait, elle,
 * la perdre.
 *
 * **Les deux colonnes sont nullables, et les fiches existantes restent à `NULL`.**
 * C'est exact : aucune n'a été récupérée d'Open Food Facts — la source n'existait pas
 * — et rien ne dit d'un aliment de la table de l'ANSES s'il est liquide. Un `0` par
 * défaut sur `is_liquid` affirmerait « solide » de 3 484 aliments que personne n'a
 * regardés.
 *
 * [modele]: docs/07-modele-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
internal object Migration5To6 : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `food` ADD COLUMN `is_liquid` INTEGER")
        db.execSQL("ALTER TABLE `food` ADD COLUMN `fetched_at` INTEGER")
    }
}
