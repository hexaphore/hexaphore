package app.hexaphore.core.testing

import app.hexaphore.domain.diary.FavoriteDish
import app.hexaphore.domain.diary.FavoriteDishId
import app.hexaphore.domain.diary.FavoriteDishes
import app.hexaphore.domain.food.SearchText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Les plats favoris, en mémoire.
 *
 * Comme [InMemoryDiaryRepository], ce n'est pas une béquille : c'est la première
 * implémentation du port, celle contre laquelle les écrans sont écrits avant Room.
 *
 * **Il compare les noms normalisés, comme la base.** C'est la propriété qu'un faux
 * indulgent laisserait passer : une comparaison sur la chaîne brute accepterait
 * « Petit-déj » à côté de « petit dej », et le second favori ne serait refusé que sur
 * l'appareil, par l'index unique — c'est-à-dire trop tard et sans message. C'est la
 * forme exacte du défaut que [D53][decisions] a nommée.
 *
 * [decisions]: docs/11-decisions.md
 */
class InMemoryFavoriteDishes(initial: List<FavoriteDish> = emptyList(), var failure: Boolean = false) : FavoriteDishes {
    private val state = MutableStateFlow(initial.sortedByDescending { it.useCount })

    /** Ce que la liste contient, pour qu'un test l'affirme sans passer par un flux. */
    val all: List<FavoriteDish> get() = state.value

    override fun observeAll(): Flow<List<FavoriteDish>> = state.map { favorites ->
        failIfBroken()
        favorites.sortedByDescending { it.useCount }
    }

    override suspend fun byId(id: FavoriteDishId): FavoriteDish? {
        failIfBroken()
        return state.value.firstOrNull { it.id == id }
    }

    override suspend fun nameTaken(name: String, excluding: FavoriteDishId?): Boolean {
        failIfBroken()
        val normalised = SearchText.normalise(name)
        return state.value.any { it.id != excluding && SearchText.normalise(it.name) == normalised }
    }

    /** Écrit ou remplace, par identifiant : renommer ne crée pas un second favori. */
    override suspend fun save(favorite: FavoriteDish) {
        failIfBroken()
        state.value = state.value.filterNot { it.id == favorite.id } + favorite
    }

    override suspend fun delete(id: FavoriteDishId) {
        failIfBroken()
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun markUsed(id: FavoriteDishId) {
        failIfBroken()
        state.value = state.value.map { if (it.id == id) it.copy(useCount = it.useCount + 1) else it }
    }

    private fun failIfBroken() {
        if (failure) error("Favoris illisibles")
    }
}
