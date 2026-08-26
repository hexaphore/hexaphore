package app.hexavore.integration.ai

import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.CatalogueTool
import app.hexavore.domain.ai.FoodCandidate
import app.hexavore.domain.ai.LabelCandidates
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.TokenUsage
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * La même boucle, dans l'autre dialecte.
 *
 * **Le même jeu de cas que pour Anthropic, et c'est délibéré.** Ce qui change entre les
 * deux fournisseurs est la forme du fil ; ce qui ne doit pas changer est le
 * comportement. Deux jeux différents auraient laissé l'un des deux dériver sans que rien
 * ne le dise — c'est le raisonnement des contrats partagés, appliqué à un protocole.
 *
 * Trois écarts s'éprouvent en plus, parce qu'ils sont propres à Gemini : l'appel arrive
 * dans une *part*, la réponse d'outil repart comme un **objet** et non une chaîne, et
 * les déclarations sont enveloppées.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeminiToolingTest {
    private val server = MockWebServer()
    private val demandes = mutableListOf<List<String>>()

    @BeforeEach
    fun ouvrir() = server.start()

    @AfterEach
    fun fermer() = server.shutdown()

    @Test
    fun `le modele cherche, puis rend le repas`() = runTest {
        server.enqueue(appelDeRecherche("abricot"))
        server.enqueue(reponseFinale("abricot" to "13039"))

        val outcome = analyser()

        assertEquals(listOf(listOf("abricot")), demandes)
        assertEquals(ABRICOT.id, outcome.recognized().items.single().chosen?.id)
    }

    @Test
    fun `une reference inventee ne rejoint aucune fiche`() = runTest {
        server.enqueue(appelDeRecherche("abricot"))
        server.enqueue(reponseFinale("abricot" to "99999"))

        assertNull(analyser().recognized().items.single().chosen)
    }

    @Test
    fun `une reference absente laisse la ligne a l estimation`() = runTest {
        server.enqueue(appelDeRecherche("abricot"))
        server.enqueue(reponseFinale("abricot" to null))

        assertNull(analyser().recognized().items.single().chosen)
    }

    @Test
    fun `le modele peut rendre le repas sans chercher`() = runTest {
        server.enqueue(reponseFinale("abricot" to null))

        assertTrue(analyser() is RecognitionOutcome.Recognized)
        assertEquals(emptyList<List<String>>(), demandes)
    }

    @Test
    fun `la reponse d outil repart comme un objet`() = runTest {
        // L'ecart le plus facile a manquer : Gemini attend un objet la ou Anthropic
        // attend une chaine. Envoyer du texte echouerait a la validation du schema, et
        // l'erreur ressemblerait a une cle refusee.
        server.enqueue(appelDeRecherche("abricot"))
        server.enqueue(reponseFinale("abricot" to "13039"))

        analyser()

        server.takeRequest()
        val second = server.takeRequest().body.readUtf8()
        assertTrue(second.contains("\"functionResponse\""), second.take(400))
        assertTrue(
            second.contains(""""response":{"resultats""""),
            "le contenu doit voyager structure et non comme une chaine, or ${second.take(400)}",
        )
    }

    @Test
    fun `les outils partent enveloppes dans functionDeclarations`() = runTest {
        server.enqueue(reponseFinale("abricot" to null))

        analyser()

        val premier = server.takeRequest().body.readUtf8()
        assertTrue(premier.contains("functionDeclarations"), premier.take(400))
        assertTrue(premier.contains(TOOL_SEARCH), premier.take(400))
    }

    @Test
    fun `les candidats partent avec leurs valeurs pour cent grammes`() = runTest {
        // C'est ce qui permet au modele d'ecarter un jus sans deviner : sans les
        // teneurs, il choisirait sur le seul libelle, donc sur la meme information que
        // le score qu'on remplace.
        server.enqueue(appelDeRecherche("abricot"))
        server.enqueue(reponseFinale("abricot" to "13039"))

        analyser()

        server.takeRequest()
        val second = server.takeRequest().body.readUtf8()
        assertTrue(second.contains("pour_100g"), second.take(400))
        assertTrue(second.contains("13039"), second.take(400))
    }

    @Test
    fun `aucun schema de sortie ne part`() = runTest {
        // La combinaison avec l'outillage n'est documentee nulle part : la boucle s'en
        // passe, et la reponse arrive de toute facon par un appel d'outil.
        server.enqueue(reponseFinale("abricot" to null))

        analyser()

        assertTrue(!server.takeRequest().body.readUtf8().contains("responseSchema"))
    }

    @Test
    fun `chaque reference rejoint sa propre ligne`() = runTest {
        // Une assiette a plusieurs aliments est le cas ordinaire, et c'est le libelle qui
        // relie une reference a sa ligne : le parseur commun ecarte les lignes sans
        // quantite, donc les positions ne correspondent deja plus.
        server.enqueue(appelDeRecherche("abricot", "amande"))
        server.enqueue(reponseFinale("abricot" to "13039", "amande" to "15004"))

        val items = analyser().recognized().items

        assertEquals(ABRICOT.id, items.first { it.label == "abricot" }.chosen?.id)
        assertEquals(AMANDE.id, items.first { it.label == "amande" }.chosen?.id)
    }

    @Test
    fun `les jetons de tous les tours s additionnent`() = runTest {
        // Chaque tour renvoie toute la conversation et se paie : ne compter que le
        // dernier annoncerait une fraction de la facture.
        server.enqueue(appelDeRecherche("abricot", jetons = 100 to 20))
        server.enqueue(reponseFinale("abricot" to "13039", jetons = 300 to 40))

        assertEquals(TokenUsage(input = 400, output = 60), analyser().recognized().usage)
    }

    @Test
    fun `un tour muet rend le total inconnu`() = runTest {
        // Une somme partielle presentee comme le total serait un chiffre faux : inconnu
        // se dit, et l'appel sera compte sans ses jetons.
        server.enqueue(appelDeRecherche("abricot"))
        server.enqueue(reponseFinale("abricot" to "13039", jetons = 300 to 40))

        assertNull(analyser().recognized().usage)
    }

    @Test
    fun `une reponse en texte est un echec`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"Je vois un abricot."}]}}]}"""),
        )

        val outcome = analyser()

        assertEquals(AiError.NothingRecognized, (outcome as RecognitionOutcome.Failed).error)
    }

    @Test
    fun `des recherches sans fin s arretent`() = runTest {
        repeat(6) { server.enqueue(appelDeRecherche("abricot")) }

        assertTrue(analyser() is RecognitionOutcome.Failed)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `une cle refusee sort de la boucle`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"nope"}"""))

        assertTrue(analyser() is RecognitionOutcome.Failed)
        assertEquals(1, server.requestCount)
    }

    private suspend fun analyser(): RecognitionOutcome = GeminiRecognizer(
        api = geminiApi(aiClient(NetworkLog.Silent, SilentExchanges)),
        prompts = AiPrompts(extract = { "x" }, estimate = { "x" }, deep = { "consigne" }),
        dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
    ).deepRecognize(
        input = RecognitionInput.Text("un abricot"),
        configuration = AiConfiguration(
            provider = AiProvider.GEMINI,
            apiKey = ApiKey("cle-de-test"),
            model = "gemini-3.7-flash",
            baseUrl = server.url("/").toString(),
            deepAnalysis = true,
        ),
        catalogue = CatalogueTool { labels ->
            demandes += labels
            labels.map { LabelCandidates(it, listOfNotNull(CATALOGUE[it])) }
        },
    )

    private fun RecognitionOutcome.recognized() = (this as RecognitionOutcome.Recognized).recognition

    private fun appelDeRecherche(vararg labels: String, jetons: Pair<Int, Int>? = null) = MockResponse().setBody(
        """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"$TOOL_SEARCH",""" +
            """"args":{"libelles":[${labels.joinToString(",") { "\"$it\"" }}]}}}]}}]""" +
            jetons.asUsage() + "}",
    )

    private fun reponseFinale(vararg lignes: Pair<String, String?>, jetons: Pair<Int, Int>? = null) =
        MockResponse().setBody(
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"$TOOL_SUBMIT",""" +
                """"args":{"items":[${lignes.joinToString(",") { it.asLine() }}]}}}]}}]""" +
                jetons.asUsage() + "}",
        )

    private fun Pair<Int, Int>?.asUsage(): String = this?.let {
        ""","usageMetadata":{"promptTokenCount":${it.first},"candidatesTokenCount":${it.second}}"""
    }.orEmpty()

    private fun Pair<String, String?>.asLine(): String =
        """{"label":"$first","quantity":100,"unit":"G","confidence":0.9""" +
            second?.let { ""","reference":"$it"""" }.orEmpty() + "}"

    private companion object {
        val ABRICOT = Food(
            id = FoodId("id-abricot"),
            source = FoodSource.CIQUAL,
            sourceRef = "13039",
            name = "Abricot, pulpe, cru",
            per100g = NutrientValues(kcal = 48.0, protein = 0.9, carbs = 9.0, sugars = 8.6, fat = 0.3, fiber = 2.0),
        )

        val AMANDE = Food(
            id = FoodId("id-amande"),
            source = FoodSource.CIQUAL,
            sourceRef = "15004",
            name = "Amande, sans peau",
            per100g = NutrientValues(kcal = 632.0, protein = 22.6, carbs = 6.4, sugars = 4.3, fat = 53.4, fiber = 12.0),
        )

        /**
         * Ce que le catalogue propose, chaque libelle ayant sa fiche.
         *
         * Une seule fiche pour tous les libelles laisserait passer une reference reliee
         * a la mauvaise ligne, puisque toutes les lignes tomberaient sur la meme.
         */
        val CATALOGUE = mapOf(
            "abricot" to FoodCandidate("13039", ABRICOT),
            "amande" to FoodCandidate("15004", AMANDE),
        )
    }
}
