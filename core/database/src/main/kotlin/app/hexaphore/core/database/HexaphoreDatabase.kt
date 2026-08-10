package app.hexaphore.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import app.hexaphore.core.database.dao.DiaryDao
import app.hexaphore.core.database.dao.FoodDao
import app.hexaphore.core.database.dao.GoalDao
import app.hexaphore.core.database.dao.ProfileDao
import app.hexaphore.core.database.entity.DishEntity
import app.hexaphore.core.database.entity.FoodEntity
import app.hexaphore.core.database.entity.FoodEntryEntity
import app.hexaphore.core.database.entity.GoalEntity
import app.hexaphore.core.database.entity.ProfileEntity
import app.hexaphore.core.database.entity.WeightEntryEntity

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
    entities = [
        DishEntity::class,
        FoodEntryEntity::class,
        FoodEntity::class,
        ProfileEntity::class,
        WeightEntryEntity::class,
        GoalEntity::class,
    ],
    version = HexaphoreDatabase.VERSION,
    exportSchema = true,
)
abstract class HexaphoreDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    abstract fun foodDao(): FoodDao

    abstract fun profileDao(): ProfileDao

    abstract fun goalDao(): GoalDao

    companion object {
        const val VERSION = 3

        const val NAME = "hexaphore.db"

        /**
         * La chaîne de migrations.
         *
         * Elle existait **avant** d'être utile, et c'était tout son intérêt : la
         * première vraie migration s'y ajoute au lieu d'inaugurer un mécanisme, et
         * elle est validée par un test déjà écrit contre un schéma déjà versionné.
         */
        val MIGRATIONS: List<Migration> = listOf(Migration1To2, Migration2To3)

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
