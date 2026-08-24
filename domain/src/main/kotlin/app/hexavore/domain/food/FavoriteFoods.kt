package app.hexavore.domain.food

import kotlinx.coroutines.flow.Flow

/**
 * Les aliments épinglés.
 *
 * Distinct de [RecentFoods] parce que ce sont deux listes différentes et non deux
 * tris de la même : « récents » est constaté, « favoris » est choisi. Un aliment
 * qu'on mange tous les jours sort des récents dès qu'on l'oublie une semaine ; un
 * favori reste.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
interface FavoriteFoods {
    fun observeFavorites(): Flow<List<Food>>

    suspend fun setFavorite(id: FoodId, favorite: Boolean)
}
