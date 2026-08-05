package app.hexaphore.core.common.di

import app.hexaphore.core.common.concurrency.DefaultDispatcherProvider
import app.hexaphore.core.common.time.SystemClock
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.time.Clock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Liaison des ports de plateforme à leurs implémentations.
 *
 * Ce module vit dans `:core:common` et non dans `:app` : chaque module Gradle
 * expose ses propres liaisons, et `:app` ne fait qu'assembler. Un test qui veut
 * une horloge figée remplace ce module entier plutôt que d'aller chercher une
 * ligne au milieu du graphe applicatif.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds
    @Singleton
    abstract fun clock(implementation: SystemClock): Clock

    @Binds
    @Singleton
    abstract fun dispatcherProvider(implementation: DefaultDispatcherProvider): DispatcherProvider
}
