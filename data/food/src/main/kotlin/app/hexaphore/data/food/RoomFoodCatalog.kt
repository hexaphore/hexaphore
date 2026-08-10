package app.hexaphore.data.food

import app.hexaphore.core.database.ciqual.CiqualDatabase
import app.hexaphore.core.database.dao.FoodDao
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.food.FavoriteFoods
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodLookup
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.food.FoodStore
import app.hexaphore.domain.food.FoodUsage
import app.hexaphore.domain.food.RecentFoods
import app.hexaphore.domain.food.SearchText
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le catalogue d'aliments, adossé aux deux bases.
 *
 * Une classe pour six ports, alors que le domaine les sépare : la séparation existe
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
    FoodLookup,
    RecentFoods,
    FavoriteFoods,
    FoodStore,
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
     *
     * **Le flux vient du catalogue local, et lui seul rythme les ré-émissions.** Room
     * invalide sur écriture — épingler, supprimer, verser une fiche — et c'est ce qui
     * fait suivre les résultats sans qu'on relance la recherche ([D53][decisions]).
     * La table de l'ANSES, livrée en lecture seule, ne change jamais : la relire à
     * chaque invalidation coûte deux requêtes sur des libellés courts, et c'est ce
     * qui garde la fusion et le dédoublonnage justes quand une fiche vient d'être
     * copiée.
     *
     * [decisions]: docs/11-decisions.md
     */
    override fun search(query: String, limit: Int): Flow<List<Food>> {
        val normalised = SearchText.normalise(query)
        if (normalised.isBlank()) return flowOf(emptyList())

        return dao
            .observeSearch(normalised, limit)
            .map { rows -> ciqual.mergedWith(rows.map { it.toDomain() }, normalised, query, limit, ids) }
            // La fusion lit la base de l'ANSES : sans cela, ces lectures SQLite se
            // feraient sur le dispatcher de celui qui collecte, c'est-a-dire le fil
            // principal.
            .flowOn(dispatchers.io)
    }

    override suspend fun byId(id: FoodId): Food? = withContext(dispatchers.io) {
        dao.byId(id.value)?.let { it.toDomain(ciqual.servingsOf(it.source, it.sourceRef)) }
    }

    override fun observeRecent(limit: Int): Flow<List<Food>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain(ciqual.servingsOf(it.source, it.sourceRef)) } }

    override fun observeFavorites(): Flow<List<Food>> =
        dao.observeFavorites().map { rows -> rows.map { it.toDomain(ciqual.servingsOf(it.source, it.sourceRef)) } }

    override suspend fun setFavorite(id: FoodId, favorite: Boolean) = withContext(dispatchers.io) {
        dao.setFavorite(id.value, favorite, clock.now().toEpochMilli())
    }

    /**
     * Le geste qui donne une existence durable a un aliment de la table de l ANSES.
     *
     * Un resultat de recherche non encore copie porte un identifiant provisoire :
     * l ecrire ici est ce qui le rend designable par une entree de journal. Sans ce
     * passage, une ligne pointerait vers une fiche absente, et la base la refuserait.
     */
    override suspend fun place(food: Food): Food = withContext(dispatchers.io) {
        // Par la reference plutot que par l identifiant : le provisoire change a
        // chaque recherche, alors que (source, source_ref) designe la meme chose
        // pour toujours. Chercher par identifiant recopierait l aliment a chaque
        // fois qu on le choisit.
        val reference = food.sourceRef
        val known = when {
            reference != null -> dao.byReference(food.source.name, reference)
            else -> dao.byId(food.id.value)
        }
        known?.let { return@withContext it.toDomain(ciqual.servingsOf(it.source, it.sourceRef)) }

        dao.upsert(food.toEntity(clock.now().toEpochMilli()))
        food
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
            val stored = place(food)
            dao.markUsed(stored.id.value, at.toEpochMilli())
        }
    }
}

/**
 * Les lignes de l'ANSES que le catalogue n'a pas encore copiées, ajoutées et classées.
 *
 * Hors de la classe pour la même raison que [servingsOf] juste dessous : c'est une
 * lecture de la base embarquée, pas une des six capacités que le catalogue expose.
 *
 * Chaque ligne non copiée reçoit un identifiant **provisoire**, régénéré à chaque
 * appel. Il ne désigne rien tant que [FoodStore.place] n'a pas été appelé, et c'est
 * volontaire : copier les 3 484 lignes à l'installation gonflerait la base, les
 * sauvegardes et la recherche avec 99 % de contenu jamais utilisé ([D51][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private fun CiqualDatabase.mergedWith(
    local: List<Food>,
    normalised: String,
    query: String,
    limit: Int,
    ids: IdGenerator,
): List<Food> {
    val alreadyCopied = local.mapNotNullTo(mutableSetOf()) { it.sourceRef.takeIf { _ -> it.isFromCiqual } }

    val fromCiqual = search(normalised, limit)
        .filterNot { it.code in alreadyCopied }
        .map { row -> row.toDomain(FoodId(ids.next()), servings(row.code)) }

    return FoodRanking.sort(local + fromCiqual, query).take(limit)
}

/**
 * Les portions d'une fiche viennent de la table de l'ANSES, quand elle en vient.
 *
 * Hors de la classe : c'est une lecture de la base embarquée, pas une des six
 * capacités que le catalogue expose.
 */
private fun CiqualDatabase.servingsOf(source: String, reference: String?) =
    if (source == FoodSource.CIQUAL.name && reference != null) {
        servings(reference).map { it.toDomain() }
    } else {
        emptyList()
    }

private val Food.isFromCiqual: Boolean get() = source == FoodSource.CIQUAL
