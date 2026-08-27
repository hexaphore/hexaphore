package app.hexavore.integration.ai

import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.EstimatedFood
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.RecognizedItem
import app.hexavore.domain.ai.TokenUsage
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Ce que le modèle a répondu, ramené à des lignes exploitables.
 *
 * **Même avec une sortie structurée, un modèle finit par entourer son JSON de
 * texte, ou par le tronquer.** Le schéma envoyé au fournisseur réduit la fréquence,
 * il ne l'annule pas — et les six fournisseurs ne l'appliquent pas tous. Ce parseur
 * est donc commun, et c'est la seule règle du module qui s'éprouve sans réseau.
 *
 * Il ne rend jamais de succès vide : une réponse lisible sans aucune ligne aboutit à
 * [AiError.NothingRecognized], pour que l'écran dise quelque chose plutôt que
 * d'ouvrir une validation sans contenu ([docs/05][ia]).
 *
 * [ia]: docs/05-ia.md
 */
internal fun parseRecognition(raw: String, usage: TokenUsage? = null): RecognitionOutcome {
    val items = balancedBlocks(raw)
        .mapNotNull { block -> runCatching { Tolerant.parseToJsonElement(block) }.getOrNull() }
        .mapNotNull { it.firstArray() }
        .firstOrNull()
        ?.mapNotNull { it.toItemOrNull() }
        ?: return RecognitionOutcome.Failed(AiError.Unparseable)

    return if (items.isEmpty()) {
        RecognitionOutcome.Failed(AiError.NothingRecognized)
    } else {
        RecognitionOutcome.Recognized(Recognition(items, usage))
    }
}

/**
 * Décodage tolérant, les trois indulgences de [docs/05][ia].
 *
 * Elles ne se recouvrent pas : `ignoreUnknownKeys` encaisse un champ que le modèle
 * ajoute de lui-même, `coerceInputValues` un `null` là où on attend une valeur, et
 * `isLenient` des guillemets manquants.
 *
 * [ia]: docs/05-ia.md
 */
private val Tolerant = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/**
 * Ce que le modèle a estimé, ramené à des valeurs pour 100 g.
 *
 * **Une liste vide est une réponse valide ici**, à la différence de la reconnaissance :
 * le prompt demande explicitement d'omettre ce qu'on ne sait pas, et un modèle qui ne
 * connaît aucun des libellés a raison de se taire. Les lignes restent alors à
 * compléter à la main, ce qui est exactement ce qu'elles étaient avant l'appel.
 *
 * Le libellé est recopié tel qu'il a été demandé — c'est le prompt qui l'exige, et
 * c'est ce qui permet de recoller l'estimation à sa ligne. Une reformulation rend une
 * estimation qu'on ne peut plus rattacher : elle est écartée plus haut, faute de
 * correspondance, plutôt que devinée.
 */
internal fun parseEstimation(raw: String, usage: TokenUsage? = null): EstimationOutcome {
    val foods = balancedBlocks(raw)
        .mapNotNull { block -> runCatching { Tolerant.parseToJsonElement(block) }.getOrNull() }
        .mapNotNull { it.firstArray() }
        .firstOrNull()
        ?.mapNotNull { it.toEstimateOrNull() }
        ?: return EstimationOutcome.Failed(AiError.Unparseable)

    return EstimationOutcome.Estimated(foods, usage)
}

/**
 * Une estimation, ou rien.
 *
 * Un libellé vide la rend inutilisable : sans lui, elle ne se rattache à aucune ligne.
 * Une valeur **négative** est traitée comme inconnue plutôt que ramenée à zéro : un
 * zéro est une affirmation, et le projet n'en écrit pas à la place de l'utilisateur.
 */
private fun JsonElement.toEstimateOrNull(): EstimatedFood? {
    val dto = runCatching { Tolerant.decodeFromJsonElement(EstimateDto.serializer(), this) }.getOrNull()
    val label = dto?.label?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    return EstimatedFood(
        label = label,
        per100g = NutrientValues(
            kcal = dto.kcal.positiveOrNull(),
            protein = dto.protein.positiveOrNull(),
            carbs = dto.carbs.positiveOrNull(),
            sugars = dto.sugars.positiveOrNull(),
            fat = dto.fat.positiveOrNull(),
            fiber = dto.fiber.positiveOrNull(),
        ),
    )
}

