package app.hexaphore.di

import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.time.Clock
import app.hexaphore.platform.DefaultDispatcherProvider
import app.hexaphore.platform.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Liaison des ports du domaine à leurs implémentations de plateforme.
 *
 * C'est ici que se joue la promesse du découpage : substituer une horloge figée
 * ou un dispatcher de test ne demande de toucher qu'à une ligne, et aucun appelant
 * ne s'en aperçoit.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds
    @Singleton
    abstract fun clock(implementation: SystemClock): Clock

    @Binds
    @Singleton
    abstract fun dispatcherProvider(implementation: DefaultDispatcherProvider): DispatcherProvider
}
