package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.RecognitionOutcome
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Le montage HTTP des fournisseurs d'IA, écrit une seule fois.
 *
 * Il vit ici plutôt que dans le module Hilt pour que le test puisse assembler
 * **exactement** la même pile devant un serveur local — c'est le montage d'Open Food
 * Facts, et pour la même raison : recopié dans le test, il aurait éprouvé un client
 * qui ressemble au vrai, et l'intercepteur de redaction aurait pu manquer en
 * production avec un test vert.
 *
 * **Une seule pile pour les six fournisseurs**, ce qui est l'argument qui a fait
 * retenir Retrofit ([D73][decisions]) : un seul intercepteur de redaction à écrire,
 * donc un seul à ne pas oublier.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun aiClient(log: NetworkLog): OkHttpClient = OkHttpClient.Builder()
    // Soixante secondes de lecture, la valeur que docs/05 fixe pour AiError.Timeout :
    // une analyse d'image est lente, et abandonner trop tot ferait recommencer
    // quelqu'un qui n'avait qu'a attendre. La connexion, elle, se juge vite.
    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .addInterceptor(RedactionInterceptor(log))
    .build()

internal fun anthropicApi(client: OkHttpClient): AnthropicApi = Retrofit.Builder()
    // Une base de facade : chaque appel porte son URL complete, parce que
    // l'utilisateur peut avoir change la sienne. Retrofit en exige une quand meme.
    .baseUrl(AiProvider.ANTHROPIC.defaultBaseUrl)
    .client(client)
    .addConverterFactory(AI_JSON.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
    .build()
    .create(AnthropicApi::class.java)

/**
 * Le meme client, une seconde interface.
 *
 * La base de facade differe — Retrofit en exige une, et celle du fournisseur est la
 * plus lisible — mais chaque appel porte son URL complete, donc elle ne sert a rien
 * d'autre qu'a satisfaire le constructeur.
 */
internal fun geminiApi(client: OkHttpClient): GeminiApi = Retrofit.Builder()
    .baseUrl(AiProvider.GEMINI.defaultBaseUrl)
    .client(client)
    .addConverterFactory(AI_JSON.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
    .build()
    .create(GeminiApi::class.java)

/**
 * La troisième et **dernière** : elle sert les quatre fournisseurs qui parlent
 * `chat/completions`.
 *
 * Sa base de façade est celle d'OpenAI, et elle ne veut rien dire — chaque appel porte
 * son URL complète, ce qui est ici la fonctionnalité même : le fournisseur
 * « compatible » n'a pas d'adresse par défaut, et c'est l'utilisateur qui la donne.
 */
internal fun openAiApi(client: OkHttpClient): OpenAiApi = Retrofit.Builder()
    .baseUrl(AiProvider.OPENAI.defaultBaseUrl)
    .client(client)
    .addConverterFactory(AI_JSON.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
    .build()
    .create(OpenAiApi::class.java)

/**
 * **`encodeDefaults` est la ligne qui fait marcher les requêtes.**
 *
 * `kotlinx.serialization` omet par défaut les champs qui valent leur valeur par
 * défaut. Or `"type": "base64"` et `"type": "json_schema"` en sont : sans cette
 * ligne, ils disparaissent du corps envoyé et le fournisseur rend un `400` qui parle
 * d'un champ manquant qu'on croit pourtant écrit. Le défaut ne se voit ni à la
 * compilation ni à la relecture du code Kotlin.
 *
 * `ignoreUnknownKeys` couvre l'autre sens : une réponse gagne des champs sans
 * prévenir, et un champ qu'on ne lit pas ne doit pas faire échouer le décodage de
 * toute l'analyse.
 */
internal val AI_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true

    /**
     * **`encodeDefaults` seul ecrit aussi les `null`**, et c'est le piege que Gemini a
     * revele : ses parts portent `text` **ou** `inlineData`, jamais les deux, et sans
     * cette ligne chaque part partait avec l'autre champ a `null` -- une image
     * accompagnee de `"text":null`. Les deux lignes se lisent comme une seule
     * intention : ecrire les valeurs par defaut, taire les absences.
     */
    explicitNulls = false
}

private const val CONNECT_TIMEOUT_SECONDS = 10L
private const val READ_TIMEOUT_SECONDS = 60L
private const val JSON_MEDIA_TYPE = "application/json"

/**
 * Une panne réseau réduite à l'issue qui la décrit — **écrite, et non faite en silence
 * dans un `catch` vide** — et partagée par les deux fournisseurs, parce que la
 * question « que faire d'un réseau absent » n'a pas deux réponses.
 *
 * La pile de l'exception est perdue, et c'est délibéré : elle ne dit rien que l'issue
 * ne dise, et [docs/05][ia] veut que le détail technique s'arrête ici. Ce que la
 * fonction achète est que la perte soit un geste nommé plutôt qu'un oubli — la forme
 * qu'a déjà prise `ProductLookup.Unreachable` pour Open Food Facts.
 *
 * [ia]: docs/05-ia.md
 */
internal fun IOException.reducedTo(error: AiError): RecognitionOutcome = RecognitionOutcome.Failed(error)