private fun Double?.positiveOrNull(): Double? = this?.takeIf { it >= 0.0 }

@Serializable
private data class EstimateDto(
    val label: String? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val sugars: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

@Serializable
private data class ItemDto(
    val label: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val confidence: Float? = null,
    /** Le poids total que le modèle attribue à la ligne, quand il s'est prononcé. */
    val grams: Double? = null,
)

/**
 * Les blocs équilibrés du texte, dans l'ordre, sans jamais tout charger.
 *
 * **Le premier bloc n'est pas forcément le bon.** [docs/05][ia] dit « le premier
 * bloc équilibré » ; une phrase d'introduction qui contient une accolade —
 * « voici l'analyse (format {label, quantité}) : [...] » — le détournerait vers
 * quelque chose qui n'est pas du JSON, et la réponse serait déclarée illisible alors
 * qu'elle était juste. La règle appliquée est donc : le premier bloc **qui se
 * décode**, ce que la paresse de cette séquence rend gratuit ([D72][decisions]).
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
private fun balancedBlocks(text: String): Sequence<String> = sequence {
    var from = text.indexOfAny(OPENERS)
    while (from >= 0) {
        val end = balancedEnd(text, from)
        if (end < 0) {
            // Un bloc jamais refermé signifie que le texte s'arrête là : une réponse
            // tronquee. Il n'y a rien apres, donc rien a chercher de plus.
            from = -1
        } else {
            yield(text.substring(from, end + 1))
            from = text.indexOfAny(OPENERS, end + 1)
        }
    }
}

/**
 * L'index du délimiteur qui referme celui de [start], ou `-1`.
 *
 * Les délimiteurs à l'intérieur d'une chaîne ne comptent pas, ni ceux qu'un
 * antislash échappe — un libellé comme `« pain [complet] »` refermerait sinon le
 * tableau au milieu.
 */
private fun balancedEnd(text: String, start: Int): Int {
    val opening = text[start]
    val closing = if (opening == '[') ']' else '}'
    var depth = 0
    var inString = false
    var escaped = false

    for (index in start until text.length) {
        val char = text[index]
        when {
            escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            char == '"' -> inString = !inString
            inString -> Unit
            char == opening -> depth++
            char == closing -> if (--depth == 0) return index
        }
    }
    return -1
}

/** Le tableau de lignes, que la réponse en soit un ou qu'elle en contienne un. */
private fun JsonElement.firstArray(): JsonArray? = when (this) {
    is JsonArray -> this
    is JsonObject -> values.filterIsInstance<JsonArray>().firstOrNull()
    else -> null
}

/**
 * La validation métier de [docs/05][ia], et ce qu'elle laisse passer.
 *
 * Une ligne sans libellé ou sans quantité positive est **écartée** plutôt que
 * corrigée : on ne sait pas quoi inventer. Une unité inconnue, si — `PIECE` est le
 * repli le moins faux, parce que c'est l'unité qui ne suppose ni poids ni volume.
 * Une confiance hors bornes est ramenée dans l'intervalle : le modèle s'est trompé
 * d'échelle, pas d'aliment.
 *
 * Une ligne illisible n'emporte pas les autres — c'est le `mapNotNull` de
 * l'appelant, et c'est voulu : une assiette de six aliments dont un mal formé vaut
 * mieux qu'un échec complet.
 *
 * [ia]: docs/05-ia.md
 */
private fun JsonElement.toItemOrNull(): RecognizedItem? {
    val dto = runCatching { Tolerant.decodeFromJsonElement(ItemDto.serializer(), this) }.getOrNull() ?: return null
    val label = dto.label?.trim().orEmpty()
    val quantity = dto.quantity ?: 0.0

    return if (label.isEmpty() || quantity <= 0.0) {
        null
    } else {
        RecognizedItem(
            label = label,
            quantity = quantity,
            unit = estimatedUnit(dto.unit),
            confidence = (dto.confidence ?: 0f).coerceIn(0f, 1f),
            // Zero ou negatif n'est pas un poids : c'est une absence, et elle se dit
            // `null` plutot que de faire disparaitre la ligne du journal.
            grams = dto.grams?.takeIf { it > 0.0 },
        )
    }
}

private fun estimatedUnit(raw: String?): EstimatedUnit =
    EstimatedUnit.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EstimatedUnit.PIECE

private val OPENERS = charArrayOf('[', '{')
