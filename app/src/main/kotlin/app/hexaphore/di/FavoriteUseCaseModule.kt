package app.hexaphore.di

import app.hexaphore.domain.ai.NutritionEstimator
import app.hexaphore.domain.ai.PendingRecognition
import app.hexaphore.domain.diary.FavoriteDishes
import app.hexaphore.domain.food.FoodLookup
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.AddFoodLine
import app.hexaphore.domain.usecase.CreateDraft
import app.hexaphore.domain.usecase.GetDishDraft
import app.hexaphore.domain.usecase.GetFavoriteDraft
import app.hexaphore.domain.usecase.NextFavoriteNumber
import app.hexaphore.domain.usecase.OpenDraft
import app.hexaphore.domain.usecase.RemoveFavoriteDish
import app.hexaphore.domain.usecase.ResolveFoodLabel
import app.hexaphore.domain.usecase.ResolveRecognition
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
        pending: PendingRecognition,
        resolve: ResolveRecognition,
    ): OpenDraft = OpenDraft(dishes, favorites, create, foods, pending, resolve)

    /**
     * La résolution d'une proposition, et le résolveur de libellés qu'elle chaîne.
     *
     * Les deux ici parce qu'ils n'ont qu'un appelant — l'ouverture d'un brouillon — et
     * que `ResolveFoodLabel` n'a jamais eu de fournisseur : il attendait depuis sa
     * livraison quelqu'un pour l'appeler.
     */
    @Provides
    fun resolveRecognition(
        resolve: ResolveFoodLabel,
        create: CreateDraft,
        estimate: NutritionEstimator,
    ): ResolveRecognition = ResolveRecognition(resolve, create, estimate)

    @Provides
    fun resolveFoodLabel(foods: FoodSearch): ResolveFoodLabel = ResolveFoodLabel(foods)

    @Provides
    fun addFoodLine(foods: FoodLookup, create: CreateDraft): AddFoodLine = AddFoodLine(foods, create)

    @Provides
    fun saveFavoriteDish(favorites: FavoriteDishes, ids: IdGenerator): SaveFavoriteDish =
        SaveFavoriteDish(favorites, ids)

    /** Le premier numéro libre, pour proposer « Plat 3 » plutôt qu'une liste d'aliments. */
    @Provides
    fun nextFavoriteNumber(favorites: FavoriteDishes): NextFavoriteNumber = NextFavoriteNumber(favorites)

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
