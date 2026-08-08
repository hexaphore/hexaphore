package app.hexaphore.data.food

import app.hexaphore.core.database.ciqual.CiqualDatabase
import app.hexaphore.core.database.dao.FoodDao
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.food.CustomFoodStore
import app.hexaphore.domain.food.FavoriteFoods
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.food.FoodUsage
import app.hexaphore.domain.food.RecentFoods
import app.hexaphore.domain.food.SearchText
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le catalogue d'aliments, adossé aux deux bases.
 *
 * Une classe pour cinq ports, alors que le domaine les sépare : la séparation existe
 * pour que **les appelants** ne dépendent que de ce qu'ils utilisent ([docs/06][archi]),
 * pas pour forcer cinq objets. L'écran de recherche ne voit que [FoodSearch], et son
 * test n'a donc pas besoin d'un faux à quinze méthodes.
 *
 * **Une seule instance.** L'ouverture de la base de l'ANSES recopie un asset de
 * 824 Ko au premier appel ; cinq instances feraient cinq copies concurrentes du
 * même fichier.
 *
 * [archi]: docs/06-architecture.md
 */
@Singleton
class RoomFoodCatalog @Inject constructor(
    private val dao: FoodDao,
    private val ciqual: CiqualDatabase,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : FoodSearch,
    RecentFoods,
    FavoriteFoods,
    CustomFoodStore,
    FoodUsage {
    /**
     * Le catalogue local d'abord, la table de l'ANSES ensuite, puis un seul tri.
     *
     * **Un aliment déjà copié n'apparaît pas deux fois.** Une fiche du catalogue qui
     * porte un code CIQUAL est la version vivante de la ligne de l'ANSES : c'est
     * elle qui compte les usages et porte les corrections, donc c'est elle qui reste.
     *
     * Les deux lectures demandent chacune [limit] résultats, et le tri final en
     * garde [limit]. Prendre moins de chaque côté ferait dépendre le résultat de la
     * provenance : une recherche qui rend dix aliments personnels n'a aucune raison
     * de rendre en plus dix lignes de l'ANSES moins pertinentes.
     */
    override suspend fun search(query: String, limit: Int): List<Food> = withContext(dispatchers.io) {
        val normalised = SearchText.normalise(query)
        if (normalised.isBlank()) return@withContext emptyList()

        val local = dao.search(normalised, limit).map { it.toDomain() }
        val alreadyCopied = local.mapNotNullTo(mutableSetOf()) { it.sourceRef.takeIf { _ -> it.isFromCiqual } }

        val fromCiqual = ciqual
            .search(normalised, limit)
            .filterNot { it.code in alreadyCopied }
            .map { row -> row.toDomain(FoodId(ids.next()), ciqual.servings(row.code)) }

        FoodRanking.sort(local + fromCiqual, query).take(limit)
    }

    override fun observeRecent(limit: Int): Flow<List<Food>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain(servingsOf(it.source, it.sourceRef)) } }

    override fun observeFavorites(): Flow<List<Food>> =
        dao.observeFavorites().map { rows -> rows.map { it.toDomain(servingsOf(it.source, it.sourceRef)) } }

    override suspend fun setFavorite(id: FoodId, favorite: Boolean) = withContext(dispatchers.io) {
        dao.setFavorite(id.value, favorite, clock.now().toEpochMilli())
    }

    override suspend fun save(food: Food): FoodId = withContext(dispatchers.io) {
        dao.upsert(food.toEntity(clock.now().toEpochMilli()))
        food.id
    }

    override suspend fun delete(id: FoodId) = withContext(dispatchers.io) { dao.delete(id.value) }

    override suspend fun usageCount(id: FoodId): Int = withContext(dispatchers.io) { dao.usageCount(id.value) }

    override suspend fun remember(foods: Collection<Food>, at: Instant) = withContext(dispatchers.io) {
        foods.forEach { food ->
            // La fiche n'est ecrite que si elle est absente : reecrire une fiche
            // connue defairait une correction apportee a un aliment personnel, avec
            // les valeurs qu'un brouillon ouvert depuis dix minutes porte encore.
            if (dao.byId(food.id.value) == null) dao.upsert(food.toEntity(at.toEpochMilli()))
            dao.markUsed(food.id.value, at.toEpochMilli())
        }
    }

    /** Les portions d'une fiche viennent de la table de l'ANSES, quand elle en vient. */
    private fun servingsOf(source: String, reference: String?) =
        if (source == FoodSource.CIQUAL.name && reference != null) {
            ciqual.servings(reference).map { it.toDomain() }
        } else {
            emptyList()
        }
}

private val Food.isFromCiqual: Boolean get() = source == FoodSource.CIQUAL
