package app.hexaphore.feature.capture

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * La modale texte, comme destination.
 *
 * Aucun argument : on arrive avec une phrase à écrire, pas avec une intention.
 */
@Serializable
data object DescribeDestination

/** Ouvre la modale « Décrire ». */
fun NavController.navigateToDescribe() {
    navigate(DescribeDestination)
}

/**
 * Déclare l'écran dans un graphe.
 *
 * Deux sorties seulement, et la première ne porte rien : ce que l'analyse a produit
 * attend dans le dépôt des propositions, parce qu'une route ne transporte pas cinq
 * lignes. La modale s'efface derrière la validation, comme le scan et la recherche.
 */
fun NavGraphBuilder.describeScreen(onProposal: () -> Unit, onClose: () -> Unit) {
    composable<DescribeDestination> {
        DescribeRoute(onProposal = onProposal, onClose = onClose)
    }
}
