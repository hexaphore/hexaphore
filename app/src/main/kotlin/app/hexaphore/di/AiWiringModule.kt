package app.hexaphore.di

import android.util.Log
import app.hexaphore.BuildConfig
import app.hexaphore.integration.ai.NetworkLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Les deux choses que `:integration:ai` attend de l'application, et qu'il ne peut pas
 * savoir lui-même.
 *
 * @see docs/05-ia.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AiWiringModule {
    /**
     * Le journal réseau, **et le fait qu'il n'existe qu'en `debug`**.
     *
     * [docs/05][ia] veut que les journaux détaillés soient compilés dans la seule
     * variante `debug`. La variante est une propriété de l'application, pas du module
     * d'intégration : c'est donc ici que le choix se fait, et
     * `NetworkLog.Silent` est ce que reçoit une release.
     *
     * L'intercepteur de redaction s'applique quand même dans les deux cas. Il ne
     * protège rien tant que rien ne journalise — mais le jour où quelqu'un branche un
     * journal ici, il n'a pas à se souvenir d'expurger quoi que ce soit.
     *
     * [ia]: docs/05-ia.md
     */
    @Provides
    @Singleton
    fun networkLog(): NetworkLog = if (BuildConfig.DEBUG) NetworkLog { Log.d(NETWORK_TAG, it) } else NetworkLog.Silent
}

private const val NETWORK_TAG = "HexaphoreNet"
