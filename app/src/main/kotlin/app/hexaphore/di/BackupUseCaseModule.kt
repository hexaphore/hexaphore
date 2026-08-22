package app.hexaphore.di

import app.hexaphore.data.backup.di.SafetyCopy
import app.hexaphore.domain.ai.AiCredentials
import app.hexaphore.domain.backup.BackupTarget
import app.hexaphore.domain.backup.SnapshotCodec
import app.hexaphore.domain.backup.SnapshotStore
import app.hexaphore.domain.food.ContributionSettings
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.CreateBackup
import app.hexaphore.domain.usecase.EraseEverything
import app.hexaphore.domain.usecase.RestoreBackup
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
    fun createBackup(store: SnapshotStore, codec: SnapshotCodec, clock: Clock): CreateBackup =
        CreateBackup(store, codec, clock)

    @Provides
    fun restoreBackup(
        store: SnapshotStore,
        codec: SnapshotCodec,
        createBackup: CreateBackup,
        @SafetyCopy safety: BackupTarget,
    ): RestoreBackup = RestoreBackup(store, codec, createBackup, safety)

    @Provides
    fun eraseEverything(
        store: SnapshotStore,
        credentials: AiCredentials,
        contribution: ContributionSettings,
    ): EraseEverything = EraseEverything(store, credentials, contribution)
}
