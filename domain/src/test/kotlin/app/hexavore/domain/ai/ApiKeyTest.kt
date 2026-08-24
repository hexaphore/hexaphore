package app.hexavore.domain.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * La fuite la plus banale qui existe, et la seule ligne qui la ferme.
 *
 * L'intercepteur de redaction couvre le réseau. Il ne couvre pas un
 * `Log.d(configuration.toString())`, ni le message d'une exception qui embarque la
 * configuration, ni un rapport de plantage — c'est-à-dire les chemins par lesquels une
 * clé fuit vraiment, parce que personne ne les a écrits exprès.
 */
class ApiKeyTest {
    @Test
    fun `une cle ne s imprime pas`() {
        assertEquals("***", ApiKey(SECRET).toString())
    }

    @Test
    fun `la configuration qui la porte ne l imprime pas non plus`() {
        // C'est le cas qui compte : personne n'ecrit ApiKey.toString() a la main,
        // mais le toString() genere d'une data class part dans un journal tout seul.
        val configuration = AiConfiguration(
            provider = AiProvider.ANTHROPIC,
            apiKey = ApiKey(SECRET),
            model = "claude-opus-5",
            baseUrl = "https://api.anthropic.com/",
        )

        assertFalse(configuration.toString().contains(SECRET), configuration.toString())
    }

    @Test
    fun `elle reste lisible pour le seul appelant qui en a besoin`() {
        assertEquals(SECRET, ApiKey(SECRET).value)
    }

    private companion object {
        const val SECRET = "sk-ant-jamais-dans-un-journal"
    }
}
