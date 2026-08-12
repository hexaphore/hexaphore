package app.hexaphore.integration.openfoodfacts.di

import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.food.ProductSearch
import app.hexaphore.domain.food.ProductSource
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.time.Clock
import app.hexaphore.integration.openfoodfacts.ClientIdentity
import app.hexaphore.integration.openfoodfacts.OPEN_FOOD_FACTS_BASE_URL
import app.hexaphore.integration.openfoodfacts.OpenFoodFactsApi
import app.hexaphore.integration.openfoodfacts.OpenFoodFactsProducts
import app.hexaphore.integration.openfoodfacts.openFoodFactsApi
import app.hexaphore.integration.openfoodfacts.openFoodFactsClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Ce que ce module lie : un port du domaine, une implémentation, et rien qui sorte
 * d'ici.
 *
 * Le client, le convertisseur et l'API restent **internes** : `:app` ne voit que
 * [ProductSource], et un écran encore moins. Exposer `OkHttpClient` aurait fait de ce
 * module la porte d'entrée réseau du projet, et le premier appelant pressé y aurait
 * branché autre chose.
 *
 * Le module ne fait qu'appeler le montage — il est **la portée**, pas la recette.
 *
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object OpenFoodFactsModule {
    @Provides
    @Singleton
    fun client(identity: ClientIdentity): OkHttpClient = openFoodFactsClient(identity)

    @Provides
    @Singleton
    fun api(client: OkHttpClient): OpenFoodFactsApi = openFoodFactsApi(OPEN_FOOD_FACTS_BASE_URL, client)

    /**
     * **Une seule instance pour deux ports.** Les deux interrogent le même service
     * avec le même client ; les séparer côté domaine sert les appelants — l'écran de
     * scan ne voit que le code-barres — pas le nombre d'objets.
     */
    @Provides
    @Singleton
    fun products(
        api: OpenFoodFactsApi,
        ids: IdGenerator,
        clock: Clock,
        dispatchers: DispatcherProvider,
    ): OpenFoodFactsProducts = OpenFoodFactsProducts(api, ids, clock, dispatchers)

    @Provides
    fun productSource(products: OpenFoodFactsProducts): ProductSource = products

    @Provides
    fun productSearch(products: OpenFoodFactsProducts): ProductSearch = products
}
