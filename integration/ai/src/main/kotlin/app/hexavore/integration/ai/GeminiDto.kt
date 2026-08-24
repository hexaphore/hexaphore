package app.hexavore.integration.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Le corps d'un appel à `models/{modèle}:generateContent`.
 *
 * **Rien ne ressemble à Anthropic ici**, et c'est le premier fournisseur qui le montre :
 * pas de `messages` mais des `contents`, pas de blocs typés par un discriminant mais
 * des `parts` dont la forme dit le type, et la consigne système dans un champ à part.
 * C'est exactement ce que `ProviderRecognizer` existe pour absorber — le domaine ne
 * voit ni l'un ni l'autre.
 */
@Serializable
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("systemInstruction") val systemInstruction: GeminiContent,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig,
)

@Serializable
internal data class GeminiContent(val parts: List<GeminiPart>, val role: String? = null)

/**
 * Un morceau de contenu : du texte **ou** une image, jamais les deux.
 *
 * Pas de type scellé comme chez Anthropic : Gemini distingue les parts par le champ
 * qui est présent, pas par un discriminant. Deux champs nuls dont un seul est rempli
 * décrivent donc le format plus fidèlement qu'une hiérarchie — **à condition que le
 * champ vide ne parte pas**. C'est `explicitNulls = false` dans [AI_JSON] qui le
 * garantit, et non `encodeDefaults` : le test qui affirme qu'une description ne joint
 * aucune donnée binaire est là pour que la ligne ne disparaisse pas.
 */
@Serializable
internal data class GeminiPart(
    val text: String? = null,
    @SerialName("inlineData") val inlineData: GeminiInlineData? = null,
)

@Serializable
internal data class GeminiInlineData(@SerialName("mimeType") val mimeType: String = "image/jpeg", val data: String)

/**
 * Ce qui contraint la sortie.
 *
 * `responseMimeType` **et** `responseSchema` : le premier seul rendrait du JSON de
 * forme libre, que le parseur saurait lire mais dont rien ne garantirait les champs.
 *
 * Aucune `temperature` : [docs/05][ia] en prescrivait une, et l'omettre ici n'est pas
 * une contrainte de l'API mais une cohérence — la régularité vient du schéma chez les
 * deux fournisseurs, et deux réglages pour la même intention finiraient par diverger.
 *
 * [ia]: docs/05-ia.md
 */
@Serializable
internal data class GeminiGenerationConfig(
    @SerialName("responseMimeType") val responseMimeType: String = "application/json",
    @SerialName("responseSchema") val responseSchema: JsonObject,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int,
)

@Serializable
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    @SerialName("usageMetadata") val usageMetadata: GeminiUsage? = null,
)

/**
 * Une réponse candidate.
 *
 * [finishReason] vaut `STOP` quand tout va bien. Les autres valeurs — `SAFETY`,
 * `MAX_TOKENS`, `RECITATION` — disent pourquoi il n'y a rien à lire, et se distinguent
 * d'une réponse illisible pour la même raison qu'un refus chez Anthropic : elles
 * n'appellent pas le même geste.
 */
@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason") val finishReason: String? = null,
)

@Serializable
internal data class GeminiUsage(
    @SerialName("promptTokenCount") val promptTokenCount: Int = 0,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Int = 0,
)
