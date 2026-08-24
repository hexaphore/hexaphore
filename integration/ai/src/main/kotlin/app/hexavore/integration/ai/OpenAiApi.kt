package app.hexavore.integration.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * `/v1/chat/completions`, pour les quatre fournisseurs qui le parlent.
 *
 * **Une seule interface**, parce que c'est un seul protocole : ce qui change d'un
 * fournisseur à l'autre est l'URL de base et le modèle, tous deux saisis par
 * l'utilisateur. La clé voyage en `Authorization: Bearer`, le troisième en-tête que
 * l'intercepteur de redaction masque — déclaré dès la première livraison, précisément
 * pour que celle-ci n'ait rien à ajouter.
 */
internal interface OpenAiApi {
    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: OpenAiRequest,
    ): Response<OpenAiResponse>
}
