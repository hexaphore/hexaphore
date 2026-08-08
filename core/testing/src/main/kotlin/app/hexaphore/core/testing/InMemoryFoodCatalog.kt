package app.hexaphore.core.testing

import app.hexaphore.domain.food.CustomFoodStore
import app.hexaphore.domain.food.FavoriteFoods
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.food.FoodUsage
import app.hexaphore.domain.food.RecentFoods
import app.hexaphore.domain.food.SearchText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Le catalogue d'aliments en mémoire.
 *
 * Ce n'est pas une béquille de test : c'est la **première implémentation** des cinq
 * ports du catalogue, celle contre laquelle les écrans sont écrits avant que Room
 * n'arrive. Basculer vers l'adaptateur Room ne change qu'une fonction du module
 * Hilt, et c'est cette propriété que sa présence ici entretient.
 *
 * Une seule classe pour cinq ports, alors que le domaine les sépare : la séparation
 * existe pour que **les appelants** ne dépendent que de ce qu'ils utilisent, pas
 * pour forcer cinq objets. L'adaptateur Room fait de même.
 *
 * [failure] reproduit une base illisible. Sans lui, la seule façon d'éprouver ce cas
 * serait de corrompre une vraie base.
 */
class InMemoryFoodCatalog(initial: List<Food> = emptyList(), var failure: Boolean = false) :
    FoodSearch,
    RecentFoods,
    FavoriteFoods,
    CustomFoodStore,
    FoodUsage {
    private val foods = MutableStateFlow(initial.associateBy { it.id })

    /** Ce que le catalogue contient, pour qu'un test l'affirme sans passer par un flux. */
    val all: List<Food> get() = foods.value.values.toList()

    override suspend fun search(query: String, limit: Int): List<Food> {
        failIfAsked()
        val normalised = SearchText.normalise(query)
        if (normalised.isEmpty()) return emptyList()

        return foods.value.values
            .filter { SearchText.normalise(it.name).contains(normalised) }
            // Ce que l'utilisateur mange vraiment passe devant, puis le nom court :
            // c'est le classement que le port promet, et un faux qui ordonnerait
            // autrement laisserait passer un ecran qui compte dessus.
            .sortedWith(compareByDescending<Food> { it.useCount }.thenBy { it.name.length })
            .take(limit)
    }

    override fun observeRecent(limit: Int): Flow<List<Food>> = foods.map { catalogue ->
        catalogue.values
            .filter { it.lastUsedAt != null }
            .sortedByDescending { it.lastUsedAt }
            .take(limit)
    }

    override fun observeFavorites(): Flow<List<Food>> = foods.map { catalogue ->
        catalogue.values.filter { it.favorite }.sortedBy { it.name }
    }

    override suspend fun setFavorite(id: FoodId, favorite: Boolean) {
        failIfAsked()
        update(id) { it.copy(favorite = favorite) }
    }

    override suspend fun save(food: Food): FoodId {
        failIfAsked()
        foods.value = foods.value + (food.id to food)
        return food.id
    }

    override suspend fun delete(id: FoodId) {
        failIfAsked()
        foods.value = foods.value - id
    }

    override suspend fun usageCount(id: FoodId): Int = 0

    override suspend fun remember(foods: Collection<Food>, at: Instant) {
        failIfAsked()
        foods.forEach { food ->
            // Les valeurs d'une fiche deja connue ne sont pas reecrites : une
            // correction apportee a un aliment personnel ne doit pas etre defaite
            // par un plat qui porte encore l'ancienne version.
            val known = this.foods.value[food.id] ?: food
            this.foods.value =
                this.foods.value + (food.id to known.copy(lastUsedAt = at, useCount = known.useCount + 1))
        }
    }

    private fun update(id: FoodId, transform: (Food) -> Food) {
        val food = foods.value[id] ?: return
        foods.value = foods.value + (id to transform(food))
    }

    private fun failIfAsked() {
        if (failure) error("Catalogue illisible")
    }
}
