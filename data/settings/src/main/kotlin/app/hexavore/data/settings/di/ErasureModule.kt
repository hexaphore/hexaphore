package app.hexavore.data.settings.di

import android.content.SharedPreferences
import app.hexavore.data.settings.ErasablePreferences
import app.hexavore.data.settings.StoredAdjustmentSettings
import app.hexavore.data.settings.StoredAiCredentials
import app.hexavore.data.settings.StoredContributionSettings
import app.hexavore.domain.backup.StoredPreferences
import app.hexavore.domain.concurrency.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Ce qu'« Effacer toutes mes données » doit vider, hors du journal.
 *
 * Un module à lui plutôt qu'une méthode ajoutée à l'un des trois autres : il est le
 * seul à les connaître **tous**, et le loger dans l'un d'eux aurait fait dépendre les
 * réglages d'IA de l'existence d'un compte de contribution.
 *
 * **La liste des fichiers est écrite ici**, à l'endroit le plus visible qui existe. Un
 * quatrième devra s'y ajouter à la main ; c'est le prix de ne pas dépendre d'une
 * découverte automatique qui, elle, se tairait en cas d'oubli.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ErasureModule {
    @Provides
    @Singleton
    fun storedPreferences(
        credentials: StoredAiCredentials,
        contribution: StoredContributionSettings,
        adjustment: StoredAdjustmentSettings,
        @Named(AI_PREFERENCES) ai: SharedPreferences,
        @Named(ADJUSTMENT_PREFERENCES) adjustmentFile: SharedPreferences,
        @Named(CONTRIBUTION_PREFERENCES) contributionFile: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): StoredPreferences = ErasablePreferences(
        credentials = credentials,
        contribution = contribution,
        adjustment = adjustment,
        files = listOf(ai, adjustmentFile, contributionFile),
        dispatchers = dispatchers,
    )
}
