package app.hexaphore.data.diary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexaphore.core.database.HexaphoreDatabase
import app.hexaphore.core.testing.FixedClock
import app.hexaphore.domain.diary.DiaryRepository
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Le même contrat, joué sur Room.
 *
 * Le côté que rien n'éprouvait depuis ce module : `RoomDiaryRepository` n'avait aucun
 * test, et ce qui passe par lui sans passer par le faux — une transaction, une
 * cascade, un `ORDER BY`, un `REAL` qui peut être `NULL` — n'était vérifié nulle part.
 *
 * Sous Robolectric, donc sans appareil : un test qu'il faut brancher un téléphone pour
 * exécuter est un test qu'on n'exécute pas ([D35][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RoomDiaryRepositoryTest : DiaryContract() {
    private val bases = mutableListOf<HexaphoreDatabase>()

    @After
    fun fermer() = bases.forEach { it.close() }

    override fun journal(): DiaryRepository {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = Room
            .inMemoryDatabaseBuilder(context, HexaphoreDatabase::class.java)
            .build()
            .also(bases::add)

        return RoomDiaryRepository(dao = base.diaryDao(), clock = FixedClock(MAINTENANT))
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
