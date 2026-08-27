package app.hexavore.integration.ai

import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.CatalogueTool
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.TOOL_ROUNDS
import app.hexavore.domain.ai.TokenUsage
import app.hexavore.domain.food.Food
import kotlinx.serialization.json.JsonObject
import retrofit2.Response

/**
 * La boucle d'outillage, côté Anthropic.
 *
 * ### Ce que la boucle fait, tour par tour
 *
 * On envoie l'assiette avec deux outils déclarés. Le modèle appelle [TOOL_SEARCH] avec
 * les libellés qu'il a lus, on lui répond ce que le catalogue propose, il recommence
 * s'il veut affiner, puis il appelle [TOOL_SUBMIT] avec son repas. Le JSON arrive dans
 * les arguments de ce dernier appel.
 *
 * ### Les quatre façons d'en sortir
 *
 * - **Il rend le repas** : c'est le cas nominal, et le seul qui produise des lignes.
 * - **Il répond en texte** au lieu d'appeler l'outil de réponse. On ne devine pas :
 *   c'est un échec, et l'appelant retombera sur l'analyse ordinaire.
 * - **Les tours sont épuisés.** Trois recherches sans conclure veut dire qu'il tourne
 *   en rond ; continuer coûterait sans rien promettre.
 * - **Le réseau ou le fournisseur casse.** Traduit comme partout ailleurs.
 *
 * ### Ce qui s'accumule
 *
 * `shown` retient les fiches montrées, indexées par référence. C'est ce qui évite un
 * port de relecture : quand le modèle désigne un code, la fiche est déjà là. Une
 * référence qu'il aurait inventée n'y figure pas, et la ligne repart vers l'estimation.
 *
 * `rounds` retient ce que chaque tour a coûté. Le fournisseur ne garde aucun état, donc
 * le dernier aller-retour renvoie l'assiette et les recherches qui l'ont précédée : ne
 * compter que lui annoncerait une fraction de la facture.
 *
 * @see docs/04-sources-de-donnees.md
 */
internal suspend fun AnthropicApi.deepRecognize(
    input: RecognitionInput,
    configuration: AiConfiguration,
    prompt: SystemPrompt,
    catalogue: CatalogueTool,
): RecognitionOutcome {
    val messages = mutableListOf(AnthropicMessage(role = "user", content = input.blocks().map { it.asJson() }))
    val shown = mutableMapOf<String, Food>()
    val rounds = mutableListOf<TokenUsage?>()

    // Un tour de plus que le nombre de recherches autorisees : le dernier sert a
    // conclure, sinon trois recherches ne laisseraient aucune occasion de repondre.
    repeat(TOOL_ROUNDS + 1) {
        val response = messages(
            configuration.endpoint(),
            configuration.apiKey.value,
            configuration.toolRequest(messages, prompt),
        )
        // Chaque tour se paie : ce qu'on comptera est la conversation entiere.
        rounds += response.body()?.usage?.toDomain()
        when (val turn = response.asTurn(shown, rounds.total())) {
            is Turn.Done -> return turn.outcome
            is Turn.Searching -> {
                val groups = catalogue.candidatesFor(turn.labels)
                groups.forEach { group -> group.candidates.forEach { shown[it.reference] = it.food } }

                // Tel quel : ses blocs de raisonnement portent une signature que
                // l'API exige de revoir, et qu'aucun de nos types ne nomme.
                messages += AnthropicMessage(role = "assistant", content = turn.content)
                messages += AnthropicMessage(
                    role = "user",
                    content = listOf(
                        ToolResultBlock(toolUseId = turn.callId, content = groups.asToolAnswer()).asJson(),
                    ),
                )
            }
        }
    }
    // Trois recherches sans conclure veut dire qu'il tourne en rond ; continuer
    // couterait sans rien promettre.
    return RecognitionOutcome.Failed(AiError.NothingRecognized)
}

/**
 * Ce qu'un tour a produit : soit une recherche à satisfaire, soit une issue.
 *
 * Sorti de la boucle quand le seuil de sorties a mordu, et le découpage suit ce que les
 * choses sont : la boucle dit **combien de fois**, ceci dit **ce qui vient d'arriver**.
 */
