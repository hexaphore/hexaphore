package app.hexavore.core.database.di

import android.content.Context
import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.database.ciqual.CiqualDatabase
import app.hexavore.core.database.dao.CalendarDao
import app.hexavore.core.database.dao.DiaryDao
import app.hexavore.core.database.dao.FavoriteDishDao
import app.hexavore.core.database.dao.FoodCitationsDao
import app.hexavore.core.database.dao.FoodDao
import app.hexavore.core.database.dao.FoodMarksDao
import app.hexavore.core.database.dao.GoalDao
import app.hexavore.core.database.dao.ProfileDao
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
    fun database(@ApplicationContext context: Context): HexavoreDatabase = HexavoreDatabase.build(context)

    @Provides
    fun diaryDao(database: HexavoreDatabase): DiaryDao = database.diaryDao()

    @Provides
    fun calendarDao(database: HexavoreDatabase): CalendarDao = database.calendarDao()

    @Provides
    fun foodDao(database: HexavoreDatabase): FoodDao = database.foodDao()

    @Provides
    fun foodMarksDao(database: HexavoreDatabase): FoodMarksDao = database.foodMarksDao()

    @Provides
    fun foodCitationsDao(database: HexavoreDatabase): FoodCitationsDao = database.foodCitationsDao()

    @Provides
    fun profileDao(database: HexavoreDatabase): ProfileDao = database.profileDao()

    @Provides
    fun goalDao(database: HexavoreDatabase): GoalDao = database.goalDao()

    /**
     * Singleton, comme la base applicative : l'ouverture recopie l'asset au premier
     * appel, et *n* instances feraient *n* copies concurrentes du même fichier.
     */
    @Provides
    @Singleton
    fun ciqualDatabase(@ApplicationContext context: Context): CiqualDatabase = CiqualDatabase(context)

    @Provides
    fun favoriteDishDao(database: HexavoreDatabase): FavoriteDishDao = database.favoriteDishDao()
}
