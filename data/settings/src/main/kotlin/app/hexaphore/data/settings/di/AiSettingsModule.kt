package app.hexaphore.data.settings.di

import android.content.Context
import android.content.SharedPreferences
import app.hexaphore.data.settings.KeystoreCipher
import app.hexaphore.data.settings.SecretCipher
import app.hexaphore.data.settings.StoredAiCredentials
import app.hexaphore.domain.ai.AiCredentials
import app.hexaphore.domain.ai.AiSettings
import app.hexaphore.domain.concurrency.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Ce que ce module lie : deux ports du domaine, et rien qui sorte d'ici.
 *
 * Le chiffrement et le fichier de préférences restent **internes** : exposer l'un ou
 * l'autre ferait de ce module le rangement à secrets de tout le projet, et le premier
 * appelant pressé y aurait mis autre chose.
 *
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AiSettingsModule {
    @Provides
    @Singleton
    @Named(AI_PREFERENCES)
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AI_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun cipher(): SecretCipher = KeystoreCipher()

    @Provides
    @Singleton
    fun stored(
        @Named(AI_PREFERENCES) preferences: SharedPreferences,
        cipher: SecretCipher,
        dispatchers: DispatcherProvider,
    ): StoredAiCredentials = StoredAiCredentials(preferences, cipher, dispatchers)

    @Provides
    fun credentials(stored: StoredAiCredentials): AiCredentials = stored

    @Provides
    fun settings(stored: StoredAiCredentials): AiSettings = stored
}

/**
 * Un fichier à part, et non les préférences par défaut de l'application.
 *
 * Deux raisons se renforcent : les règles d'extraction de données peuvent l'exclure
 * nommément des sauvegardes ([docs/05][ia] veut qu'une clé ne soit jamais sauvegardée),
 * et un effacement des réglages d'IA ne touche à rien d'autre.
 *
 * [ia]: docs/05-ia.md
 */
private const val AI_PREFERENCES_FILE = "ai_settings"

private const val AI_PREFERENCES = "ai"
