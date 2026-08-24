package app.hexavore.integration.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * L'intercepteur devant un vrai serveur, parce que la règle a deux moitiés opposées.
 *
 * Une clé ne doit **jamais** apparaître dans ce qu'on journalise, et doit **toujours**
 * arriver au fournisseur. Un test qui ne regarde que le journal laisserait passer un
 * intercepteur qui retire l'en-tête au lieu de le masquer — l'authentification
 * échouerait, et le symptôme ressemblerait à une clé invalide, c'est-à-dire à une
 * faute de l'utilisateur.
 */
class RedactionInterceptorTest {
    private val server = MockWebServer()
    private val recorded = mutableListOf<String>()

    @BeforeEach
    fun start() {
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    }

    @AfterEach
    fun stop() = server.shutdown()

    @Test
    fun `la cle est masquee dans le journal et intacte sur le fil`() {
        call(path = "/v1/messages")

        assertFalse(recorded.single().contains(SECRET), "une cle journalisee est une cle divulguee")
        assertTrue(recorded.single().contains("x-api-key: ***"), "l en-tete doit se voir, masque")
        assertEquals(SECRET, server.takeRequest().getHeader("x-api-key"), "le fournisseur doit la recevoir")
    }

    @Test
    fun `la chaine de requete ne se journalise pas`() {
        // Aucun fournisseur qu'on appelle n'y met sa cle -- et c'est precisement
        // pourquoi personne ne verifierait ce point le jour ou l'un d'eux s'y met.
        call(path = "/v1/messages?key=$SECRET")

        assertFalse(recorded.single().contains(SECRET))
        assertTrue(recorded.single().contains("/v1/messages?…"), "le chemin reste lisible")
    }

    @Test
    fun `le journal nomme la methode et le chemin`() {
        call(path = "/v1/messages")

        assertTrue(recorded.single().startsWith("POST "), recorded.single())
        assertTrue(recorded.single().contains("/v1/messages"), recorded.single())
    }

    private fun call(path: String) {
        val client = OkHttpClient.Builder()
            .addInterceptor(RedactionInterceptor(recorded::add))
            .build()
        val request = Request.Builder()
            .url(server.url(path))
            .header("x-api-key", SECRET)
            .header("content-type", "application/json")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).execute().close()
    }

    private companion object {
        const val SECRET = "sk-ant-jamais-dans-un-journal"
    }
}
