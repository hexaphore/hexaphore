package app.hexaphore.di

import app.hexaphore.domain.diary.FavoriteDishes
import app.hexaphore.domain.food.FoodLookup
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.AddFoodLine
import app.hexaphore.domain.usecase.CreateDraft
import app.hexaphore.domain.usecase.GetDishDraft
import app.hexaphore.domain.usecase.GetFavoriteDraft
import app.hexaphore.domain.usecase.OpenDraft
import app.hexaphore.domain.usecase.RemoveFavoriteDish
import app.hexaphore.domain.usecase.SaveFavoriteDish
import app.hexaphore.domain.usecase.ToggleDishFavorite
import app.hexaphore.domain.usecase.UpdateDish
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage des **plats favoris**.
 *
 * Un troisième module de cas d'usage, pour la même raison que [GoalUseCaseModule] :
 * le seuil de fonctions de detekt se franchit vite, et la réponse du projet est de
 * découper selon ce que les choses sont plutôt que de relever le seuil.
 */
@Module
@InstallIn(SingletonComponent::class)
object FavoriteUseCaseModule {
    /**
     * Les quatre entrées de l'écran de validation, en un objet.
     *
     * Ici plutôt que dans [DomainModule] parce que c'est ce module qui apporte le
     * rejeu d'un favori, et que l'ouverture d'un brouillon les rassemble toutes.
     */
    @Provides
    fun openDraft(
        dishes: GetDishDraft,
        favorites: GetFavoriteDraft,
        create: CreateDraft,
        foods: FoodLookup,
    ): OpenDraft = OpenDraft(dishes, favorites, create, foods)

    @Provides
    fun addFoodLine(foods: FoodLookup, create: CreateDraft): AddFoodLine = AddFoodLine(foods, create)

    @Provides
    fun saveFavoriteDish(favorites: FavoriteDishes, ids: IdGenerator): SaveFavoriteDish =
        SaveFavoriteDish(favorites, ids)

    @Provides
    fun toggleDishFavorite(
        drafts: GetDishDraft,
        update: UpdateDish,
        save: SaveFavoriteDish,
        remove: RemoveFavoriteDish,
    ): ToggleDishFavorite = ToggleDishFavorite(drafts, update, save, remove)

    @Provides
    fun removeFavoriteDish(favorites: FavoriteDishes): RemoveFavoriteDish = RemoveFavoriteDish(favorites)

    @Provides
    fun getFavoriteDraft(
        favorites: FavoriteDishes,
        foods: FoodLookup,
        clock: Clock,
        ids: IdGenerator,
    ): GetFavoriteDraft = GetFavoriteDraft(favorites, foods, clock, ids)
}
