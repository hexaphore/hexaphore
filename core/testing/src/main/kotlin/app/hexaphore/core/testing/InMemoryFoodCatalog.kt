package app.hexaphore.core.testing

import app.hexaphore.domain.food.FavoriteFoods
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodFilter
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodLookup
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.food.FoodStore
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
 * Ce n'est pas une béquille de test : c'est la **première implémentation** des six
 * ports du catalogue, celle contre laquelle les écrans sont écrits avant que Room
 * n'arrive. Basculer vers l'adaptateur Room ne change qu'une fonction du module
 * Hilt, et c'est cette propriété que sa présence ici entretient.
 *
 * Une seule classe pour six ports, alors que le domaine les sépare : la séparation
 * existe pour que **les appelants** ne dépendent que de ce qu'ils utilisent, pas
 * pour forcer cinq objets. L'adaptateur Room fait de même.
 *
 * **Deux réserves, parce que le vrai en a deux.** [initial] est le catalogue écrit ;
 * [reference] joue la table de l'ANSES, que la recherche propose sans l'avoir
 * copiée. C'est la distinction qui manquait : un faux qui ne rendait que des fiches
 * **déjà écrites** était plus indulgent que Room, qui en fabrique qui n'y sont pas
 * encore — et deux défauts livrés sont passés par ce trou ([D53][decisions]).
 *
 * [failure] reproduit une base illisible. Sans lui, la seule façon d'éprouver ce cas
 * serait de corrompre une vraie base.
 *
 * [decisions]: docs/11-decisions.md
 */
class InMemoryFoodCatalog(
    initial: List<Food> = emptyList(),
    private val reference: List<Food> = emptyList(),
    var failure: Boolean = false,
) : FoodSearch,
    FoodLookup,
    RecentFoods,
    FavoriteFoods,
    FoodStore,
    FoodUsage {
    private val foods = MutableStateFlow(initial.associateBy { it.id })

    /** Ce que le catalogue contient, pour qu'un test l'affirme sans passer par un flux. */
    val all: List<Food> get() = foods.value.values.toList()

    /** Combien d'identifiants provisoires ont été distribués, pour qu'ils diffèrent. */
    private var provisional = 0

    /**
     * Un flux, comme le vrai : le catalogue change sous les yeux de qui regarde ses
     * résultats, et une lecture unique ne peut pas se démentir.
     */
    override fun search(query: String, filter: FoodFilter, limit: Int): Flow<List<Food>> = foods.map { catalogue ->
        failIf(failure)
        val normalised = SearchText.normalise(query)
        if (normalised.isEmpty() && filter.isEmpty) return@map emptyList()

        val stored = catalogue.values.filter { it.matches(normalised) && filter.matches(it) }
        // Une qualite demandee ecarte la table de reference, comme dans le vrai : une
        // ligne qui n'a pas ete versee au catalogue n'est ni personnelle ni epinglee.
        val proposed = if (filter.traits.isEmpty()) {
            reference.notYetCopied(stored, normalised, filter) { FoodId("provisoire-${++provisional}") }
        } else {
            emptyList()
        }
        (stored + proposed)
            // Ce que l'utilisateur mange vraiment passe devant, puis le nom court :
            // c'est le classement que le port promet, et un faux qui ordonnerait
            // autrement laisserait passer un ecran qui compte dessus.
            .sortedWith(compareByDescending<Food> { it.useCount }.thenBy { it.name.length })
            .take(limit)
    }

    override suspend fun byId(id: FoodId): Food? = foods.value[id]

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
        failIf(failure)
        foods.replace(id) { it.copy(favorite = favorite) }
    }

    override suspend fun place(food: Food): Food {
        failIf(failure)
        // Par la reference plutot que par l identifiant, comme le vrai : le
        // provisoire change a chaque recherche, alors que (source, source_ref)
        // designe la meme chose pour toujours. La fiche deja connue gagne, parce
        // qu elle porte ses compteurs d usage et les corrections qu elle a recues.
        foods.value.matching(food)?.let { return it }
        foods.value = foods.value + (food.id to food)
        return food
    }

    override suspend fun save(food: Food): FoodId {
        failIf(failure)
        foods.value = foods.value + (food.id to food)
        return food.id
    }

    override suspend fun delete(id: FoodId) {
        failIf(failure)
        foods.value = foods.value - id
    }

    /** Les entrees de journal ne sont pas ici : c est [InMemoryDiaryRepository] qui les tient. */
    var usages: Map<FoodId, Int> = emptyMap()

    override suspend fun usageCount(id: FoodId): Int = usages[id] ?: 0

    override suspend fun remember(foods: Collection<Food>, at: Instant) {
        failIf(failure)
        foods.forEach { food ->
            // Les valeurs d'une fiche deja connue ne sont pas reecrites : une
            // correction apportee a un aliment personnel ne doit pas etre defaite
            // par un plat qui porte encore l'ancienne version.
            val stored = place(food)
            this.foods.replace(stored.id) { it.copy(lastUsedAt = at, useCount = it.useCount + 1) }
        }
    }
}

/**
 * Les fiches de la table de référence qui n'ont pas encore de copie.
 *
 * Chacune reçoit un identifiant **provisoire, différent à chaque recherche** —
 * c'est exactement ce que fait l'adaptateur Room, et c'est la propriété dont dépend
 * tout ce qui suit : un appelant qui garde cet identifiant garde une chose qui ne
 * désigne rien.
 */
private fun List<Food>.notYetCopied(
    stored: List<Food>,
    normalisedQuery: String,
    filter: FoodFilter,
    provisionalId: () -> FoodId,
): List<Food> {
    val copied = stored.mapNotNullTo(mutableSetOf()) { it.reference }
    return filter { it.matches(normalisedQuery) && it.reference !in copied && filter.matches(it) }
        .map { it.copy(id = provisionalId()) }
}

/**
 * La fiche déjà écrite qui désigne la même chose, s'il y en a une.
 *
 * Par la référence plutôt que par l'identifiant, **comme le vrai** : le provisoire
 * change à chaque recherche, alors que `(source, source_ref)` désigne la même chose
 * pour toujours.
 */
private fun Map<FoodId, Food>.matching(food: Food): Food? {
    val reference = food.reference ?: return this[food.id]
    return values.firstOrNull { it.reference == reference }
}

/** Remplace une fiche en place, ou ne fait rien si elle n'est pas là. */
private fun MutableStateFlow<Map<FoodId, Food>>.replace(id: FoodId, transform: (Food) -> Food) {
    val food = value[id] ?: return
    value = value + (id to transform(food))
}

private fun Food.matches(normalisedQuery: String) = SearchText.normalise(name).contains(normalisedQuery)

/** Le couple qui désigne une fiche pour toujours, quand elle en a un. */
private val Food.reference: Pair<FoodSource, String>? get() = sourceRef?.let { source to it }

/** Reproduit une base illisible, pour que ce cas s'éprouve sans en corrompre une vraie. */
private fun failIf(failure: Boolean) {
    if (failure) error("Catalogue illisible")
}
