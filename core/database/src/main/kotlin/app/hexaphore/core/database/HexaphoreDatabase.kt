package app.hexaphore.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import app.hexaphore.core.database.dao.DiaryDao
import app.hexaphore.core.database.entity.DishEntity
import app.hexaphore.core.database.entity.FoodEntryEntity

/**
 * La base locale de l'application.
 *
 * `exportSchema = true` n'est pas décoratif : c'est le schéma exporté qui sert de
 * référence au test de migration. Sans lui, une migration ne se vérifierait plus
 * qu'à l'œil, et une colonne oubliée ne se découvrirait que sur l'appareil de
 * quelqu'un.
 *
 * @see docs/07-modele-de-donnees.md
 */
@Database(
    entities = [DishEntity::class, FoodEntryEntity::class],
    version = HexaphoreDatabase.VERSION,
    exportSchema = true,
)
abstract class HexaphoreDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        const val VERSION = 1

        const val NAME = "hexaphore.db"

        /**
         * La chaîne de migrations, vide à la version 1.
         *
         * Elle existe **avant** d'être utile, et c'est tout son intérêt. Le jour où
         * trois semaines de repas sont sur un vrai téléphone, il est trop tard pour
         * prendre l'habitude : la première migration doit s'ajouter à une liste qui
         * existe déjà, testée par un mécanisme déjà en place.
         */
        val MIGRATIONS: List<Migration> = emptyList()

        /**
         * Construit la base.
         *
         * **`fallbackToDestructiveMigration` n'apparaît nulle part**, et ne doit
         * jamais apparaître — pas même « le temps du développement ». Perdre le
         * journal alimentaire de quelqu'un est une faute non rattrapable, et
         * l'habitude prise en développement est celle qu'on emporte en production.
         * Une migration manquante doit faire planter le build de la migration, pas
         * effacer des données.
         */
        fun build(context: Context): HexaphoreDatabase =
            Room.databaseBuilder(context, HexaphoreDatabase::class.java, NAME)
                .apply { MIGRATIONS.forEach { addMigrations(it) } }
                .build()
    }
}
