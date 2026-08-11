package app.hexaphore.data.diary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexaphore.core.database.HexaphoreDatabase
import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.TestDispatchers
import app.hexaphore.domain.diary.FavoriteDishes
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Le même contrat, joué sur Room.
 *
 * C'est ici que passent la transaction de `save`, la cascade vers les composants,
 * l'index unique sur le nom normalisé et le tri par position — c'est-à-dire tout ce
 * qu'un magasin en mémoire tient sans effort et qu'une base peut tenir autrement.
 *
 * Sous Robolectric, donc sans appareil ([D35][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RoomFavoriteDishesTest : FavoriteDishContract() {
    private val bases = mutableListOf<HexaphoreDatabase>()

    @After
    fun fermer() = bases.forEach { it.close() }

    override fun favorites(): FavoriteDishes {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = Room
            .inMemoryDatabaseBuilder(context, HexaphoreDatabase::class.java)
            .build()
            .also(bases::add)

        return RoomFavoriteDishes(
            dao = base.favoriteDishDao(),
            clock = FixedClock(MAINTENANT),
            dispatchers = TestDispatchers(Dispatchers.IO),
        )
    }

    private companion object {
        val MAINTENANT: Instant = Instant.parse("2026-08-10T10:00:00Z")
    }
}
