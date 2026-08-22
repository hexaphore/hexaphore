package app.hexaphore.integration.openfoodfacts

import app.hexaphore.core.testing.TestDispatchers
import app.hexaphore.domain.food.Barcode
import app.hexaphore.domain.food.ContributionOutcome
import app.hexaphore.domain.food.FoodContribution
import app.hexaphore.domain.food.OffAccount
import app.hexaphore.domain.nutrition.NutrientValues
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URLDecoder

/**
 * L'écriture devant un vrai serveur, sur la boucle locale.
 *
 * **Le corps envoyé est ce qui compte ici**, plus encore qu'en lecture. Un champ mal
 * nommé n'est pas refusé par ce script : il est **accepté et ignoré**, donc la fiche
 * part et arrive vide, et rien dans la réponse ne le dit. C'est le défaut que ces cas
 * existent pour attraper, et qu'aucun faux au niveau de l'interface Retrofit n'aurait
 * pu voir — il aurait éprouvé mes propres noms contre eux-mêmes.
 */
class OpenFoodFactsContributorTest {
    private val server = MockWebServer()

    /** La seconde instance, pour eprouver que la bascule vise bien ailleurs. */
    private val autre = MockWebServer()
    private lateinit var baseUrl: String

    @BeforeEach
    fun start() {
        server.start()
        autre.start()
        baseUrl = server.url("/").toString()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
        autre.shutdown()
    }

    @Test
    fun `une fiche acceptee est envoyee`() = runTest {
        server.enqueue(saved())

        assertEquals(ContributionOutcome.Sent, contributor().contribute(CAPRES, COMPTE))
    }

    @Test
    fun `le corps porte les noms de champs que le service attend`() = runTest {
        // **Un champ mal nomme est accepte et ignore.** La fiche partirait, le service
        // repondrait « fields saved », et elle arriverait vide.
        server.enqueue(saved())

        contributor().contribute(CAPRES, COMPTE)

        val envoye = server.takeRequest().champs()
        assertEquals("3017620422003", envoye["code"])
        assertEquals("Tapenade maison", envoye["product_name"])
        assertEquals("Sans marque", envoye["brands"])
        assertEquals("100g", envoye["nutrition_data_per"])
    }

    @Test
    fun `les six teneurs partent sous leurs identifiants et leurs unites`() = runTest {
        server.enqueue(saved())

        contributor().contribute(CAPRES, COMPTE)

        val envoye = server.takeRequest().champs()
        assertEquals("39", envoye["nutriment_energy-kcal"])
        assertEquals("kcal", envoye["nutriment_energy-kcal_unit"])
        assertEquals("2.18", envoye["nutriment_proteins"])
        assertEquals("g", envoye["nutriment_proteins_unit"])
        assertEquals("3.5", envoye["nutriment_carbohydrates"])
        assertEquals("0.86", envoye["nutriment_fat"])
        assertEquals("3.6", envoye["nutriment_fiber"])
    }

    @Test
    fun `une teneur inconnue ne part pas, et surtout pas a zero`() = runTest {
        // L'envoyer a zero ecrirait dans une base publique une mesure que personne n'a
        // faite. C'est la regle du projet, appliquee la ou elle sort de l'appareil.
        server.enqueue(saved())

        contributor().contribute(CAPRES.copy(per100g = CAPRES.per100g.copy(sugars = null)), COMPTE)

        val envoye = server.takeRequest().champs()
        assertFalse(envoye.containsKey("nutriment_sugars"), "aucune valeur, donc aucun champ")
        assertFalse(envoye.containsKey("nutriment_sugars_unit"))
    }

    @Test
    fun `le compte part avec la fiche`() = runTest {
        server.enqueue(saved())

        contributor().contribute(CAPRES, COMPTE)

        val envoye = server.takeRequest().champs()
        assertEquals("charly", envoye["user_id"])
        assertEquals("secret", envoye["password"])
    }

    @Test
    fun `un compte refuse ne se reessaie pas`() = runTest {
        // Distinct du reseau : reessayer n'y changera rien, il faut corriger le compte.
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(ContributionOutcome.Rejected, contributor().contribute(CAPRES, COMPTE))
        assertEquals(1, server.requestCount, "une ecriture ne se rejoue pas toute seule")
    }

    @Test
    fun `un refus du service garde sa propre phrase`() = runTest {
        server.enqueue(json("""{"status":0,"status_verbose":"no code or invalid code"}"""))

        val issue = contributor().contribute(CAPRES, COMPTE)

        assertEquals(ContributionOutcome.Refused("no code or invalid code"), issue)
    }

