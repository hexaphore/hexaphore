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
 * La boucle d'outillage, éprouvée contre un vrai serveur qui récite.
 *
 * **Ce qui se vérifie ici est la conversation**, pas la qualité d'un choix : combien de
 * tours partent, ce que l'outil reçoit, ce que le modèle voit revenir, et ce que la
 * boucle fait de sa réponse. Le choix lui-même appartient au modèle, et aucun test ne
 * peut l'affirmer.
 *
 * **La règle la plus fragile est la dernière** : une référence que le modèle invente ne
 * doit rejoindre aucune fiche. C'est elle qui empêche de remplir une ligne avec une
 * fiche qu'on n'a pas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnthropicToolingTest {
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
        val item = (outcome as RecognitionOutcome.Recognized).recognition.items.single()
        assertEquals(ABRICOT.id, item.chosen?.id)
    }

    @Test
    fun `une reference inventee ne rejoint aucune fiche`() = runTest {
        // La regle qui empeche de remplir une ligne avec une fiche qu'on n'a pas : la
        // ligne repart alors vers l'estimation, ce qui est le bon comportement.
        server.enqueue(appelDeRecherche("abricot"))
        server.enqueue(reponseFinale("abricot" to "99999"))

        val item = analyser().recognized().items.single()

        assertNull(item.chosen)
    }

    @Test
    fun `une reference absente laisse la ligne a l estimation`() = runTest {
        server.enqueue(appelDeRecherche("abricot"))
        server.enqueue(reponseFinale("abricot" to null))

        assertNull(analyser().recognized().items.single().chosen)
    }

    @Test
    fun `le modele peut rendre le repas sans chercher`() = runTest {
        // Un seul aliment qu'il connait deja, ou une assiette ou rien ne vaut la
        // peine : la boucle ne l'oblige a rien.
        server.enqueue(reponseFinale("abricot" to null))

        assertTrue(analyser() is RecognitionOutcome.Recognized)
        assertEquals(emptyList<List<String>>(), demandes)
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
    fun `aucun schema de sortie ne part`() = runTest {
        // La combinaison du forcage de sortie avec l'outillage n'est documentee nulle
        // part : la boucle s'en passe, et la reponse arrive de toute facon par un appel
        // d'outil.
        server.enqueue(reponseFinale("abricot" to null))

        analyser()

        assertTrue(!server.takeRequest().body.readUtf8().contains("output_config"))
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
        // Il a repondu au lieu d'appeler l'outil de reponse : on ne devine pas ce qu'il
        // voulait dire, et l'appelant retombera sur l'analyse ordinaire.
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"Je vois un abricot."}]}"""))

        val outcome = analyser()

        assertEquals(AiError.NothingRecognized, (outcome as RecognitionOutcome.Failed).error)
    }

    @Test
    fun `des recherches sans fin s arretent`() = runTest {
        // Quatre appels partent au maximum -- trois recherches, plus le tour qui sert a
        // conclure. Sans cette borne, un modele qui tourne en rond coute sans limite.
        repeat(6) { server.enqueue(appelDeRecherche("abricot")) }

        assertTrue(analyser() is RecognitionOutcome.Failed)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `une cle refusee sort de la boucle`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"nope"}"""))

        assertTrue(analyser() is RecognitionOutcome.Failed)
        assertEquals(1, server.requestCount, "inutile d insister sur une cle refusee")
    }

    private suspend fun analyser(): RecognitionOutcome = AnthropicRecognizer(
        api = anthropicApi(aiClient(NetworkLog.Silent, SilentExchanges)),
        prompts = AiPrompts(extract = { "x" }, estimate = { "x" }, deep = { "consigne" }),
        dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
    ).deepRecognize(
        input = RecognitionInput.Text("un abricot"),
        configuration = AiConfiguration(
            provider = AiProvider.ANTHROPIC,
            apiKey = ApiKey("sk-de-test"),
            model = "claude-opus-5",
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
        """{"content":[{"type":"tool_use","id":"toolu_1","name":"$TOOL_SEARCH",""" +
            """"input":{"libelles":[${labels.joinToString(",") { "\"$it\"" }}]}}],""" +
            """"stop_reason":"tool_use"""" + jetons.asUsage() + "}",
    )

    private fun reponseFinale(vararg lignes: Pair<String, String?>, jetons: Pair<Int, Int>? = null) =
        MockResponse().setBody(
            """{"content":[{"type":"tool_use","id":"toolu_2","name":"$TOOL_SUBMIT",""" +
                """"input":{"items":[${lignes.joinToString(",") { it.asLine() }}]}}]""" +
                jetons.asUsage() + "}",
        )

    private fun Pair<Int, Int>?.asUsage(): String =
        this?.let { ""","usage":{"input_tokens":${it.first},"output_tokens":${it.second}}""" }.orEmpty()

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
