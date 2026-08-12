package app.hexaphore.di

import app.hexaphore.data.food.RoomBarcodeLookup
import app.hexaphore.data.food.RoomFoodCatalog
import app.hexaphore.domain.food.BarcodeLookup
import app.hexaphore.domain.food.FavoriteFoods
import app.hexaphore.domain.food.FoodLookup
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.food.FoodStore
import app.hexaphore.domain.food.FoodUsage
import app.hexaphore.domain.food.RecentFoods
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Le catalogue d'aliments : sept ports, deux adaptateurs.
 *
 * Six des sept liaisons désignent la **même instance** : `RoomFoodCatalog` porte
 * `@Singleton` et son constructeur est injecté, donc Hilt le construit lui-même. Le
 * fournir ici en plus créerait un cycle — une liaison qui se demanderait elle-même.
 *
 * Que six ports partagent un adaptateur ne les rend pas superflus : c'est du côté
 * des **appelants** que la séparation paie. L'écran de recherche ne voit que
 * [FoodSearch] et [RecentFoods], le formulaire d'aliment personnel que
 * [FoodStore], et `LogDish` que [FoodUsage] — chacun se teste avec ce qu'il
 * utilise, pas avec quinze méthodes dont il ignore l'existence.
 *
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
object FoodModule {
    @Provides
    fun foodSearch(catalog: RoomFoodCatalog): FoodSearch = catalog

    @Provides
    fun foodLookup(catalog: RoomFoodCatalog): FoodLookup = catalog

    /**
     * Le seul port du catalogue qui ne vienne pas de la même classe : voir
     * [RoomBarcodeLookup].
     */
    @Provides
    fun barcodeLookup(lookup: RoomBarcodeLookup): BarcodeLookup = lookup

    @Provides
    fun recentFoods(catalog: RoomFoodCatalog): RecentFoods = catalog

    @Provides
    fun favoriteFoods(catalog: RoomFoodCatalog): FavoriteFoods = catalog

    @Provides
    fun customFoodStore(catalog: RoomFoodCatalog): FoodStore = catalog

    @Provides
    fun foodUsage(catalog: RoomFoodCatalog): FoodUsage = catalog
}
