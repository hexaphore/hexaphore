package app.hexavore.integration.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Le corps d'un appel à `/v1/messages`.
 *
 * **Ni `temperature`, ni `top_p`, ni `top_k`.** [docs/05][ia] prescrivait
 * `temperature = 0.2` ; les trois sont retirés des modèles Claude actuels et leur
 * présence rend un `400`. La régularité qu'on cherchait par là vient maintenant du
 * schéma de sortie, qui contraint la forme, et de l'effort, qui règle la profondeur.
 *
 * **Aucun champ `thinking` non plus**, et c'est le second écart. Le raisonnement est
 * actif par défaut ; le désactiver s'écrirait ici, et ne s'écrit pas — voir
 * [AnthropicRecognizer].
 *
 * [ia]: docs/05-ia.md
 */
@Serializable
internal data class AnthropicRequest(
    val model: String,
    /**
     * Le plafond porte sur le raisonnement **et** la réponse ensemble.
     *
     * C'est ce qui condamne les 1 024 jetons de [docs/05][ia] : avec un raisonnement
     * actif, une réponse d'une dizaine de lignes se fait tronquer en silence, et le
     * JSON arrive coupé au milieu d'un libellé.
     *
     * [ia]: docs/05-ia.md
     */
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    /**
     * `null` en mode approfondi : la sortie n'y est pas forcée, le modèle rend son
     * repas en appelant un outil. Rien ne documente si les deux se combinent, et le
     * silence n'est pas une confirmation.
     */
    @SerialName("output_config") val outputConfig: OutputConfig? = null,
    val messages: List<AnthropicMessage>,
    /** Les deux outils, en mode approfondi seulement. */
    val tools: List<AnthropicTool>? = null,
    @SerialName("tool_choice") val toolChoice: ToolChoice? = null,
)

/** Un outil déclaré : son nom, ce qu'il fait, et la forme de ses arguments. */
@Serializable
internal data class AnthropicTool(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonObject,
)

/**
 * Un seul appel d'outil par tour.
 *
 * `auto` laisse le modèle décider s'il appelle ou s'il répond ;
 * `disable_parallel_tool_use` lui interdit d'en appeler plusieurs à la fois. C'est la
 * forme que la documentation montre elle-même pour un aller-retour de ce genre, et
 * elle épargne la règle des résultats parallèles — qu'une erreur suffit à casser.
 */
@Serializable
internal data class ToolChoice(
    val type: String = "auto",
    @SerialName("disable_parallel_tool_use") val disableParallelToolUse: Boolean = true,
)

/** L'effort consenti, et la forme exigée de la réponse. */
@Serializable
internal data class OutputConfig(val effort: String, val format: OutputFormat)

/**
 * La sortie structurée : le modèle **ne peut pas** rendre autre chose que ce schéma.
 *
 * C'est un écart avec [docs/05][ia], qui prescrivait un outil forcé (`tool_choice`).
 * L'outil forcé rend le JSON dans `tool_use.input`, donc par un chemin que le parseur
 * commun ne lit pas — il aurait fallu une seconde extraction pour ce seul
 * fournisseur. Ici la réponse **est** du texte, et le parseur de [D72][decisions] la
 * lit sans rien savoir d'Anthropic.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
@Serializable
internal data class OutputFormat(val type: String = "json_schema", val schema: JsonObject)

@Serializable
internal data class AnthropicMessage(val role: String, val content: List<ContentBlock>)

/**
 * Un bloc de contenu envoyé au modèle.
 *
 * Le discriminant de `kotlinx.serialization` est `type` par défaut, ce qui est
 * exactement le nom qu'attend l'API : les sous-types s'encodent en
 * `{"type": "text", …}` sans configuration.
 */
@Serializable
internal sealed interface ContentBlock

@Serializable
@SerialName("text")
internal data class TextBlock(val text: String) : ContentBlock

@Serializable
@SerialName("image")
internal data class ImageBlock(val source: ImageSource) : ContentBlock

/**
 * L'appel d'outil, **renvoyé tel quel** dans l'historique.
 *
 * L'API exige que le message d'assistant soit rejoué à l'identique avant le résultat :
 * un appel qu'on paraphraserait ne serait plus celui auquel le résultat répond.
 */
@Serializable
@SerialName("tool_use")
internal data class ToolUseBlock(val id: String, val name: String, val input: JsonObject) : ContentBlock

/** Ce que l'outil a répondu, rattaché à l'appel par son identifiant. */
@Serializable
@SerialName("tool_result")
internal data class ToolResultBlock(@SerialName("tool_use_id") val toolUseId: String, val content: String) :
    ContentBlock

@Serializable
internal data class ImageSource(
    val type: String = "base64",
    @SerialName("media_type") val mediaType: String = "image/jpeg",
    val data: String,
)

/**
 * Ce que l'API rend.
 *
 * **Les blocs de réponse ne sont pas polymorphes ici**, contrairement à ceux qu'on
 * envoie : `type` est une simple chaîne. La réponse contient des blocs dont ce module
 * n'a que faire — un bloc de raisonnement au texte vide, par exemple — et un décodeur
 * scellé échouerait sur le premier type qu'Anthropic ajoute. Ne pas reconnaître un
 * bloc doit être sans conséquence.
 */
@Serializable
internal data class AnthropicResponse(
    val content: List<ResponseBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
)

@Serializable
internal data class ResponseBlock(
    val type: String,
    val text: String? = null,
    /** Les trois champs d'un `tool_use`. Absents partout ailleurs, donc facultatifs. */
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null,
)

@Serializable
internal data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)
