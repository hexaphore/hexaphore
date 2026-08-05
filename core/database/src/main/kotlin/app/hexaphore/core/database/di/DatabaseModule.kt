package app.hexaphore.core.database.di

import android.content.Context
import app.hexaphore.core.database.HexaphoreDatabase
import app.hexaphore.core.database.dao.DiaryDao
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
}
