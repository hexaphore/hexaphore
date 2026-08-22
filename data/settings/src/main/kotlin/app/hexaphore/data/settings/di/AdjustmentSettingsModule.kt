package app.hexaphore.data.settings.di

import android.content.Context
import android.content.SharedPreferences
import app.hexaphore.data.settings.StoredAdjustmentSettings
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.goal.AdjustmentSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Les réponses aux suggestions d'ajustement, dans leur propre fichier.
 *
 * Ni secret ni lié aux deux autres sujets rangés par ce module : effacer ses réglages
 * d'IA ne doit pas réactiver une adaptation qu'on avait désactivée, ni effacer la trace
 * d'un refus. **Aucun chiffrement** — ce ne sont pas des secrets mais des traces de
 * décision, et le protéger coûterait une lecture de Keystore par ouverture de
 * l'accueil.
 *
 * @see docs/03-nutrition-calculs.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AdjustmentSettingsModule {
    @Provides
    @Singleton
    @Named(ADJUSTMENT_PREFERENCES)
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(ADJUSTMENT_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun adjustment(
        @Named(ADJUSTMENT_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): AdjustmentSettings = StoredAdjustmentSettings(preferences, dispatchers)
}

private const val ADJUSTMENT_PREFERENCES_FILE = "adjustment_settings"

private const val ADJUSTMENT_PREFERENCES = "adjustment"
