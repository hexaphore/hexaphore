package app.hexaphore.feature.scan

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.hexaphore.domain.food.Barcode
import app.hexaphore.domain.food.FoodId
import kotlinx.serialization.Serializable

/**
 * Le scan, comme destination. Aucun argument : on arrive avec la caméra, pas avec une
 * intention.
 */
@Serializable
data object ScanDestination

/** Ouvre la modale de scan. */
fun NavController.navigateToScan() {
    navigate(ScanDestination)
}

/**
 * Déclare l'écran dans un graphe.
 *
 * Trois sorties, et elles disent le parcours de [docs/02][parcours] : le produit
 * trouvé part vers la validation, le produit absent vers la création — **avec son
 * code** —, et « chercher par nom » vers la recherche ordinaire.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
fun NavGraphBuilder.scanScreen(
    onProduct: (FoodId) -> Unit,
    onCreateFood: (Barcode) -> Unit,
    onSearchByName: () -> Unit,
    onClose: () -> Unit,
) {
    composable<ScanDestination> {
        ScanRoute(
            onProduct = onProduct,
            onCreateFood = onCreateFood,
            onSearchByName = onSearchByName,
            onClose = onClose,
        )
    }
}
