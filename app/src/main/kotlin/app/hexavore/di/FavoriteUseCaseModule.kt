package app.hexavore.di

import app.hexavore.domain.ai.NutritionEstimator
import app.hexavore.domain.ai.PendingRecognition
import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.FavoriteDishes
import app.hexavore.domain.diary.FavoriteNumbering
import app.hexavore.domain.food.FoodLookup
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.AddFoodLine
import app.hexavore.domain.usecase.CreateDraft
import app.hexavore.domain.usecase.GetDishDraft
import app.hexavore.domain.usecase.GetFavoriteDraft
import app.hexavore.domain.usecase.NextFavoriteNumber
import app.hexavore.domain.usecase.OpenDraft
import app.hexavore.domain.usecase.RemoveFavoriteDish
import app.hexavore.domain.usecase.ResolveFoodLabel
import app.hexavore.domain.usecase.ResolveRecognition
import app.hexavore.domain.usecase.SaveFavoriteDish
import app.hexavore.domain.usecase.ToggleDishFavorite
import app.hexavore.domain.usecase.UpdateDish
import app.hexavore.domain.usecase.UpdateFavoriteDish
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
    fun nextFavoriteNumber(numbering: FavoriteNumbering, favorites: FavoriteDishes): NextFavoriteNumber =
        NextFavoriteNumber(numbering, favorites)

    /**
     * La modification d'un favori, qui touche aux deux : le modèle et la provenance
     * des plats qui le citaient.
     */
    @Provides
    fun updateFavoriteDish(favorites: FavoriteDishes, diary: DiaryRepository): UpdateFavoriteDish =
        UpdateFavoriteDish(favorites, diary)

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
