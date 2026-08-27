package app.hexavore.integration.ai

import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.CatalogueTool
import app.hexavore.domain.ai.LabelCandidates
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.TOOL_ROUNDS
import app.hexavore.domain.ai.TokenUsage
import app.hexavore.domain.food.Food
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import java.util.Base64

/**
 * La même boucle, dans l'autre dialecte.
 *
 * **Les trois écarts avec Anthropic, et aucun n'a été deviné** — la forme vient de la
 * référence de `generateContent` :
 *
 * - les outils sont enveloppés dans `functionDeclarations` au lieu d'être la liste ;
 * - l'appel arrive dans une *part* (`functionCall`) et non dans un bloc typé, et sa
 *   réponse repart dans une part `functionResponse` d'un contenu de rôle `user` ;
 * - cette réponse est un **objet**, pas une chaîne : on y range le JSON analysé.
 *
 * Ce qui reste identique est tout le reste — le nombre de tours, ce qu'on accumule, les
 * quatre façons d'en sortir, et le parseur du repas rendu. C'est ce que `Tooling.kt`
 * porte, et c'est la moitié qui compte.
 *
 * @see docs/04-sources-de-donnees.md
 */
internal suspend fun GeminiApi.deepRecognize(
    input: RecognitionInput,
    configuration: AiConfiguration,
    prompt: SystemPrompt,
    catalogue: CatalogueTool,
): RecognitionOutcome {
    val contents = mutableListOf(GeminiContent(role = "user", parts = input.geminiToolParts()).asJson())
    val shown = mutableMapOf<String, Food>()
    val rounds = mutableListOf<TokenUsage?>()

    repeat(TOOL_ROUNDS + 1) {
        val response = generateContent(
            configuration.geminiToolEndpoint(),
            configuration.apiKey.value,
            configuration.geminiToolRequest(contents, prompt),
        )
        // Chaque tour se paie : ce qu'on comptera est la conversation entiere.
        rounds += response.body()?.usageMetadata?.toDomain()
        when (val turn = response.asGeminiTurn(shown, rounds.total())) {
            is GeminiTurn.Done -> return turn.outcome
            is GeminiTurn.Searching -> {
                val groups = catalogue.candidatesFor(turn.labels)
                groups.forEach { group -> group.candidates.forEach { shown[it.reference] = it.food } }

                // Tel quel : la signature de pensee de l'appel vit dans des champs
                // que nos types ne nomment pas, et le modele exige de la revoir.
                contents += turn.content.asModelTurn()
                contents += GeminiContent(
                    role = "user",
                    parts = listOf(groups.asFunctionResponse(turn.name)),
                ).asJson()
            }
        }
    }
    return RecognitionOutcome.Failed(AiError.NothingRecognized)
}

/** Ce qu un tour a produit, dans l autre dialecte. La meme distinction, les memes raisons. */
private sealed interface GeminiTurn {
    data class Searching(val labels: List<String>, val name: String, val content: JsonObject) : GeminiTurn

    data class Done(val outcome: RecognitionOutcome) : GeminiTurn
}

private fun Response<GeminiResponse>.asGeminiTurn(shown: Map<String, Food>, billed: TokenUsage?): GeminiTurn {
    val body = body()
    val candidate = body?.candidates?.firstOrNull()
    // Brut : on le decode pour lire l'appel, et c'est l'original qui repartira.
    val raw = candidate?.content
    val call = raw?.asGeminiContent()?.parts?.firstNotNullOfOrNull { it.functionCall }
    return when {
        candidate == null -> GeminiTurn.Done(RecognitionOutcome.Failed(geminiToolFailure()))
        call == null || raw == null -> GeminiTurn.Done(RecognitionOutcome.Failed(AiError.NothingRecognized))
        call.name == TOOL_SUBMIT -> GeminiTurn.Done(parseSubmitted(call.args, shown, billed))
        else -> GeminiTurn.Searching(call.args.requestedLabels(), call.name, raw)
    }
}

/**
 * La reponse d outil, dans une part.
 *
 * `response` est un **objet** et non une chaine, a la difference d Anthropic : le JSON
 * qu on vient d ecrire est donc reanalyse pour repartir comme structure.
 */
private fun List<LabelCandidates>.asFunctionResponse(name: String) = GeminiPart(
    functionResponse = GeminiFunctionResponse(
        name = name,
        response = Json.parseToJsonElement(asToolAnswer()) as JsonObject,
    ),
)

/**
 * La requête d'un tour : les deux outils, et **aucun schéma de sortie**.
 *
 * `generationConfig` est absent en entier, et pas seulement son schéma : le plafond de
 * jetons y vivait, et le porter seul dans un objet dont le reste est nul rendrait un
 * champ vide que rien n'attend. La boucle s'en passe — les modèles de Gemini ont un
 * plafond par défaut plus large que ce qu'une réponse d'outil demande.
 */
private fun AiConfiguration.geminiToolRequest(contents: List<JsonObject>, prompt: SystemPrompt) = GeminiRequest(
    contents = contents,
    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = prompt.text()))),
    tools = listOf(
        GeminiTool(
            functionDeclarations = listOf(
                GeminiFunction(TOOL_SEARCH, SEARCH_HINT, searchToolSchema(strict = false)),
                GeminiFunction(TOOL_SUBMIT, SUBMIT_HINT, submitToolSchema(strict = false)),
            ),
        ),
    ),
    toolConfig = GeminiToolConfig(),
)

private fun AiConfiguration.geminiToolEndpoint(): String =
    baseUrl.trimEnd('/') + "/v1beta/models/" + model + ":generateContent"

private fun RecognitionInput.geminiToolParts(): List<GeminiPart> = when (this) {
    is RecognitionInput.Photo -> listOf(
        GeminiPart(inlineData = GeminiInlineData(data = Base64.getEncoder().encodeToString(jpeg))),
        GeminiPart(text = note?.let { "$TOOL_PHOTO_REQUEST\n\n$it" } ?: TOOL_PHOTO_REQUEST),
    )

    is RecognitionInput.Text -> listOf(GeminiPart(text = description))
}

private fun Response<GeminiResponse>.geminiToolFailure(): AiError =
    if (isSuccessful) AiError.Unparseable else geminiError()

private const val TOOL_PHOTO_REQUEST = "Analyse ce repas."

private const val SEARCH_HINT =
    "Cherche des aliments dans le catalogue nutritionnel de l'application. " +
        "Appelle-le avec tous les aliments que tu as identifiés, en une fois. " +
        "Pour chaque libellé, tu recevras jusqu'à six fiches avec leur nom complet, " +
        "leur rayon et leurs valeurs pour 100 g."

private const val SUBMIT_HINT =
    "Rends le repas analysé. Pour chaque aliment, donne « reference » si l'une des " +
        "fiches proposées correspond vraiment — c'est ce qui donne des valeurs mesurées " +
        "plutôt qu'estimées. Omets « reference » si aucune ne convient : l'application " +
        "estimera elle-même les valeurs, ce qui vaut mieux qu'une fiche approchante."
