package app.hexaphore.integration.openfoodfacts

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Le montage HTTP, écrit **une seule fois**.
 *
 * Il vit ici plutôt que dans le module Hilt pour que le test puisse assembler
 * exactement la même pile devant un serveur local. Recopié dans le test, il aurait
 * éprouvé un client qui ressemble au vrai — et l'en-tête `User-Agent`, dont [D26]
 * dit qu'il est obligatoire, aurait pu manquer en production avec un test vert :
 * c'est la forme de défaut que [D53] a nommée et que ce projet ne veut plus payer.
 *
 * @see docs/11-decisions.md
 */
internal fun openFoodFactsClient(identity: ClientIdentity): OkHttpClient = OkHttpClient.Builder()
    // Plus courts que les dix secondes par defaut d'OkHttp : la promesse de la
    // tranche est une fiche en moins de deux secondes, et un reseau qui n'a pas
    // repondu en cinq ne repondra pas. Dire « pas de connexion » vaut mieux que
    // faire attendre quelqu'un debout devant un rayon.
    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .addInterceptor(UserAgentInterceptor(identity))
    .build()

internal fun openFoodFactsApi(baseUrl: String, client: OkHttpClient): OpenFoodFactsApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(LENIENT_JSON.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
    .build()
    .create(OpenFoodFactsApi::class.java)

private const val CONNECT_TIMEOUT_SECONDS = 5L
private const val READ_TIMEOUT_SECONDS = 5L
private const val JSON_MEDIA_TYPE = "application/json"

/**
 * `ignoreUnknownKeys` n'est pas une facilité : la fiche complète d'un produit porte
 * des centaines de clés, la requête n'en demande que sept, et le service en ajoute
 * sans prévenir. Sans cette ligne, une clé nouvelle ferait échouer le décodage de
 * **toutes** les fiches d'un coup — une panne totale pour un champ qu'on ne lit pas.
 */
internal val LENIENT_JSON = Json { ignoreUnknownKeys = true }
