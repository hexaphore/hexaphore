package app.hexaphore.integration.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Le corps d'un appel à `/v1/chat/completions` — celui que quatre fournisseurs
 * partagent.
 *
 * **C'est le format de fait**, et c'est ce qui fait du dernier fournisseur
 * l'assurance-vie de [docs/05][ia] : OpenRouter, Groq, Ollama, LM Studio et ceux qui
 * n'existent pas encore parlent tous celui-là. Écrire quatre classes de DTO
 * identiques pour quatre fournisseurs qui envoient le même JSON aurait fait quatre
 * endroits à corriger le jour où l'un d'eux ajoute un champ.
 *
 * **La consigne système est un message**, ici, et non un champ à part comme chez
 * Anthropic ou Gemini. C'est la troisième forme rencontrée pour la même idée, et la
 * dernière : les six fournisseurs sont couverts.
 *
 * [ia]: docs/05-ia.md
 */
@Serializable
internal data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("response_format") val responseFormat: OpenAiResponseFormat,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int,
)

/**
 * Un message, dont le contenu a **deux formes**.
 *
 * Du texte simple pour la consigne système et pour une description ; une liste de
 * parties dès qu'une image s'y joint. Les deux sont légales et le champ est le même,
 * d'où [OpenAiContent] plutôt qu'un type par cas — un `String?` et une `List?` côte à
 * côte auraient laissé exprimer « les deux à la fois », qui n'existe pas.
 */
@Serializable
internal data class OpenAiMessage(val role: String, val content: OpenAiContent)

/** Du texte, ou des parties. Sérialisé en chaîne ou en tableau selon le cas. */
@Serializable(with = OpenAiContentSerializer::class)
internal sealed interface OpenAiContent {
    @JvmInline
    value class Text(val value: String) : OpenAiContent

    @JvmInline
    value class Parts(val value: List<OpenAiPart>) : OpenAiContent
}

/**
 * Une partie de message : du texte, ou une image.
 *
 * `type` porte le discriminant — la troisième convention rencontrée, après le type
 * scellé d'Anthropic et le champ présent de Gemini.
 */
@Serializable
internal data class OpenAiPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: OpenAiImageUrl? = null,
)

/**
 * L'image, en `data:` URI.
 *
 * Le champ s'appelle `url` et accepte les deux : une adresse, ou les octets encodés en
 * base64 dans une URI de données. La seconde forme est la seule utilisable ici — la
 * photo n'est publiée nulle part, et elle ne doit surtout pas l'être.
 */
@Serializable
internal data class OpenAiImageUrl(val url: String)

/**
 * Ce qui contraint la sortie, et **la seule différence entre les quatre**.
 *
 * OpenAI accepte un schéma complet ; les trois autres ne promettent que
 * `json_object`, qui exige du JSON sans dire lequel. C'est le prompt qui porte la
 * forme dans ce second cas — il la décrit déjà, en toutes lettres et avec un exemple,
 * précisément pour que le parseur reste le même pour les six fournisseurs.
 */
@Serializable
internal data class OpenAiResponseFormat(
    val type: String,
    @SerialName("json_schema") val jsonSchema: OpenAiJsonSchema? = null,
)

@Serializable
internal data class OpenAiJsonSchema(val name: String, val schema: JsonObject, val strict: Boolean = true)

@Serializable
internal data class OpenAiResponse(val choices: List<OpenAiChoice> = emptyList(), val usage: OpenAiUsage? = null)

/**
 * Un choix, et pourquoi [finishReason] compte.
 *
 * `length` dit que la réponse est **tronquée** : le JSON qu'elle contient est
 * incomplet, et le parseur échouerait sur un texte qui n'a rien d'illisible — il
 * manque juste la fin. `content_filter` dit que le fournisseur a refusé. Les deux
 * appellent le même geste que le refus d'Anthropic.
 */
@Serializable
internal data class OpenAiChoice(
    val message: OpenAiResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class OpenAiResponseMessage(val content: String? = null)

@Serializable
internal data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
)
