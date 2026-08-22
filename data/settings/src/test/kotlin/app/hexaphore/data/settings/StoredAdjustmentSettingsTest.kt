package app.hexaphore.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.hexaphore.core.testing.TestDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Les réponses aux suggestions, sur de vraies préférences.
 *
 * Ce qui se juge ici est la **relecture** : ce qu'on vient d'écrire ressort, et une
 * valeur illisible ne se déguise pas en réponse.
 */
@RunWith(RobolectricTestRunner::class)
class StoredAdjustmentSettingsTest {
    private val preferences = ApplicationProvider
        .getApplicationContext<Context>()
        .getSharedPreferences("adjustment-test", Context.MODE_PRIVATE)

    private val settings = StoredAdjustmentSettings(preferences, TestDispatchers(UnconfinedTestDispatcher()))

    @Test
    fun `sans rien, l adaptation est active et n a jamais repondu`() = runTest {
        val setup = settings.observe().first()

        assertTrue("l'adaptation est ce qui fait qu'un objectif reste juste", setup.enabled)
        assertNull(setup.lastAcceptedOn)
        assertNull(setup.lastIgnoredOn)
    }

    @Test
    fun `un ajustement accepte se relit`() = runTest {
        settings.accepted(LUNDI)

        assertEquals(LUNDI, settings.observe().first().lastAcceptedOn)
    }

    @Test
    fun `un refus n est pas un ajustement accepte`() = runTest {
        // Les deux imposent le meme silence, mais ce ne sont pas les memes faits :
        // les confondre ferait dire a l'historique qu'un objectif a ete corrige un
        // jour ou l'utilisateur avait refuse.
        settings.ignored(LUNDI)

        val setup = settings.observe().first()

        assertEquals(LUNDI, setup.lastIgnoredOn)
        assertNull(setup.lastAcceptedOn)
    }

    @Test
    fun `ne plus proposer eteint l adaptation sans effacer les reponses`() = runTest {
        settings.accepted(LUNDI)
        settings.stop()

        val setup = settings.observe().first()

        assertFalse(setup.enabled)
        assertEquals(LUNDI, setup.lastAcceptedOn)
    }

    @Test
    fun `le flux se dement apres une ecriture`() = runTest {
        settings.ignored(LUNDI)
        settings.ignored(MARDI)

        assertEquals(MARDI, settings.observe().first().lastIgnoredOn)
    }

    @Test
    fun `une date illisible vaut aucune date`() = runTest {
        // Et non aujourd'hui : une valeur corrompue ne doit ni faire taire l'adaptation
        // pour deux semaines, ni la faire parler alors qu'on venait de repondre.
        preferences.edit().putString("adjustment.last_ignored", "hier matin").commit()

        assertNull(
            StoredAdjustmentSettings(preferences, TestDispatchers(UnconfinedTestDispatcher()))
                .observe()
                .first()
                .lastIgnoredOn,
        )
    }

    private companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 17)
        val MARDI: LocalDate = LocalDate.of(2026, 8, 18)
    }
}
