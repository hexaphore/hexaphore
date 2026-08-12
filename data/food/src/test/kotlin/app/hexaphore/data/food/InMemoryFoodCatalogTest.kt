package app.hexaphore.data.food

import app.hexaphore.core.testing.InMemoryBarcodeLookup
import app.hexaphore.core.testing.InMemoryFoodCatalog
import app.hexaphore.domain.food.Food

/**
 * Le même contrat, joué sur le faux.
 *
 * Il vit dans ce module et non dans `:core:testing` pour une seule raison, qui est
 * tout l'intérêt du dispositif : les deux implémentations sont **compilées et
 * exécutées côte à côte**, sous la même commande et dans le même rapport. Une
 * divergence n'est plus une chose qu'on découvre sur l'appareil, c'est une ligne
 * rouge à côté d'une verte.
 */
class InMemoryFoodCatalogTest : FoodCatalogContract() {
    override fun catalogue(stored: List<Food>, reference: List<Food>): FoodCatalogView<*> {
        val catalogue = InMemoryFoodCatalog(initial = stored, reference = reference)
        return FoodCatalogView(catalogue, InMemoryBarcodeLookup(catalogue))
    }
}
