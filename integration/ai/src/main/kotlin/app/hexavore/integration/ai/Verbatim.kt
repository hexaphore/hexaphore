package app.hexavore.integration.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * **Le tour du modèle se rejoue tel quel, jamais reconstruit.**
 *
 * Les deux fournisseurs exigent que le message d'assistant reparte **à l'identique**
 * avant le résultat d'outil. Or nos types ne décrivent que ce qu'on lit, et [AI_JSON]
 * ignore les champs inconnus : tout ce qu'on n'a pas déclaré disparaît au décodage et ne
 * repart jamais. Reconstruire un tour depuis des types revient donc à promettre qu'on
 * connaît la liste complète des champs du fournisseur — une promesse qu'on ne peut pas
 * tenir, et qui a cassé l'analyse approfondie dès le premier vrai appel.
 *
 * Gemini attache aux appels d'outil une **signature de pensée** qu'il exige de revoir ;
 * Anthropic pose la même règle sur ses blocs de raisonnement, dont la documentation dit
 * qu'en retirer un déclenche une erreur d'ordre ou de signature. Aucun de nos champs ne
 * les nommait.
 *
 * **On décode pour lire, jamais pour renvoyer.** Lire une copie ne perd rien ; réécrire
 * l'original perd exactement ce qu'on ignore. C'est pour cela que le contenu venu du
 * modèle voyage ici en JSON brut, et que seuls les contenus **qu'on fabrique** passent
 * par un type.
 *
 * @see docs/05-ia.md
 */
internal fun ContentBlock.asJson(): JsonObject = AI_JSON.encodeToJsonElement<ContentBlock>(this).jsonObject

/** Le pendant, dans l'autre dialecte : un contenu qu'on fabrique, mis sous sa forme de fil. */
internal fun GeminiContent.asJson(): JsonObject = AI_JSON.encodeToJsonElement(this).jsonObject

/**
 * La lecture d'un bloc reçu.
 *
 * `null` quand la forme surprend, plutôt qu'une exception : un bloc qu'on ne sait pas
 * lire ne doit pas faire tomber l'analyse — et il repartira quand même, puisqu'il n'est
 * plus reconstruit. C'est ce que l'ancien commentaire d'[AnthropicResponse] croyait
 * déjà vrai, et qui ne l'était que pour la lecture.
 */
internal fun JsonObject.asResponseBlock(): ResponseBlock? =
    runCatching { AI_JSON.decodeFromJsonElement<ResponseBlock>(this) }.getOrNull()

/** La même lecture, côté Gemini : les *parts* d'un contenu qu'on n'a pas fabriqué. */
internal fun JsonObject.asGeminiContent(): GeminiContent? =
    runCatching { AI_JSON.decodeFromJsonElement<GeminiContent>(this) }.getOrNull()

/**
 * Le tour du modèle, prêt à repartir — **la seule retouche que la règle autorise**.
 *
 * Gemini renvoie le rôle dans son contenu, et on le garde tel quel. Mais s'il venait à
 * manquer, le tour serait attribué au hasard des positions : on l'ajoute alors, sans
 * jamais en remplacer un. **Ajouter ce qu'on sait ne perd rien ; c'est retirer ce qu'on
 * ignore qui casse**, et c'est de cela que cette règle protège.
 */
internal fun JsonObject.asModelTurn(): JsonObject =
    if (containsKey(ROLE)) this else JsonObject(this + (ROLE to JsonPrimitive("model")))

private const val ROLE = "role"
