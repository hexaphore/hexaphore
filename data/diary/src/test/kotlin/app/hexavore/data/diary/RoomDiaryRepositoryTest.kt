package app.hexavore.data.diary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.database.entity.FoodEntity
import app.hexavore.core.testing.FixedClock
import app.hexavore.domain.food.FoodId
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
    private val bases = mutableListOf<HexavoreDatabase>()

    @After
    fun fermer() = bases.forEach { it.close() }

    override fun open(): OpenJournal {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = Room
            .inMemoryDatabaseBuilder(context, HexavoreDatabase::class.java)
            .build()
            .also(bases::add)

        return OpenJournal(
            diary = RoomDiaryRepository(
                dao = base.diaryDao(),
                calendar = base.calendarDao(),
                favorites = base.favoriteDishDao(),
                clock = FixedClock(MAINTENANT),
            ),
            citations = RoomFoodCitations(base.foodCitationsDao()),
            // La cle etrangere de food_entry.food_id refuse une citation vers rien :
            // la fiche doit exister avant le plat qui la cite.
            givenFood = { base.foodDao().upsert(fiche(it)) },
        )
    }

    /** La fiche la plus dépouillée que la table accepte : seul son identifiant compte ici. */
    private fun fiche(id: FoodId) = FoodEntity(
        id = id.value,
        source = "CUSTOM",
        sourceRef = null,
        name = id.value,
        nameSearch = id.value,
        brand = null,
        kcal100 = null,
        protein100 = null,
        carb100 = null,
        sugar100 = null,
        fat100 = null,
        fiber100 = null,
        saturatedFat100 = null,
        salt100 = null,
        defaultServingG = null,
        isLiquid = null,
        fetchedAt = null,
        lastUsedAt = null,
        useCount = 0,
        isFavorite = false,
        createdAt = MAINTENANT.toEpochMilli(),
        updatedAt = MAINTENANT.toEpochMilli(),
    )

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
