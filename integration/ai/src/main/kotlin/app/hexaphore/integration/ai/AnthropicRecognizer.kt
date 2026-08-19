package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiConfiguration
import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.RecognitionInput
import app.hexaphore.domain.ai.RecognitionOutcome
import app.hexaphore.domain.ai.TokenUsage
import app.hexaphore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64

/**
 * Anthropic, et les trois arbitrages que son API impose à [docs/05][ia].
 *
 * ### Le raisonnement reste actif, et l'économie passe par l'effort
 *
 * [docs/05][ia] écrit « pas de raisonnement demandé — il coûterait des jetons sans
 * améliorer une tâche de perception ». L'intention est juste ; le moyen ne l'est
 * plus. Sur les modèles actuels le raisonnement est **actif par défaut**, et le
 * levier recommandé pour dépenser moins est de baisser l'effort, pas de le couper :
 * couper coûte plus cher en pratique et traîne deux modes d'échec documentés sur
 * cette famille de modèles — un appel structuré rendu en texte brut, et des balises
 * de raisonnement qui fuient dans la réponse visible.
 *
 * **Aucun des deux ne nous mordrait forcément** : on ne déclare pas d'outil, et le
 * schéma contraint la sortie. Mais on n'a rien à y gagner : [EFFORT] rend l'économie
 * cherchée sans toucher au mécanisme. Prendre un risque documenté pour zéro bénéfice
 * n'est pas un arbitrage, c'est une distraction.
 *
 * ### Ni température, ni jetons comptés au plus juste
 *
 * `temperature` est retirée de l'API et rend un `400` ; [MAX_TOKENS] est large parce
 * que le plafond couvre le raisonnement **et** la réponse — les 1 024 jetons de
 * [docs/05][ia] tronqueraient le JSON en silence.
 *
 * ### Un refus n'est pas une réponse illisible
 *
 * Les classificateurs peuvent décliner une requête : la réponse est alors un `200`
 * parfaitement lisible, sans contenu. La ranger dans `Unparseable` annoncerait un
 * défaut technique là où il n'y en a pas, et inviterait à réessayer à l'identique ce
 * qui vient d'être refusé. C'est [AiError.NothingRecognized] — le raisonnement de
 * [D72][decisions], appliqué à une issue qu'il n'avait pas prévue.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
internal class AnthropicRecognizer(
    private val api: AnthropicApi,
    private val prompt: SystemPrompt,
    private val dispatchers: DispatcherProvider,
) : ProviderRecognizer {
    override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome =
        withContext(dispatchers.io) {
            try {
                api
                    .messages(
                        configuration.endpoint(),
                        configuration.apiKey.value,
                        configuration.request(input, prompt),
                    )
                    .toOutcome()
            } catch (timeout: SocketTimeoutException) {
                // Avant IOException, dont elle herite : « le service n'a pas repondu »
                // et « on n'a pas pu demander » n'invitent pas au meme geste.
                timeout.reducedTo(AiError.Timeout)
            } catch (offline: IOException) {
                offline.reducedTo(AiError.NoNetwork)
            }
        }
}

/**
 * L'adresse complète, quelle que soit la façon dont l'utilisateur a saisi la sienne.
 *
 * Une base sans barre oblique finale collerait le chemin au dernier segment, et
 * l'échec ressemblerait à une clé refusée.
 */
private fun AiConfiguration.endpoint(): String = baseUrl.trimEnd('/') + "/v1/messages"

private fun AiConfiguration.request(input: RecognitionInput, prompt: SystemPrompt) = AnthropicRequest(
    model = model,
    maxTokens = MAX_TOKENS,
    system = prompt.text(),
    outputConfig = OutputConfig(effort = EFFORT, format = OutputFormat(schema = RECOGNITION_SCHEMA)),
    messages = listOf(AnthropicMessage(role = "user", content = input.blocks())),
)

/**
 * L'image d'abord, la consigne ensuite.
 *
 * L'ordre compte : un modèle qui lit la consigne avant d'avoir vu l'image la rapporte
 * moins bien à ce qu'il regarde. La note de l'utilisateur est jointe au même bloc —
 * c'est le levier de précision le moins coûteux qui existe, et il n'a de sens qu'à
 * côté de la demande.
 */
