package app.hexavore.di

import app.hexavore.data.backup.di.SafetyCopy
import app.hexavore.domain.backup.BackupTarget
import app.hexavore.domain.backup.SnapshotCodec
import app.hexavore.domain.backup.SnapshotStore
import app.hexavore.domain.backup.StoredPreferences
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.CreateBackup
import app.hexavore.domain.usecase.EraseEverything
import app.hexavore.domain.usecase.ExportBackup
import app.hexavore.domain.usecase.RestoreBackup
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage de la **sauvegarde**.
 *
 * Séparé des deux autres pour la même raison qu'eux : le journal, l'objectif et la
 * sauvegarde ne se lisent pas ensemble et ne changent pas pour les mêmes raisons.
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupUseCaseModule {
    @Provides
    fun exportBackup(store: SnapshotStore, codec: SnapshotCodec): ExportBackup = ExportBackup(store, codec)

    @Provides
    fun createBackup(export: ExportBackup, clock: Clock): CreateBackup = CreateBackup(export, clock)

    @Provides
    fun restoreBackup(
        store: SnapshotStore,
        codec: SnapshotCodec,
        createBackup: CreateBackup,
        @SafetyCopy safety: BackupTarget,
    ): RestoreBackup = RestoreBackup(store, codec, createBackup, safety)

    @Provides
    fun eraseEverything(store: SnapshotStore, preferences: StoredPreferences): EraseEverything =
        EraseEverything(store, preferences)
}
