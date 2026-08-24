package app.hexavore.core.database.di

import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.database.dao.BackupReadDao
import app.hexavore.core.database.dao.BackupWriteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Les deux DAO de la sauvegarde.
 *
 * Sortis de [DatabaseModule] quand le seuil de longueur a mordu, et le découpage suit
 * ce que les choses sont : les huit autres DAO servent les écrans, ceux-ci servent un
 * seul cas d'usage qui touche toutes les tables à la fois.
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupDaoModule {
    @Provides
    fun backupReadDao(database: HexavoreDatabase): BackupReadDao = database.backupReadDao()

    @Provides
    fun backupWriteDao(database: HexavoreDatabase): BackupWriteDao = database.backupWriteDao()
}
