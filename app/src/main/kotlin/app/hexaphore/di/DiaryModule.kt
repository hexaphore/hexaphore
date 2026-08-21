package app.hexaphore.di

import android.content.Context
import android.content.SharedPreferences
import app.hexaphore.data.diary.RoomDiaryRepository
import app.hexaphore.data.diary.RoomFavoriteDishes
import app.hexaphore.data.diary.RoomFoodCitations
import app.hexaphore.data.diary.StoredFavoriteNumbering
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.FavoriteDishes
import app.hexaphore.domain.diary.FavoriteNumbering
import app.hexaphore.domain.food.FoodCitations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
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

    /**
     * Le compteur des noms proposés, dans son propre fichier de préférences.
     *
     * À part des réglages d'IA : ce n'est ni un secret ni une préférence, mais un état
     * de nommage, et effacer l'un ne doit pas remettre l'autre à zéro.
     */
    @Provides
    @Singleton
    @Named(DIARY_PREFERENCES)
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(DIARY_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun favoriteNumbering(
        @Named(DIARY_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): FavoriteNumbering = StoredFavoriteNumbering(preferences, dispatchers)

    /**
     * Le compte des citations, fourni ici alors que c'est un port du **catalogue**.
     *
     * Ce n'est pas un rangement approximatif : le compte se dérive des entrées de
     * journal, et son adaptateur vit dans ce module pour que le contrat du journal
     * puisse l'éprouver — écrire un plat, puis constater que le compte a bougé. Il a
     * vécu sur le catalogue, où le faux ne pouvait l'exposer qu'en carte posée à la
     * main ([D71][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    fun foodCitations(citations: RoomFoodCitations): FoodCitations = citations
}

/** Un fichier à part : les réglages d'IA ne rangent pas les noms de plats. */
private const val DIARY_PREFERENCES_FILE = "diary"

private const val DIARY_PREFERENCES = "diary"
