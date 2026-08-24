package app.hexavore.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.TokenUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le compteur, sur de vraies préférences.
 *
 * Ce qui se juge ici est **l'accumulation** — la seule promesse du port — et la
 * relecture de ce qui a été écrit, y compris quand un modèle porte un nom que personne
 * n'a validé : c'est l'utilisateur qui le saisit.
 */
@RunWith(RobolectricTestRunner::class)
class StoredAiUsageTest {
    private val preferences = ApplicationProvider
        .getApplicationContext<Context>()
        .getSharedPreferences("usage-test", Context.MODE_PRIVATE)

    private val usage = StoredAiUsage(preferences, TestDispatchers(UnconfinedTestDispatcher()))

    @Test
    fun `les appels s additionnent`() = runTest {
        usage.record(AiProvider.ANTHROPIC, "claude-opus-5", TokenUsage(input = 900, output = 80))
        usage.record(AiProvider.ANTHROPIC, "claude-opus-5", TokenUsage(input = 100, output = 20))

        val entry = usage.observe().first().single()

        assertEquals(2, entry.calls.toLong())
        assertEquals(1000, entry.input.toLong())
        assertEquals(100, entry.output.toLong())
    }

    @Test
    fun `un appel sans jetons compte quand meme`() = runTest {
        // Le fournisseur n'a pas dit ce qu'il a compte, mais l'appel a bien eu lieu et
        // il se paiera. Annoncer zero jeton serait pire que de n'en annoncer aucun.
        usage.record(AiProvider.GEMINI, "gemini-3.7-flash", usage = null)

        val entry = usage.observe().first().single()

        assertEquals(1, entry.calls.toLong())
        assertEquals(0, entry.input.toLong())
    }

    @Test
    fun `chaque modele a son compte`() = runTest {
        // Les tarifs sont attaches aux modeles : un compte agrege ne pourrait plus se
        // convertir en argent.
        usage.record(AiProvider.ANTHROPIC, "claude-opus-5", TokenUsage(input = 10, output = 1))
        usage.record(AiProvider.ANTHROPIC, "claude-haiku-4-5", TokenUsage(input = 20, output = 2))

        val entries = usage.observe().first()

        assertEquals(2, entries.size.toLong())
        assertEquals(setOf("claude-opus-5", "claude-haiku-4-5"), entries.map { it.model }.toSet())
    }

    @Test
    fun `un modele au nom biscornu se relit`() = runTest {
        // C'est l'utilisateur qui saisit le modele : rien ne garantit qu'il ressemble a
        // ce que la table connait.
        val biscornu = "relais/local:mon-modele,v2"
        usage.record(AiProvider.COMPATIBLE, biscornu, TokenUsage(input = 5, output = 5))

        val entry = usage.observe().first().single()

        assertEquals(biscornu, entry.model)
        assertEquals(AiProvider.COMPATIBLE, entry.provider)
    }

    @Test
    fun `une cle etrangere au compteur est ignoree`() = runTest {
        // Le fichier est partage avec les cles d'API : ce qui n'est pas un compte ne
        // doit pas se lire comme un compte.
        preferences.edit().putString("anthropic.key", "sk-ant-de-test").apply()
        usage.record(AiProvider.ANTHROPIC, "claude-opus-5", TokenUsage(input = 1, output = 1))

        assertEquals(1, usage.observe().first().size.toLong())
    }

    @Test
    fun `le compte le plus lourd vient en premier`() = runTest {
        usage.record(AiProvider.ANTHROPIC, "petit", TokenUsage(input = 1, output = 1))
        usage.record(AiProvider.ANTHROPIC, "gros", TokenUsage(input = 900, output = 900))

        assertTrue(usage.observe().first().first().model == "gros")
    }
}
