package app.hexaphore.integration.openfoodfacts

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * L'API v2 d'Open Food Facts, réduite à ce que l'application demande.
 *
 * Elle rend un [Response] et non le corps décodé : un code inconnu se signale par un
 * `404`, et un service surchargé par un `429` ou un `5xx`. Ces trois-là appellent
 * trois conduites différentes, qu'un corps seul ne permettrait pas de distinguer —
 * Retrofit lèverait une exception pour les trois.
 */
internal interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun product(@Path("barcode") barcode: String, @Query("fields") fields: String): Response<ProductEnvelope>
}

/**
 * Les champs demandés, et rien de plus.
 *
 * Une fiche complète d'Open Food Facts pèse plusieurs dizaines de kilo-octets ;
 * celle-ci en pèse quelques centaines d'octets, et c'est ce qui tient le budget de
 * deux secondes sur une connexion mobile. Les champs absents de cette liste sont
 * absents du modèle : en demander un que rien ne lit serait le même travers qu'une
 * colonne que rien ne remplit.
 */
internal const val PRODUCT_FIELDS =
    "code,product_name,product_name_fr,brands,serving_size,serving_quantity,nutriments"

/** L'instance publique, la francophone. */
internal const val OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org/"
