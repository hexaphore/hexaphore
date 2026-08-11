package app.hexaphore.data.diary

import app.hexaphore.core.database.dao.FavoriteDishDao
import app.hexaphore.core.database.dao.FavoriteWithComponents
import app.hexaphore.core.database.entity.FavoriteComponentEntity
import app.hexaphore.core.database.entity.FavoriteDishEntity
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.diary.FavoriteComponent
import app.hexaphore.domain.diary.FavoriteDish
import app.hexaphore.domain.diary.FavoriteDishId
import app.hexaphore.domain.diary.FavoriteDishes
import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.SearchText
import app.hexaphore.domain.nutrition.NutrientValues
import app.hexaphore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Les plats favoris, adossés à Room.
 *
 * **La normalisation du nom se fait ici**, à l'écriture, par la même fonction que la
 * recherche d'aliments : `name_search` est ce que l'index unique compare, et le nom
 * affiché reste tel qu'il a été tapé ([D49][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Singleton
class RoomFavoriteDishes @Inject constructor(
    private val dao: FavoriteDishDao,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : FavoriteDishes {
    override fun observeAll(): Flow<List<FavoriteDish>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override suspend fun byId(id: FavoriteDishId): FavoriteDish? = withContext(dispatchers.io) {
        dao.byId(id.value)?.toDomain()
    }

    override suspend fun nameTaken(name: String, excluding: FavoriteDishId?): Boolean = withContext(dispatchers.io) {
        dao.nameTaken(SearchText.normalise(name), excluding?.value)
    }

    override suspend fun save(favorite: FavoriteDish) = withContext(dispatchers.io) {
        dao.save(favorite.toEntity(clock.now().toEpochMilli()), favorite.toComponents())
    }

    override suspend fun delete(id: FavoriteDishId) = withContext(dispatchers.io) {
        dao.delete(id.value)
    }

    override suspend fun markUsed(id: FavoriteDishId) = withContext(dispatchers.io) {
        dao.markUsed(id.value)
    }
}

/**
 * Les composants sont **triés par position** à la lecture.
 *
 * `@Relation` ne garantit aucun ordre : sans ce tri, les lignes d'un favori pourraient
 * se réordonner d'un rejeu à l'autre. Rien ne planterait, et le plat n'aurait
 * simplement plus la même tête qu'on lui connaissait.
 */
private fun FavoriteWithComponents.toDomain() = FavoriteDish(
    id = FavoriteDishId(favorite.id),
    name = favorite.name,
    useCount = favorite.useCount,
    components = components.sortedBy { it.position }.map { it.toDomain() },
)

private fun FavoriteComponentEntity.toDomain() = FavoriteComponent(
    foodId = foodId?.let(::FoodId),
    name = displayName,
    quantity = quantity,
    // La portion nommee se reconstruit depuis ce qui a ete ecrit : la fiche a pu
    // etre supprimee depuis, et le favori doit rester rejouable.
    unit = QuantityUnit.of(unit, grams, quantity),
    grams = grams,
    // Les six valeurs nullables traversent telles quelles : un `?: 0.0` ici ferait
    // disparaitre la distinction entre inconnu et zero, silencieusement.
    values = NutrientValues(
        kcal = kcal,
        protein = proteinG,
        carbs = carbG,
        sugars = sugarG,
        fat = fatG,
        fiber = fiberG,
    ),
)

private fun FavoriteDish.toEntity(now: Long) = FavoriteDishEntity(
    id = id.value,
    name = name,
    nameSearch = SearchText.normalise(name),
    useCount = useCount,
    createdAt = now,
)

/**
 * La position est l'indice dans la liste, et elle fait partie de la clé.
 *
 * C'est ce qui permet à un favori de n'avoir aucun identifiant de composant : un
 * composant n'existe pas hors de son plat et n'est jamais désigné seul.
 */
private fun FavoriteDish.toComponents(): List<FavoriteComponentEntity> = components.mapIndexed { index, component ->
    FavoriteComponentEntity(
        favoriteId = id.value,
        position = index,
        foodId = component.foodId?.value,
        displayName = component.name,
        quantity = component.quantity,
        unit = component.unit.code,
        grams = component.grams,
        kcal = component.values.kcal,
        proteinG = component.values.protein,
        carbG = component.values.carbs,
        sugarG = component.values.sugars,
        fatG = component.values.fat,
        fiberG = component.values.fiber,
    )
}
