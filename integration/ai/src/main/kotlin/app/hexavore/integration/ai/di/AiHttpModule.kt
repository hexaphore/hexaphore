package app.hexavore.integration.ai.di

import app.hexavore.integration.ai.AnthropicApi
import app.hexavore.integration.ai.GeminiApi
import app.hexavore.integration.ai.NetworkLog
import app.hexavore.integration.ai.OpenAiApi
import app.hexavore.integration.ai.aiClient
import app.hexavore.integration.ai.anthropicApi
import app.hexavore.integration.ai.geminiApi
import app.hexavore.integration.ai.openAiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

/**
 * La pile HTTP des fournisseurs d'IA : un client, trois interfaces.
 *
 * **Séparé de [AiModule]** parce que ce sont deux choses différentes — ici le
 * transport, là les ports du domaine et la fabrique qui les sert. Le découpage est
 * arrivé quand le seuil de fonctions a mordu, et il suit ce que les choses sont plutôt
 * qu'un compte : c'est la réponse du projet à ce seuil, jamais de le relever.
 *
 * **Trois interfaces pour six fournisseurs**, et c'est le bon compte : trois protocoles
 * existent.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AiHttpModule {
    @Provides
    @Singleton
    @Named(AI_CLIENT)
    fun client(log: NetworkLog): OkHttpClient = aiClient(log)

    @Provides
    @Singleton
    fun anthropic(@Named(AI_CLIENT) client: OkHttpClient): AnthropicApi = anthropicApi(client)

    @Provides
    @Singleton
    fun gemini(@Named(AI_CLIENT) client: OkHttpClient): GeminiApi = geminiApi(client)

    @Provides
    @Singleton
    fun openAi(@Named(AI_CLIENT) client: OkHttpClient): OpenAiApi = openAiApi(client)

    /**
     * Les trois interfaces, en un objet.
     *
     * Passées une par une à la fabrique, elles poussaient sa fonction au-delà du seuil
     * de paramètres.
     */
    @Provides
    @Singleton
    fun apis(anthropic: AnthropicApi, gemini: GeminiApi, openAi: OpenAiApi) = AiApis(anthropic, gemini, openAi)
}

/** Les trois protocoles que les six fournisseurs se partagent. */
internal data class AiApis(val anthropic: AnthropicApi, val gemini: GeminiApi, val openAi: OpenAiApi)

/**
 * Le qualificatif qui sépare ce client de celui d'Open Food Facts.
 *
 * Les deux modules fournissent un `OkHttpClient` dans le même composant, et sans
 * qualificatif Dagger refuse de choisir. Les partager serait pire que les séparer :
 * ils n'ont ni les mêmes délais ni les mêmes intercepteurs, et le `User-Agent`
 * d'Open Food Facts n'a rien à faire dans un appel payant.
 */
internal const val AI_CLIENT = "ai"
