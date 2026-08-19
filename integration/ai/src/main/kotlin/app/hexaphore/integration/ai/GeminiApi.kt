package app.hexaphore.integration.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * L'unique point d'entrée de Gemini.
 *
 * **Le modèle est dans l'URL**, pas dans le corps — `models/{modèle}:generateContent`.
 * C'est la deuxième façon dont ce fournisseur diffère du premier, et c'est pourquoi
 * l'URL complète se construit à l'appel plutôt que de vivre dans une base figée.
 *
 * La clé voyage en `x-goog-api-key`, l'un des trois en-têtes que l'intercepteur de
 * redaction masque — déclarés tous les trois dès la première livraison, précisément
 * pour que celui-ci n'ait rien à ajouter.
 */
internal interface GeminiApi {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest,
    ): Response<GeminiResponse>
}
