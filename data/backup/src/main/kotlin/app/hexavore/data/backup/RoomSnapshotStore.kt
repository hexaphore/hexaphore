package app.hexavore.data.backup

import androidx.room.withTransaction
import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.database.dao.BackupReadDao
import app.hexavore.core.database.dao.BackupWriteDao
import app.hexavore.core.database.eraseUserData
import app.hexavore.data.diary.toComponents
import app.hexavore.data.diary.toDomain
import app.hexavore.data.diary.toEntity
import app.hexavore.data.food.toDomain
import app.hexavore.data.food.toEntity
import app.hexavore.data.profile.toDomain
import app.hexavore.data.profile.toEntity
import app.hexavore.domain.backup.Snapshot
import app.hexavore.domain.backup.SnapshotStore
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.goal.AdjustmentSettings
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tout ce que l'utilisateur a écrit, lu et remplacé d'un bloc.
 *
 * **Il emprunte les mappeurs des trois modules qui les portent déjà** plutôt que d'en
 * écrire un second jeu. Deux traductions de la même chose finissent par diverger, et
 * ici la divergence ne produirait pas un affichage bizarre mais une sauvegarde qui
 * écrit des lignes que l'application relit de travers. Le contrat de ce module l'éprouve
 * en écrivant par les vrais dépôts, en capturant, en effaçant, en restaurant, puis en
 * relisant par les vrais dépôts.
 *
 * **L'état de l'adaptation ne vient pas de la base** mais des préférences : c'est le
 * seul morceau du contenu utilisateur rangé ailleurs, et l'oublier ferait réapparaître
 * une carte à laquelle on venait de répondre.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
@Singleton
@Suppress("LongParameterList")
class RoomSnapshotStore @Inject constructor(
    private val database: HexavoreDatabase,
    private val reads: BackupReadDao,
    private val writes: BackupWriteDao,
    private val adjustment: AdjustmentSettings,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : SnapshotStore {
    override suspend fun capture(): Snapshot = withContext(dispatchers.io) {
        Snapshot(
            exportedAt = clock.now(),
            appVersion = APP_VERSION,
            profile = reads.profile()?.toDomain(),
            goals = reads.goals().map { it.toDomain() },
            weights = reads.weights().map { it.toDomain() },
            dishes = reads.dishes().map { it.toDomain() },
            // Sans annotations : le rayon et le titre court d'une fiche de l'ANSES se
            // relisent dans la base de reference, ils n'appartiennent pas a la copie.
            foods = reads.foods().map { it.toDomain() },
            favorites = reads.favorites().map { it.toDomain() },
            adjustment = adjustment.observe().first(),
        )
    }

    /**
     * Tout vider puis tout écrire, **en une transaction**.
     *
     * Entre les deux, la base serait vide : une lecture concurrente y verrait un
     * journal effacé, et une interruption y laisserait l'application sans rien.
     *
     * L'ordre des insertions suit les clés étrangères — les aliments avant les lignes
     * qui les citent, les plats avant leurs lignes, les favoris avant leurs composants.
     */
    override suspend fun replace(snapshot: Snapshot) = withContext(dispatchers.io) {
        val now = clock.now().toEpochMilli()

        database.withTransaction {
            database.eraseUserData()
            snapshot.profile?.let { writes.insertProfile(it.toEntity(now)) }
            writes.insertGoals(snapshot.goals.map { it.toEntity(now) })
            // L'identifiant d'une pesee n'est pas dans le domaine : une pesee se
            // reconnait a sa date, qui porte deja l'index unique. Le derive de la
            // date le rend stable d'une restauration a l'autre.
            writes.insertWeights(snapshot.weights.map { it.toEntity(id = "poids-${it.date}", now = now) })
            writes.insertFoods(snapshot.foods.map { it.toEntity(now) })
            writes.insertDishes(snapshot.dishes.map { it.toEntity(now) })
            writes.insertEntries(snapshot.dishes.flatMap { dish -> dish.entries.map { it.toEntity(now) } })
            writes.insertFavorites(snapshot.favorites.map { it.toEntity(now) })
            writes.insertComponents(snapshot.favorites.flatMap { it.toComponents() })
        }

        // **Hors de la transaction, et il n'y a pas de choix** : l'adaptation est
        // rangee dans des preferences et non dans la base. L'instantane la capture
        // depuis toujours -- elle voyage donc dans le fichier -- mais la restauration
        // la laissait tomber, faute de savoir ou la remettre. Quelqu'un qui restaurait
        // retrouvait ses repas sans son « ne plus proposer », et la carte revenait le
        // lendemain sans que rien ne l'explique.
        //
        // Apres la base et non avant : une transaction qui echoue ne doit pas laisser
        // l'adaptation d'un autre appareil sur un journal qui n'a pas bouge.
        adjustment.restore(snapshot.adjustment)
    }

    override suspend fun erase() = withContext(dispatchers.io) {
        database.withTransaction { database.eraseUserData() }
    }
}

/**
 * La version de l'application, telle qu'elle est écrite dans le fichier.
 *
 * Elle n'est là que pour la lecture humaine — [app.hexavore.domain.backup.SNAPSHOT_FORMAT_VERSION]
 * est ce qui décide de la relecture. La brancher sur `BuildConfig` ferait dépendre ce
 * module de la configuration de l'APK pour une chaîne que personne ne compare.
 */
private const val APP_VERSION = "0.4"
