package app.hexavore.integration.ai

import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.EstimatedFood
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Les quatre derniers fournisseurs, devant un vrai serveur.
 *
 * Une seule classe les sert, donc un seul jeu de cas — et ce qui s'y juge est ce qui
 * **ne se voit qu'ici** : le contenu qui change de forme selon qu'il y a une image, le
 * schéma qui ne part que chez celui qui le prend, et l'URL de base saisie à la main,
 * qui est le seul endroit du projet où l'utilisateur peut écrire quelque chose que
 * personne n'a validé.
 *
 * Ce qu'ils ne revérifient pas : le parseur et le prompt, éprouvés ailleurs et
 * partagés par les six fournisseurs.
 */
class OpenAiCompatibleRecognizerTest {
    private val server = MockWebServer()

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.shutdown()

    @Test
    fun `une reponse bien formee rend des lignes et le compte de jetons`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        val outcome = recognize(RecognitionInput.Text("un verre de jus d'orange"))

        val recognition = (outcome as RecognitionOutcome.Recognized).recognition
        assertEquals("jus d'orange", recognition.items.single().label)
        assertEquals(EstimatedUnit.ML, recognition.items.single().unit)
        assertEquals(PROMPT_TOKENS, recognition.usage?.input)
    }

    @Test
    fun `la cle voyage en Bearer, sur la route des completions`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"))

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer $KEY", request.getHeader("Authorization"))
    }

    @Test
    fun `une base qui porte deja son v1 n en recoit pas un second`() = runTest {
        // Le premier fournisseur dont l'URL se saisit vraiment a la main : les relais
        // s'annoncent tantot avec, tantot sans. Un second `/v1` rendrait un 404 que
        // personne ne rapporterait a cette ligne.
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"), baseUrl = server.url("/v1").toString())

        assertEquals("/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun `une description part en chaine, pas en tableau`() = runTest {
        // Les deux formes sont legales chez OpenAI, mais un relais compatible
        // n'accepte parfois que la premiere -- et c'est celui-la qu'on ne peut pas
        // tester.
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("un bol de riz"))

        val messages = corps().getValue("messages").jsonArray
        assertEquals(JsonPrimitive("un bol de riz"), messages.last().jsonObject.getValue("content"))
    }

    @Test
    fun `une photo part en data URI, avant la consigne`() = runTest {
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Photo(byteArrayOf(1, 2, 3), note = "l assiette fait 24 cm"))

        val parts = corps().getValue("messages").jsonArray.last().jsonObject
            .getValue("content").jsonArray

        // L'ordre d'un tableau veut dire quelque chose : le modele lit dans cet ordre.
        assertEquals("image_url", parts.first().jsonObject.getValue("type").jsonPrimitiveContent())
        assertEquals("text", parts.last().jsonObject.getValue("type").jsonPrimitiveContent())
        assertTrue(parts.toString().contains("data:image/jpeg;base64,AQID"), parts.toString())
        assertTrue(parts.toString().contains("24 cm"), "la note de l utilisateur doit accompagner la demande")
    }

    @Test
    fun `la consigne systeme est un message, et le premier`() = runTest {
        // La troisieme forme rencontree pour la meme idee : un champ chez Anthropic,
        // un objet a part chez Gemini, un message ici.
        server.enqueue(ok(ONE_ITEM))

        recognize(RecognitionInput.Text("une pomme"))

        val premier = corps().getValue("messages").jsonArray.first().jsonObject
        assertEquals("system", premier.getValue("role").jsonPrimitiveContent())
        assertEquals(JsonPrimitive(PROMPT), premier.getValue("content"))
    }

    @Test
    fun `le schema ne part que chez celui qui le prend`() = runTest {
        server.enqueue(ok(ONE_ITEM))
        recognize(RecognitionInput.Text("une pomme"), strictSchema = true)
        val avecSchema = corps().getValue("response_format").jsonObject

        server.enqueue(ok(ONE_ITEM))
        recognize(RecognitionInput.Text("une pomme"), strictSchema = false)
        val sansSchema = corps().getValue("response_format").jsonObject

        assertEquals("json_schema", avecSchema.getValue("type").jsonPrimitiveContent())
        assertTrue(avecSchema.toString().contains("additionalProperties"), avecSchema.toString())
        assertEquals("json_object", sansSchema.getValue("type").jsonPrimitiveContent())
        assertFalse(sansSchema.toString().contains("schema"), sansSchema.toString())
    }

    @Test
    fun `une reponse tronquee n est pas une reponse illisible`() = runTest {
        // `length` dit qu'il manque la fin du JSON, `content_filter` que le
        // fournisseur a decline. Ni l'un ni l'autre n'est un defaut technique.
        assertEquals(AiError.NothingRecognized, failureOn("length"))
        assertEquals(AiError.NothingRecognized, failureOn("content_filter"))
    }

    @Test
    fun `les codes deviennent les memes issues`() = runTest {
        assertEquals(AiError.InvalidKey, failure(UNAUTHORIZED))
        assertEquals(AiError.QuotaExceeded, failure(PAYMENT_REQUIRED))
        assertEquals(AiError.QuotaExceeded, failure(TOO_MANY_REQUESTS))
        assertEquals(BAD_REQUEST, (failure(BAD_REQUEST) as AiError.Server).status)
    }

    @Test
    fun `ce qu un relais inconnu repond est conserve`() = runTest {
        // D'autant plus utile ici qu'une URL saisie a la main peut atteindre
        // n'importe quoi : la page d'erreur du service vise est la seule chose qui
        // puisse le dire.
        server.enqueue(MockResponse().setResponseCode(BAD_REQUEST).setBody("""{"error":"model not found"}"""))

        val error = (recognize(RecognitionInput.Text("un jus")) as RecognitionOutcome.Failed).error

        assertTrue((error as AiError.Server).detail?.contains("model not found") == true, error.toString())
    }

    private fun corps() = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveContent(): String = (this as JsonPrimitive).content

    private suspend fun failureOn(reason: String): AiError {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"choices":[{"finish_reason":"$reason"}]}"""),
        )
        return (recognize(RecognitionInput.Text("un jus")) as RecognitionOutcome.Failed).error
    }

    private suspend fun failure(status: Int): AiError {
        server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
        return (recognize(RecognitionInput.Text("un jus")) as RecognitionOutcome.Failed).error
    }

    @Test
    fun `l estimation part avec son propre prompt et son propre schema`() = runTest {
        // L'etape 4 emprunte la meme route et la meme cle, et rien d'autre : deux
        // questions differentes ne se posent pas avec la meme consigne, et un schema
        // de reconnaissance ferait rendre des unites la ou on attend des macros.
        server.enqueue(ok(ESTIMATION))

        val foods = estimate(listOf("tofu fume au sesame", "sauce maison"))

        val corps = server.takeRequest().body.readUtf8()
        assertTrue(corps.contains(ESTIMATE_PROMPT), corps)
        assertFalse(corps.contains(PROMPT), "la consigne d extraction n a rien a faire ici")
        assertTrue(corps.contains("tofu fume au sesame"), corps)
        assertTrue(corps.contains("sauce maison"), "les libelles partent ensemble, en un seul appel")
        assertEquals(180.0, foods.single().per100g.kcal)
    }

    private suspend fun estimate(labels: List<String>): List<EstimatedFood> {
        val recognizer = OpenAiCompatibleRecognizer(
            api = openAiApi(aiClient(NetworkLog.Silent, SilentExchanges)),
            prompt = { PROMPT },
            estimatePrompt = { ESTIMATE_PROMPT },
            dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
            strictSchema = true,
        )
        val outcome = recognizer.estimate(
            labels,
            AiConfiguration(
                provider = AiProvider.OPENAI,
                apiKey = ApiKey(KEY),
                model = "gpt-5.6-luna",
                baseUrl = server.url("/").toString(),
            ),
        )
        return (outcome as EstimationOutcome.Estimated).foods
    }

    private suspend fun recognize(
        input: RecognitionInput,
        baseUrl: String = server.url("/").toString(),
        strictSchema: Boolean = true,
    ): RecognitionOutcome {
        val recognizer = OpenAiCompatibleRecognizer(
            api = openAiApi(aiClient(NetworkLog.Silent, SilentExchanges)),
            prompt = { PROMPT },
            estimatePrompt = { ESTIMATE_PROMPT },
            dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
            strictSchema = strictSchema,
        )
        return recognizer.recognize(
            input,
            AiConfiguration(
                provider = AiProvider.OPENAI,
                apiKey = ApiKey(KEY),
                model = "gpt-5.6-luna",
                baseUrl = baseUrl,
            ),
        )
    }

    private fun ok(items: String) = MockResponse().setResponseCode(200).setBody(
        """
        {
          "choices": [
            {"message": {"role": "assistant", "content": ${JsonPrimitive(items)}}, "finish_reason": "stop"}
          ],
          "usage": {"prompt_tokens": $PROMPT_TOKENS, "completion_tokens": 80}
        }
        """.trimIndent(),
    )

    private companion object {
        const val KEY = "sk-de-test"
        const val PROMPT = "Tu identifies les aliments."
        const val ESTIMATE_PROMPT = "Tu estimes des macros."
        const val PROMPT_TOKENS = 900
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val PAYMENT_REQUIRED = 402
        const val TOO_MANY_REQUESTS = 429

        val ESTIMATION = """
            {"foods":[{"label":"tofu fume au sesame","kcal":180,"protein":16}]}
        """.trimIndent()

        val ONE_ITEM = """
            {"items":[{"label":"jus d'orange","quantity":200,"unit":"ML","confidence":0.9}]}
        """.trimIndent()
    }
}
