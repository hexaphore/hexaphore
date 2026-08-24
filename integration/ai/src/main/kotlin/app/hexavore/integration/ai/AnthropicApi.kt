package app.hexavore.integration.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * L'unique point d'entrée qu'Anthropic nous demande.
 *
 * **L'URL est un paramètre et non une base figée.** Retrofit fixe son `baseUrl` à la
 * construction, alors que la nôtre est un réglage : l'utilisateur peut la changer
 * pour passer par un relais. Un `Retrofit` reconstruit à chaque appel aurait fait le
 * même travail en jetant le pool de connexions à chaque analyse.
 *
 * `anthropic-version` est déclaré ici plutôt que dans un intercepteur : il est propre
 * à ce fournisseur, et l'intercepteur est partagé par les six.
 */
internal interface AnthropicApi {
    @Headers("anthropic-version: $ANTHROPIC_VERSION")
    @POST
    suspend fun messages(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Body request: AnthropicRequest,
    ): Response<AnthropicResponse>
}

/** La version d'API, figée : elle ne suit pas celle des modèles. */
private const val ANTHROPIC_VERSION = "2023-06-01"
