package app.hexaphore.core.testing

import app.hexaphore.domain.food.Barcode
import app.hexaphore.domain.food.BarcodeLookup
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodSource

/**
 * Le catalogue en mémoire, vu par le scanner.
 *
 * À part d'[InMemoryFoodCatalog] pour la même raison que son pendant Room : cette
 * lecture est la seule qui ait à savoir que `sourceRef` range deux espaces de noms
 * dans un même champ.
 *
 * **Il lit la réserve vivante et non une copie** : une fiche versée au catalogue juste
 * avant doit s'y retrouver, sans quoi le cas d'usage du scan serait éprouvé sur un
 * faux qui ne voit pas ses propres écritures.
 *
 * Les deux règles ci-dessous ne se devinent ni l'une ni l'autre, et c'est pour ça que
 * le contrat les éprouve des deux côtés.
 *
 * **Il échoue quand le catalogue est déclaré illisible**, comme `RoomBarcodeLookup`
 * échouerait sur une base qu'on ne peut pas ouvrir. Sans cela, un test qui prétend
 * éprouver ce cas mesure autre chose : il tombe sur un catalogue simplement vide, et
 * c'est la source distante qui répond à sa place.
 */
class InMemoryBarcodeLookup(private val catalogue: InMemoryFoodCatalog) : BarcodeLookup {
    override suspend fun byBarcode(code: Barcode): Food? {
        check(!catalogue.failure) { "Catalogue illisible" }
        return byBarcodeIn(catalogue.all, code)
    }
}

private fun byBarcodeIn(catalogue: List<Food>, code: Barcode): Food? = catalogue
    // Un code de la table de l'ANSES n'est pas un code-barres.
    .filter { it.sourceRef == code.value && it.source != FoodSource.CIQUAL }
    // Ce que l'utilisateur a saisi lui-meme passe devant un produit en cache.
    .minByOrNull { if (it.source == FoodSource.CUSTOM) 0 else 1 }
