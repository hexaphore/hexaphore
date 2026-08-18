package app.hexaphore.di

import android.util.Log
import app.hexaphore.BuildConfig
import app.hexaphore.domain.ai.AiSettings
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

    /**
     * Aucun fournisseur configuré — **et c'est la vérité tant que rien ne permet d'en
     * configurer un.**
     *
     * L'écran de saisie des clés et leur rangement en `EncryptedSharedPreferences`
     * arrivent à la livraison suivante. D'ici là, `null` est la réponse exacte : les
     * deux boutons IA sont visibles et grisés, ce que [D73][decisions] demande, et
     * toute analyse rendrait `AiError.NoProviderConfigured`.
     *
     * Écrire ce faux ici plutôt qu'un `TODO()` est délibéré : le graphe est complet,
     * l'application se construit, et le remplacement se fait en changeant cette seule
     * liaison.
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    @Singleton
    fun aiSettings(): AiSettings = AiSettings { null }
}

private const val NETWORK_TAG = "HexaphoreNet"
