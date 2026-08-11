package app.hexaphore.di

import app.hexaphore.data.diary.RoomDiaryRepository
import app.hexaphore.data.diary.RoomFavoriteDishes
import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.FavoriteDishes
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Le journal, désormais lu depuis Room.
 *
 * **C'était le critère de fin de la tranche 1**, et il est tenu : passer de
 * l'implémentation en mémoire à Room n'a changé que le corps de [diaryRepository].
 * Aucun cas d'usage, aucun ViewModel, aucun écran n'a bougé — ils ne connaissent
 * que le port.
 *
 * Le jeu de démonstration a disparu avec la bascule. L'application démarre donc sur
 * une journée vide, ce qui est le comportement exact tant que la tranche 2 n'a pas
 * apporté la saisie : afficher des plats que l'utilisateur n'a pas notés serait
 * mentir sur l'état de son journal.
 *
 * @see docs/12-plan-de-developpement.md
 */
@Module
@InstallIn(SingletonComponent::class)
object DiaryModule {
    @Provides
    @Singleton
    fun diaryRepository(implementation: RoomDiaryRepository): DiaryRepository = implementation

    /**
     * Les plats favoris, second port de ce module.
     *
     * Séparé de [DiaryRepository] parce qu'un favori n'est pas une entrée de journal :
     * l'un est un modèle réutilisable qui suit les fiches vivantes, l'autre un registre
     * d'événements qui fige ses valeurs ([D62][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    fun favoriteDishes(store: RoomFavoriteDishes): FavoriteDishes = store
}
