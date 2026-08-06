package app.hexaphore.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexaphore.core.database.entity.DishEntity
import app.hexaphore.core.database.entity.FoodEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class DiaryDaoTest {
    private lateinit var database: HexaphoreDatabase

    @Before
    fun ouvrir() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HexaphoreDatabase::class.java,
        ).build()
    }

    @After
    fun fermer() {
        database.close()
    }

    @Test
    fun `un plat ressort avec ses lignes, dans l ordre de saisie`() = runBlocking {
        database.diaryDao().insertDishWithEntries(dish(), listOf(entry("Riz"), entry("Poulet")))

        val jour = database.diaryDao().observeDay(JOUR).first()

        assertEquals(1, jour.size)
        assertEquals(2, jour.single().entries.size)
    }

    @Test
    fun `une valeur inconnue ressort nulle et non a zero`() = runBlocking {
        // Le seul endroit ou la distinction peut se perdre sans que rien ne le
        // signale : un NOT NULL DEFAULT 0 sur ces colonnes fausserait des mois de
        // journal en silence.
        database.diaryDao().insertDishWithEntries(dish(), listOf(entry("Sauce", fiberG = null)))

        val ligne = database.diaryDao().observeDay(JOUR).first().single().entries.single()

        assertNull("des fibres non renseignees ne sont pas zero gramme de fibres", ligne.fiberG)
        assertEquals(1.0, ligne.proteinG)
    }

    @Test
    fun `une journee sans plat rend une liste vide`() = runBlocking {
        val jour = database.diaryDao().observeDay(JOUR).first()

        assertTrue("rien de note ne doit pas produire un plat a zero", jour.isEmpty())
    }

    @Test
    fun `supprimer un plat emporte ses lignes`() = runBlocking {
        database.diaryDao().insertDishWithEntries(dish(), listOf(entry("Riz")))

        database.openHelper.writableDatabase.execSQL("DELETE FROM dish WHERE id = '$DISH_ID'")

        assertEquals(0, database.diaryDao().countDishes())
        assertTrue(database.diaryDao().observeDay(JOUR).first().isEmpty())
    }

    // --- Decor ---------------------------------------------------------------

    private fun dish() = DishEntity(
        id = DISH_ID,
        date = JOUR,
        source = "MANUAL",
        loggedAt = INSTANT,
        createdAt = INSTANT,
        updatedAt = INSTANT,
    )

    private var suivant = 0

    private fun entry(nom: String, fiberG: Double? = 2.0) = FoodEntryEntity(
        id = "ligne-${suivant++}",
        dishId = DISH_ID,
        displayName = nom,
        quantity = 100.0,
        unit = "g",
        grams = 100.0,
        kcal = 120.0,
        proteinG = 1.0,
        carbG = 20.0,
        sugarG = 3.0,
        fatG = 4.0,
        fiberG = fiberG,
        createdAt = INSTANT,
        updatedAt = INSTANT,
    )

    private companion object {
        const val JOUR = "2026-03-15"
        const val DISH_ID = "plat-1"
        const val INSTANT = 1_773_000_000_000L
    }
}
