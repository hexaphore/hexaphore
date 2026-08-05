package app.hexaphore.di

import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.core.testing.SampleDiary
import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.time.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Le journal, en mémoire pour l'instant.
 *
 * **C'est ici que se joue le critère de fin de la tranche 1** : passer à Room ne
 * doit changer que le corps de [diaryRepository]. Si un autre fichier doit bouger,
 * c'est que le port a fui quelque part.
 *
 * Le jeu de démonstration disparaît avec ce changement. Il n'est pas là pour faire
 * joli : il contient une ligne sans valeur de fibres, ce qui permet de vérifier sur
 * un appareil que la mention « totaux minorés » apparaît vraiment.
 *
 * @see docs/12-plan-de-developpement.md
 */
@Module
@InstallIn(SingletonComponent::class)
object DiaryModule {
    @Provides
    @Singleton
    fun diaryRepository(clock: Clock): DiaryRepository = InMemoryDiaryRepository(SampleDiary.day(clock.today()))
}
