package app.hexavore.domain.usecase

import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.BarcodeLookup
import app.hexavore.domain.food.FoodStore
import app.hexavore.domain.food.ProductLookup
import app.hexavore.domain.food.ProductSource

/**
 * Ce qu'un code-barres désigne : le catalogue d'abord, le réseau ensuite.
 *
 * **L'ordre est la fonctionnalité.** C'est lui qui tient les deux promesses de la
 * tranche — une fiche en moins de deux secondes au premier scan, et un deuxième scan
 * instantané qui marche en mode avion. Interroger le service d'abord les perdrait
 * toutes les deux, sans qu'aucun test de correspondance ne s'en aperçoive.
 *
 * **Une fiche récupérée est versée au catalogue tout de suite**, avant même qu'un plat
 * la cite. C'est un écart délibéré avec la règle des aliments de la table de l'ANSES,
 * qui n'y entrent qu'à l'enregistrement du plat ([D50][decisions]) : là-bas, la
 * source est embarquée et toujours disponible, ici elle est à l'autre bout d'un
 * réseau. Ne pas écrire reviendrait à redemander le même produit à chaque scan.
 *
 * **La date de récupération est posée par le module qui interroge le service**, pas
 * ici : c'est lui qui sait quand il l'a fait, et la recherche par nom l'a montré en
 * ajoutant un second chemin de récupération que ce cas d'usage ne traverse pas.
 *
 * Elle n'apparaît pas pour autant dans « Récents » : `last_used_at` reste nul tant que
 * rien n'a été mangé, et cette liste dit ce qu'on mange, pas ce qu'on a regardé.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md
 */
class LookupBarcode(
    private val catalogue: BarcodeLookup,
    private val products: ProductSource,
    private val store: FoodStore,
) {
    suspend operator fun invoke(code: Barcode): ProductLookup =
        catalogue.byBarcode(code)?.let(ProductLookup::Found) ?: fetch(code)

    /**
     * La fiche récupérée, versée au catalogue, et rendue **telle qu'elle y est**.
     *
     * `place` rend la fiche stockée et non celle qu'on lui passe : l'appelant récupère
     * ainsi l'identifiant définitif, sans lequel une entrée de journal désignerait une
     * fiche absente et la base la refuserait ([D51][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    private suspend fun fetch(code: Barcode): ProductLookup = when (val found = products.byBarcode(code)) {
        is ProductLookup.Found -> ProductLookup.Found(store.place(found.food))
        else -> found
    }
}