private sealed interface Turn {
    data class Searching(val labels: List<String>, val callId: String, val content: List<JsonObject>) : Turn

    data class Done(val outcome: RecognitionOutcome) : Turn
}

private fun Response<AnthropicResponse>.asTurn(shown: Map<String, Food>, billed: TokenUsage?): Turn =
    body()?.asTurn(shown, billed) ?: Turn.Done(RecognitionOutcome.Failed(toolFailure()))

/**
 * Ce que ce tour a donne.
 *
 * Un `when` et non des sorties anticipees : les quatre cas se lisent alors cote a cote,
 * et c'est ce qui rend evident qu'aucun n'est oublie.
 */
private fun AnthropicResponse.asTurn(shown: Map<String, Food>, billed: TokenUsage?): Turn {
    val call = content.mapNotNull { it.asResponseBlock() }.firstOrNull { it.type == TOOL_USE_BLOCK }
    val arguments = call?.input
    return when {
        // Il a repondu en texte au lieu d'appeler l'outil de reponse : on ne devine pas.
        call == null -> Turn.Done(RecognitionOutcome.Failed(AiError.NothingRecognized))
        arguments == null -> Turn.Done(RecognitionOutcome.Failed(AiError.Unparseable))
        call.name == TOOL_SUBMIT -> Turn.Done(parseSubmitted(arguments, shown, billed))
        else -> Turn.Searching(arguments.requestedLabels(), call.id.orEmpty(), content)
    }
}

/**
 * La requête d'un tour : les deux outils, et **aucun forçage de sortie**.
 *
 * `output_config` est absent, contrairement au chemin ordinaire. Ce n'est pas un oubli
 * — voir `Tooling.kt` : rien ne dit que le forçage se combine avec l'outillage, et la
 * réponse arrive de toute façon par un appel d'outil.
 */
private fun AiConfiguration.toolRequest(messages: List<AnthropicMessage>, prompt: SystemPrompt) = AnthropicRequest(
    model = model,
    maxTokens = TOOL_MAX_TOKENS,
    system = prompt.text(),
    messages = messages,
    tools = listOf(
        AnthropicTool(TOOL_SEARCH, SEARCH_DESCRIPTION, searchToolSchema(strict = true)),
        AnthropicTool(TOOL_SUBMIT, SUBMIT_DESCRIPTION, submitToolSchema(strict = true)),
    ),
    toolChoice = ToolChoice(),
)

/**
 * L'échec d'un tour.
 *
 * Un corps nul sur une réponse réussie est illisible ; sinon c'est le code qui parle,
 * traduit comme partout ailleurs dans ce module.
 */
private fun Response<AnthropicResponse>.toolFailure(): AiError = if (isSuccessful) AiError.Unparseable else toAiError()

private const val TOOL_USE_BLOCK = "tool_use"

/**
 * Plus large que le chemin ordinaire.
 *
 * Une boucle accumule : l'assiette, les candidats de chaque recherche et le
 * raisonnement de chaque tour tiennent dans le même plafond. Le tronquer ferait arriver le JSON
 * du repas coupé au milieu d'un libellé, après avoir payé trois allers-retours.
 */
private const val TOOL_MAX_TOKENS = 8192

private const val SEARCH_DESCRIPTION =
    "Cherche des aliments dans le catalogue nutritionnel de l'application. " +
        "Appelle-le avec tous les aliments que tu as identifiés, en une fois. " +
        "Pour chaque libellé, tu recevras jusqu'à six fiches avec leur nom complet, " +
        "leur rayon et leurs valeurs pour 100 g."

private const val SUBMIT_DESCRIPTION =
    "Rends le repas analysé. Pour chaque aliment, donne « reference » si l'une des " +
        "fiches proposées correspond vraiment — c'est ce qui donne des valeurs mesurées " +
        "plutôt qu'estimées. Omets « reference » si aucune ne convient : l'application " +
        "estimera elle-même les valeurs, ce qui vaut mieux qu'une fiche approchante."