    @Test
    fun `un refus muet se dit quand meme`() = runTest {
        // Se taire laisserait devant un echec sans cause ; inventer une raison
        // ferait chercher la mauvaise.
        server.enqueue(json("""{"status":0}"""))

        assertTrue(contributor().contribute(CAPRES, COMPTE) is ContributionOutcome.Refused)
    }

    @Test
    fun `un service en panne est injoignable, pas un refus`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(ContributionOutcome.Unreachable, contributor().contribute(CAPRES, COMPTE))
    }

    @Test
    fun `hors ligne, rien ne part et rien ne plante`() = runTest {
        // Aucune exception ne franchit la frontiere du port : une panne reseau est une
        // reponse possible, pas un accident.
        server.shutdown()
        autre.shutdown()

        assertEquals(ContributionOutcome.Unreachable, contributor().contribute(CAPRES, COMPTE))
    }

    @Test
    fun `une ecriture ne se rejoue jamais toute seule`() = runTest {
        // Contrairement a la lecture, qui a son retrait exponentiel. Une ecriture
        // rejouee sans que personne l'ait demandee est une action sortante de plus.
        server.enqueue(MockResponse().setResponseCode(500))

        contributor().contribute(CAPRES, COMPTE)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `l en-tete d identification part aussi sur une ecriture`() = runTest {
        // Open Food Facts bloque les clients anonymes, et un refus ne ressemble pas a
        // un refus (D26). L'intercepteur vaut pour toutes les requetes, celle-ci
        // comprise -- c'est justement pour ca qu'il est un intercepteur.
        server.enqueue(saved())

        contributor().contribute(CAPRES, COMPTE)

        assertEquals(IDENTITE.userAgent, server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `le bac a sable vise l autre instance, et la vraie base ne recoit rien`() = runTest {
        // La bascule doit prendre effet **sans redemarrer**, sinon la verification
        // qu'elle sert a faire demanderait justement ce qu'on veut eviter.
        autre.enqueue(saved())

        assertEquals(ContributionOutcome.Sent, contributor(sandbox = true).contribute(CAPRES, COMPTE))
        assertEquals(1, autre.requestCount)
        assertEquals(0, server.requestCount, "rien ne part vers la base de production")
    }

    @Test
    fun `sans bascule, rien ne part vers le bac a sable`() = runTest {
        server.enqueue(saved())

        contributor(sandbox = false).contribute(CAPRES, COMPTE)

        assertEquals(0, autre.requestCount)
    }

    @Test
    fun `le chemin vise est celui du script d edition`() = runTest {
        server.enqueue(saved())

        contributor().contribute(CAPRES, COMPTE)

        assertEquals("/$CONTRIBUTION_PATH", server.takeRequest().path)
    }

    /**
     * **Le repartiteur vient du `runTest` courant**, et c'est une extension de
     * [TestScope] pour cette seule raison. Un `TestScope()` neuf porterait un
     * ordonnanceur que personne n'avance : le `withContext` du contributeur ne
     * rendrait jamais la main, et le cas ne finirait pas -- il pendrait.
     */
    private fun TestScope.contributor(sandbox: Boolean = false) = OpenFoodFactsContributor(
        api = contributionApi(baseUrl, openFoodFactsClient(IDENTITE)),
        dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
        sandbox = { sandbox },
        // **Les deux instances pointent sur le serveur local**, et c'est ce qui rend
        // ces cas honnetes. Avec les constantes, le contributeur visait la vraie base
        // d'Open Food Facts : les cas partaient pour de vrai, et attendaient ensuite
        // une requete locale qui n'arrivait jamais.
        liveUrl = baseUrl,
        sandboxUrl = autre.url("/").toString(),
    )

    private fun saved() = json("""{"status":1,"status_verbose":"fields saved"}""")

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    /** Le corps de formulaire, redécoupé en champs. */
    private fun okhttp3.mockwebserver.RecordedRequest.champs(): Map<String, String> = body
        .readUtf8()
        .split('&')
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val (name, value) = pair.split('=', limit = 2)
            URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    private companion object {
        val IDENTITE = ClientIdentity("Hexaphore/0.3 (github.com/hexaphore/hexaphore)")
        val COMPTE = OffAccount(userId = "charly", password = "secret")

        val CAPRES = FoodContribution(
            barcode = checkNotNull(Barcode.of("3017620422003")),
            name = "Tapenade maison",
            brand = "Sans marque",
            per100g = NutrientValues(
                kcal = 39.0,
                protein = 2.18,
                carbs = 3.5,
                sugars = 0.4,
                fat = 0.86,
                fiber = 3.6,
            ),
            servingGrams = 30.0,
        )
    }
}
