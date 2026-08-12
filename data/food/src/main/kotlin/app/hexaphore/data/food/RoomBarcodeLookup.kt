package app.hexaphore.data.food

import app.hexaphore.core.database.dao.FoodDao
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.food.Barcode
import app.hexaphore.domain.food.BarcodeLookup
import app.hexaphore.domain.food.Food
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Le catalogue vu par le scanner.
 *
 * **À part de [RoomFoodCatalog], et pas seulement pour tenir un seuil.** `source_ref`
 * range deux espaces de noms dans une même colonne — des codes de la table de l'ANSES
 * et des codes-barres — et cette lecture est la **seule** qui ait à le savoir : c'est
 * elle qui porte la clause qui écarte `CIQUAL`. Les six autres capacités du catalogue
 * traitent cette colonne comme opaque. Une classe pour une requête nomme la couture au
 * lieu de la cacher au milieu d'un objet qui fait six autres choses.
 *
 * **Aucune portion n'est relue.** Un produit scanné n'en a pas dans la table de
 * l'ANSES, et sa portion d'emballage vit déjà dans `default_serving_g`.
 *
 * @see docs/06-architecture.md
 */
class RoomBarcodeLookup @Inject constructor(private val dao: FoodDao, private val dispatchers: DispatcherProvider) :
    BarcodeLookup {
    override suspend fun byBarcode(code: Barcode): Food? =
        withContext(dispatchers.io) { dao.byBarcode(code.value)?.toDomain() }
}
