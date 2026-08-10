package app.hexaphore.di

import app.hexaphore.data.profile.RoomProfileStore
import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.profile.Profiles
import app.hexaphore.domain.profile.WeightLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Le profil, le poids et les objectifs : trois ports, un adaptateur.
 *
 * Même forme que [FoodModule], et pour la même raison : la séparation paie du côté
 * des **appelants**. L'accueil ne voit que [Goals] ; l'onboarding écrit les trois.
 *
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {
    @Provides
    fun profiles(store: RoomProfileStore): Profiles = store

    @Provides
    fun weightLog(store: RoomProfileStore): WeightLog = store

    @Provides
    fun goals(store: RoomProfileStore): Goals = store
}
