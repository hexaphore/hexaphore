package app.hexavore.integration.openfoodfacts

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * L'écriture, sur un point d'entrée qui n'est pas celui de la lecture.
 *
 * **Une interface distincte de [OpenFoodFactsApi]**, et pas par symétrie : celle-là
 * décrit l'API v2 en lecture, celle-ci un script d'édition hérité — un chemin
 * différent, un encodage différent, et une authentification que la lecture n'a jamais
 * demandée. Les mêler ferait croire que ce sont deux facettes du même contrat.
 *
 * **L'URL est un paramètre** plutôt que la `baseUrl` du client. Retrofit fige sa base
 * à la construction, or l'envoi doit pouvoir viser le bac à sable **ou** la vraie
 * base selon un réglage que l'utilisateur bascule sans redémarrer ([D90][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
internal interface ContributionApi {
    /**
     * Ajoute ou met à jour un produit.
     *
     * **Encodage de formulaire et non JSON** : ce script attend des champs plats, et
     * c'est ce que sa documentation montre. Le corps porte l'identifiant et le mot de
     * passe au même titre que les valeurs nutritionnelles — c'est ce que le service
     * prévoit, et c'est pourquoi cet appel ne part jamais en clair.
     *
     * Un [Response] et non le corps décodé, comme en lecture : un refus
     * d'authentification et un refus de contenu n'appellent pas la même conduite, et
     * seul le code HTTP les sépare.
     */
    @FormUrlEncoded
    @POST
    suspend fun contribute(@Url url: String, @FieldMap fields: Map<String, String>): Response<ContributionEnvelope>
}

/**
 * Ce que le service répond à un envoi.
 *
 * `status` vaut 1 quand la fiche est passée. `status_verbose` porte sa propre phrase
 * — « fields saved » en cas de succès, la raison du refus sinon — et c'est elle qu'on
 * montre plutôt qu'un message inventé : le service garde la parole ([D78][decisions]).
 *
 * Les deux champs ont une valeur par défaut : une réponse tronquée ou d'une forme
 * inattendue se lit alors comme un refus sans raison, et non comme un plantage.
 *
 * [decisions]: docs/11-decisions.md
 */
@Serializable
internal data class ContributionEnvelope(
    val status: Int = 0,
    @kotlinx.serialization.SerialName("status_verbose") val statusVerbose: String = "",
)

/** Le chemin du script d'édition, relatif à l'instance visée. */
internal const val CONTRIBUTION_PATH = "cgi/product_jqm2.pl"

/**
 * L'instance de test d'Open Food Facts.
 *
 * Le même logiciel et la même API sur un jeu de données jetable. C'est là que se
 * vérifie qu'un envoi aboutit vraiment — **la première écriture sortante de
 * l'application, et personne ne l'a jamais vue partir** ([D90][decisions]). Un
 * premier essai mal formé y reste sans conséquence, là où il laisserait sur la vraie
 * base une fiche publique que d'autres relisent.
 *
 * [decisions]: docs/11-decisions.md
 */
internal const val OPEN_FOOD_FACTS_SANDBOX_URL = "https://world.openfoodfacts.net/"
