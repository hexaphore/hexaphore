package app.hexavore.feature.capture

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

/**
 * La modale photo, comme destination.
 *
 * Aucun argument non plus : on arrive avec une assiette devant soi.
 */
@Serializable
data object PhotoDestination

/** Ouvre la modale « Photographier ». */
fun NavController.navigateToPhoto() {
    navigate(PhotoDestination)
}

/**
 * Déclare l'écran dans un graphe.
 *
 * Trois sorties, une de plus que la modale texte : l'échec d'une analyse photo offre
 * **la saisie manuelle**, parce qu'un fournisseur en panne ne doit pas empêcher de
 * noter son repas ([docs/02][parcours]). La description, elle, se corrige et se
 * relance — la phrase est déjà tapée.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
fun NavGraphBuilder.photoScreen(onProposal: () -> Unit, onManual: () -> Unit, onClose: () -> Unit) {
    composable<PhotoDestination> {
        PhotoRoute(onProposal = onProposal, onManual = onManual, onClose = onClose)
    }
}
