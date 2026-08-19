package app.hexaphore.integration.ai.di

import android.content.Context
import app.hexaphore.domain.ai.AiProbe
import app.hexaphore.domain.ai.AiSettings
import app.hexaphore.domain.ai.FoodRecognizer
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.integration.ai.AnthropicApi
import app.hexaphore.integration.ai.AnthropicRecognizer
import app.hexaphore.integration.ai.AssetSystemPrompt
import app.hexaphore.integration.ai.ConfiguredRecognizer
import app.hexaphore.integration.ai.GeminiApi
import app.hexaphore.integration.ai.GeminiRecognizer
import app.hexaphore.integration.ai.NetworkLog
import app.hexaphore.integration.ai.SystemPrompt
import app.hexaphore.integration.ai.aiClient
import app.hexaphore.integration.ai.anthropicApi
import app.hexaphore.integration.ai.geminiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

/**
 * Ce que ce module lie : un port du domaine, et rien qui sorte d'ici.
 *
 * `:app` ne voit que [FoodRecognizer]. Le client, les DTO et les fournisseurs restent
 * internes — exposer `OkHttpClient` aurait fait de ce module la porte d'entrée réseau
 * du projet, et le premier appelant pressé y aurait branché autre chose.
 *
 * **[NetworkLog] vient de `:app`**, comme `ClientIdentity` pour Open Food Facts :
 * c'est l'application qui sait si elle est en `debug`, et [docs/05][ia] veut qu'un
 * journal réseau détaillé n'existe que là. Ce module n'a pas à connaître les
 * variantes de build.
 *
 * [ia]: docs/05-ia.md
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AiModule {
    @Provides
    @Singleton
    @Named(AI_CLIENT)
    fun client(log: NetworkLog): OkHttpClient = aiClient(log)

    @Provides
    @Singleton
    fun api(@Named(AI_CLIENT) client: OkHttpClient): AnthropicApi = anthropicApi(client)

    @Provides
    @Singleton
    fun gemini(@Named(AI_CLIENT) client: OkHttpClient): GeminiApi = geminiApi(client)

    @Provides
    @Singleton
    fun systemPrompt(@ApplicationContext context: Context): SystemPrompt = AssetSystemPrompt(context)

    /**
     * **Une seule instance pour deux ports.** Analyser et sonder empruntent le même
     * chemin — c'est ce qui rend le bouton « Tester » digne de confiance —, et deux
     * objets auraient fini par diverger d'un délai ou d'une traduction de code.
     */
    @Provides
    @Singleton
    fun configured(
        settings: AiSettings,
        api: AnthropicApi,
        geminiApi: GeminiApi,
        prompt: SystemPrompt,
        dispatchers: DispatcherProvider,
    ): ConfiguredRecognizer = ConfiguredRecognizer(
        settings = settings,
        anthropic = AnthropicRecognizer(api, prompt, dispatchers),
        gemini = GeminiRecognizer(geminiApi, prompt, dispatchers),
    )

    @Provides
    fun recognizer(configured: ConfiguredRecognizer): FoodRecognizer = configured

    @Provides
    fun probe(configured: ConfiguredRecognizer): AiProbe = configured
}

/**
 * Le qualificatif qui sépare ce client de celui d'Open Food Facts.
 *
 * Les deux modules fournissent un `OkHttpClient` dans le même composant, et sans
 * qualificatif Dagger refuse de choisir. Les partager serait pire que les séparer :
 * ils n'ont ni les mêmes délais ni les mêmes intercepteurs, et le `User-Agent`
 * d'Open Food Facts n'a rien à faire dans un appel payant.
 */
private const val AI_CLIENT = "ai"
