package app.hexaphore.core.database.di

import android.content.Context
import app.hexaphore.core.database.HexaphoreDatabase
import app.hexaphore.core.database.ciqual.CiqualDatabase
import app.hexaphore.core.database.dao.CalendarDao
import app.hexaphore.core.database.dao.DiaryDao
import app.hexaphore.core.database.dao.FavoriteDishDao
import app.hexaphore.core.database.dao.FoodCitationsDao
import app.hexaphore.core.database.dao.FoodDao
import app.hexaphore.core.database.dao.FoodMarksDao
import app.hexaphore.core.database.dao.GoalDao
import app.hexaphore.core.database.dao.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * La base et ses DAO.
 *
 * Ce module vit dans `:core:database` et non dans `:app` : chaque module Gradle
 * expose ses propres liaisons, et `:app` ne fait qu'assembler. Un test qui veut une
 * base en mémoire remplace ce module entier plutôt que d'aller chercher une ligne
 * au milieu du graphe applicatif.
 *
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HexaphoreDatabase = HexaphoreDatabase.build(context)

    @Provides
    fun diaryDao(database: HexaphoreDatabase): DiaryDao = database.diaryDao()

    @Provides
    fun calendarDao(database: HexaphoreDatabase): CalendarDao = database.calendarDao()

    @Provides
    fun foodDao(database: HexaphoreDatabase): FoodDao = database.foodDao()

    @Provides
    fun foodMarksDao(database: HexaphoreDatabase): FoodMarksDao = database.foodMarksDao()

    @Provides
    fun foodCitationsDao(database: HexaphoreDatabase): FoodCitationsDao = database.foodCitationsDao()

    @Provides
    fun profileDao(database: HexaphoreDatabase): ProfileDao = database.profileDao()

    @Provides
    fun goalDao(database: HexaphoreDatabase): GoalDao = database.goalDao()

    /**
     * Singleton, comme la base applicative : l'ouverture recopie l'asset au premier
     * appel, et *n* instances feraient *n* copies concurrentes du même fichier.
     */
    @Provides
    @Singleton
    fun ciqualDatabase(@ApplicationContext context: Context): CiqualDatabase = CiqualDatabase(context)

    @Provides
    fun favoriteDishDao(database: HexaphoreDatabase): FavoriteDishDao = database.favoriteDishDao()
}
