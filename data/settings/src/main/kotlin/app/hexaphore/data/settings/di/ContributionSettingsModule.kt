package app.hexaphore.data.settings.di

import android.content.Context
import android.content.SharedPreferences
import app.hexaphore.data.settings.SecretCipher
import app.hexaphore.data.settings.StoredContributionSettings
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.food.ContributionSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Le compte Open Food Facts, et **son propre fichier**.
 *
 * Un module à lui, sorti de [AiSettingsModule] : ce sont deux services sans rapport, et
 * effacer ses réglages d'IA ne doit pas déconnecter le compte de contribution. Le
 * fichier séparé disait déjà cela ; le module le dit maintenant aussi, et c'est le
 * découpage que le seuil de longueur a rendu nécessaire — un module de réglages par
 * sujet rangé, plutôt qu'un module de tous les réglages.
 *
 * Il emprunte le chiffrement à [AiSettingsModule], qui le fournit : c'est la clé d'API
 * qui l'a rendu nécessaire, et le mot de passe s'en sert au même titre.
 *
 * @see docs/04-sources-de-donnees.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ContributionSettingsModule {
    @Provides
    @Singleton
    @Named(CONTRIBUTION_PREFERENCES)
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(CONTRIBUTION_PREFERENCES_FILE, Context.MODE_PRIVATE)

    /**
     * L'instance concrete, exposee comme telle a cote de son interface.
     *
     * Le meme decoupage qu'en [AiSettingsModule] : `ErasureModule` a besoin du type
     * reel, qui sait remettre son flux d'accord avec le disque.
     */
    @Provides
    @Singleton
    fun stored(
        @Named(CONTRIBUTION_PREFERENCES) preferences: SharedPreferences,
        cipher: SecretCipher,
        dispatchers: DispatcherProvider,
    ): StoredContributionSettings = StoredContributionSettings(preferences, cipher, dispatchers)

    @Provides
    fun contribution(stored: StoredContributionSettings): ContributionSettings = stored
}

/**
 * Le fichier du compte Open Food Facts.
 *
 * Distinct de celui des clés d'IA : les deux portent un secret, mais pas le secret du
 * même service, et on doit pouvoir oublier l'un sans perdre l'autre ([D86][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private const val CONTRIBUTION_PREFERENCES_FILE = "contribution_settings"

/**
 * Le qualifiant de ce fichier de préférences.
 *
 * `internal` et non `private` : `ErasureModule` les injecte tous les trois pour les
 * vider d'un geste, et lui passer des noms plutôt que des instances l'aurait obligé à
 * les réécrire — un fichier renommé ici et pas là ne survivrait à l'effacement qu'en
 * silence.
 */
internal const val CONTRIBUTION_PREFERENCES = "contribution"