private fun RecognitionInput.blocks(): List<ContentBlock> = when (this) {
    is RecognitionInput.Photo -> listOf(
        ImageBlock(ImageSource(data = Base64.getEncoder().encodeToString(jpeg))),
        TextBlock(note?.let { "$PHOTO_REQUEST\n\n$it" } ?: PHOTO_REQUEST),
    )

    is RecognitionInput.Text -> listOf(TextBlock(description))
}

private fun Response<AnthropicResponse>.toOutcome(): RecognitionOutcome {
    val body = body()
    return when {
        !isSuccessful -> RecognitionOutcome.Failed(toAiError())
        body == null -> RecognitionOutcome.Failed(AiError.Unparseable)
        body.stopReason == REFUSAL -> RecognitionOutcome.Failed(AiError.NothingRecognized)
        else -> parseRecognition(body.text(), body.usage?.toDomain())
    }
}

/**
 * Le texte de la réponse, tous blocs confondus.
 *
 * Concaténés plutôt que « le premier » : un bloc de raisonnement précède le texte et
 * porte une chaîne vide, et prendre le premier rendrait donc du vide. Les recoller
 * évite de dépendre d'un ordre que rien ne nous promet.
 */
private fun AnthropicResponse.text(): String =
    content.filter { it.type == TEXT_BLOCK }.mapNotNull { it.text }.joinToString(separator = "")

private fun AnthropicUsage.toDomain() = TokenUsage(input = inputTokens, output = outputTokens)

/**
 * Le code HTTP, traduit une fois — et qui ne remonte jamais tel quel à un écran
 * d'analyse.
 *
 * `402` rejoint le quota : « crédits épuisés » et « trop de requêtes » se corrigent du
 * même côté et n'ont qu'un message pour l'utilisateur.
 *
 * **Le corps de l'erreur est joint aux cas non traduits**, et lui seul. Les autres ont
 * déjà un message précis — « votre clé a été refusée » ne gagne rien à être suivi de
 * la phrase anglaise du fournisseur. `Server` n'en a aucun : c'est le fourre-tout, et
 * sans ce que le fournisseur en dit, personne ne peut le diagnostiquer. Un `400` qui
 * refuse un champ et un `400` qui annonce un compte sans crédits sont deux problèmes
 * différents que le seul chiffre confond.
 */
private fun Response<AnthropicResponse>.toAiError(): AiError = when (code()) {
    UNAUTHORIZED, FORBIDDEN -> AiError.InvalidKey
    PAYMENT_REQUIRED, TOO_MANY_REQUESTS -> AiError.QuotaExceeded
    else -> AiError.Server(code(), detail())
}

/**
 * Ce que le fournisseur a écrit, borné.
 *
 * `errorBody()` ne se lit **qu'une fois** et peut jeter ; le `runCatching` couvre les
 * deux. La borne évite qu'une page HTML d'un relais mal configuré remplisse l'écran —
 * et ce qui compte tient toujours dans les premières lignes.
 */
private fun Response<AnthropicResponse>.detail(): String? =
    runCatching { errorBody()?.string() }.getOrNull()?.trim()?.take(DETAIL_LIMIT)?.takeIf { it.isNotEmpty() }

/**
 * Ce qui remplace `temperature = 0.2`, et le levier qu'on déplacera.
 *
 * `low` est la lecture la plus proche de l'intention de [docs/05][ia] — une tâche de
 * perception n'a pas besoin de délibérer — et la moins chère, ce qui compte quand
 * c'est l'utilisateur qui paie. **Il n'a été calibré contre rien** : aucune
 * reconnaissance réelle n'a encore tourné. Une constante nommée, pour qu'un jour de
 * calibrage n'ait qu'un mot à changer.
 *
 * [ia]: docs/05-ia.md
 */
private const val EFFORT = "low"

/** Large exprès : le plafond couvre le raisonnement autant que la réponse. */
private const val MAX_TOKENS = 4096

private const val TEXT_BLOCK = "text"
private const val REFUSAL = "refusal"

private const val UNAUTHORIZED = 401
private const val PAYMENT_REQUIRED = 402
private const val FORBIDDEN = 403
private const val TOO_MANY_REQUESTS = 429

/** Assez pour la phrase du fournisseur, pas assez pour une page d erreur entiere. */
private const val DETAIL_LIMIT = 400

private const val PHOTO_REQUEST = "Analyse ce repas."

/** Anthropic exige `additionalProperties: false` dans sa sortie structurée. */
private val RECOGNITION_SCHEMA: JsonObject = recognitionSchema(strict = true)
