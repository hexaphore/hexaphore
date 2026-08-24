package app.hexavore.feature.weight

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Le journal de poids, comme destination. Aucun argument : il n'y en a qu'un. */
@Serializable
data object WeightDestination

fun NavController.navigateToWeight() = navigate(WeightDestination)

/** Déclare le journal de poids dans un graphe. */
fun NavGraphBuilder.weightScreen(onClose: () -> Unit) {
    composable<WeightDestination> { WeightRoute(onClose = onClose) }
}
