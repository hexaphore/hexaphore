package app.hexaphore.data.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexaphore.core.database.HexaphoreDatabase
import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.core.testing.TestDispatchers
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Le même contrat, joué sur Room.
 *
 * C'est le côté que rien n'éprouvait : `RoomProfileStore` porte trois ports et n'avait
 * aucun test. Ce qui passe par lui et pas par le faux — une sérialisation, un index
 * unique, une transaction, un `ORDER BY` — n'était vérifié nulle part.
 *
 * Sous Robolectric, donc sans appareil : un test qu'il faut brancher un téléphone pour
 * exécuter est un test qu'on n'exécute pas ([D35][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RoomProfileStoreTest : ProfileStoreContract() {
    private val bases = mutableListOf<HexaphoreDatabase>()

    @After
    fun fermer() = bases.forEach { it.close() }

    override fun store(): ProfileStoreView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = Room
            .inMemoryDatabaseBuilder(context, HexaphoreDatabase::class.java)
            .build()
            .also(bases::add)

        // Le meme objet trois fois : c'est RoomProfileStore qui porte les trois ports,
        // et la vue ne fait que rappeler lequel est interroge.
        val magasin = RoomProfileStore(
            profiles = base.profileDao(),
            goalDao = base.goalDao(),
            ids = SequentialIdGenerator("pesee"),
            clock = FixedClock(MAINTENANT),
            dispatchers = TestDispatchers(Dispatchers.IO),
        )
        return ProfileStoreView(profiles = magasin, weights = magasin, goals = magasin)
    }

    private companion object {
        val MAINTENANT: Instant = Instant.parse("2026-08-10T10:00:00Z")
    }
}

/**
 * Version d'Android simulée par Robolectric.
 *
 * Choisie parmi celles que la version de Robolectric embarque, et non alignée sur
 * `compileSdk` : ces tests portent sur SQLite et sur des flux, pas sur une API dont la
 * version importerait.
 */
internal const val ROBOLECTRIC_SDK = 33
