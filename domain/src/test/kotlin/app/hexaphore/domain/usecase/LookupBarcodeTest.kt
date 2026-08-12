package app.hexaphore.domain.usecase

import app.hexaphore.core.testing.InMemoryBarcodeLookup
import app.hexaphore.core.testing.InMemoryFoodCatalog
import app.hexaphore.domain.food.Barcode
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.food.ProductLookup
import app.hexaphore.domain.food.ProductSource
import app.hexaphore.domain.nutrition.NutrientValues
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * L'ordre — catalogue d'abord, réseau ensuite — est la fonctionnalité, pas un détail
 * d'implémentation. C'est lui qui rend le deuxième scan instantané et disponible en
 * mode avion, et c'est lui que ces cas éprouvent.
 */
class LookupBarcodeTest {
    @Test
    fun `un produit deja en cache ne fait aucun appel reseau`() = runTest {
        // La promesse « le deuxieme scan est instantane et marche en mode avion ». La
        // source distante refuse tout : si elle est consultee, le cas tombe.
        val catalogue = InMemoryFoodCatalog(initial = listOf(JUS_EN_CACHE))
        val reseau = SourceComptee(ProductLookup.Unreachable)

        val trouve = lookup(catalogue, reseau)(CODE)

        assertEquals(JUS_EN_CACHE.id, (trouve as ProductLookup.Found).food.id)
        assertEquals(0, reseau.appels, "le reseau a ete interroge alors que la fiche etait la")
    }

    @Test
    fun `un produit inconnu du catalogue est recupere puis mis en cache`() = runTest {
        val catalogue = InMemoryFoodCatalog()
        val reseau = SourceComptee(ProductLookup.Found(JUS_PUBLIE))

        val trouve = lookup(catalogue, reseau)(CODE)

        assertTrue(trouve is ProductLookup.Found)
        assertNotNull(InMemoryBarcodeLookup(catalogue).byBarcode(CODE), "la fiche n a pas ete versee au catalogue")
    }

    @Test
    fun `le second scan du meme produit ne repasse pas par le reseau`() = runTest {
        // Le parcours entier, en deux appels : c'est ce que fait quelqu'un qui scanne
        // le meme yaourt deux matins de suite. Un test qui ne jouerait que le premier
        // appel laisserait passer un cache qui n'ecrit rien.
        val catalogue = InMemoryFoodCatalog()
        val reseau = SourceComptee(ProductLookup.Found(JUS_PUBLIE))
        val lookup = lookup(catalogue, reseau)

        lookup(CODE)
        lookup(CODE)

        assertEquals(1, reseau.appels, "le produit a ete redemande au service")
    }

    @Test
    fun `la date de recuperation traverse la mise en cache`() = runTest {
        // Elle est posee par le module qui interroge le service -- lui seul sait quand
        // il l'a fait -- et ce cas verifie qu'elle survit a l'ecriture. Sans elle, le
        // rafraichissement de la tranche 6 n'aurait aucun age a comparer.
        val catalogue = InMemoryFoodCatalog()

        lookup(catalogue, SourceComptee(ProductLookup.Found(JUS_PUBLIE)))(CODE)

        assertEquals(RECUPERE_LE, InMemoryBarcodeLookup(catalogue).byBarcode(CODE)?.fetchedAt)
    }

    @Test
    fun `un produit scanne n entre pas dans les recents`() = runTest {
        // « Recents » dit ce qu'on mange, pas ce qu'on a regarde. Un produit repose
        // sur l'etagere aussi souvent qu'il finit dans un plat.
        val catalogue = InMemoryFoodCatalog()

        lookup(catalogue, SourceComptee(ProductLookup.Found(JUS_PUBLIE)))(CODE)

        assertNull(InMemoryBarcodeLookup(catalogue).byBarcode(CODE)?.lastUsedAt, "le scan a compte comme un usage")
    }

    @Test
    fun `la fiche rendue est celle du catalogue, avec son identifiant definitif`() = runTest {
        // Celle que le service publie porte un identifiant provisoire. Rendre
        // celui-la ferait ecrire une entree de journal qui designe une fiche absente,
        // et la base la refuserait -- au moment de l'enregistrement, pas du scan.
        val catalogue = InMemoryFoodCatalog()
        val lookup = lookup(catalogue, SourceComptee(ProductLookup.Found(JUS_PUBLIE)))

        val rendue = (lookup(CODE) as ProductLookup.Found).food

        assertEquals(rendue.id, catalogue.byId(rendue.id)?.id)
    }

    @Test
    fun `un code que le service ne connait pas reste inconnu, et rien n est ecrit`() = runTest {
        val catalogue = InMemoryFoodCatalog()

        val trouve = lookup(catalogue, SourceComptee(ProductLookup.Unknown))(CODE)

        assertEquals(ProductLookup.Unknown, trouve)
        assertTrue(catalogue.all.isEmpty(), "une fiche a ete inventee pour un produit absent")
    }

    @Test
    fun `un service injoignable se distingue d un produit absent`() = runTest {
        // Les deux invitent a creer l'aliment a la main, mais l'un dit que la question
        // reste posee. Les confondre annoncerait une absence qu'on n'a pas verifiee.
        val catalogue = InMemoryFoodCatalog()

        val trouve = lookup(catalogue, SourceComptee(ProductLookup.Unreachable))(CODE)

        assertEquals(ProductLookup.Unreachable, trouve)
        assertTrue(catalogue.all.isEmpty())
    }

    // --- Decor -------------------------------------------------------------------

    private fun lookup(catalogue: InMemoryFoodCatalog, reseau: ProductSource) = LookupBarcode(
        catalogue = InMemoryBarcodeLookup(catalogue),
        products = reseau,
        store = catalogue,
    )

    /** Une source distante qui compte combien de fois on l'a dérangée. */
    private class SourceComptee(private val reponse: ProductLookup) : ProductSource {
        var appels = 0
            private set

        override suspend fun byBarcode(code: Barcode): ProductLookup {
            appels++
            return reponse
        }
    }

    private companion object {
        val CODE: Barcode = requireNotNull(Barcode.of("5449000000996"))
        val RECUPERE_LE: Instant = Instant.parse("2026-08-12T09:00:00Z")

        /** Telle que le service la publie : identifiant provisoire, date de récupération posée. */
        val JUS_PUBLIE = Food(
            id = FoodId("provisoire"),
            source = FoodSource.OFF,
            sourceRef = CODE.value,
            name = "Boisson gazeuse",
            per100g = NutrientValues(kcal = 42.0),
            isLiquid = true,
            fetchedAt = RECUPERE_LE,
        )

        val JUS_EN_CACHE = JUS_PUBLIE.copy(id = FoodId("f-jus"))
    }
}
