package app.hexaphore.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * Les réglages du profil, comme destination. Aucun argument.
 *
 * Il n'y a **pas** d'écran « Réglages » au-dessus, et ce n'est pas un oubli : les
 * quatre autres sections que [docs/02][parcours] prévoit dépendent des tranches 6 et 8.
 * Un écran de transit qui ne désigne qu'une seule destination est un écran de trop, et
 * quatre entrées qui n'ouvrent rien ne sont pas une avance ([D59][decisions]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Serializable
data object ProfileDestination

fun NavController.navigateToProfile() = navigate(ProfileDestination)

/**
 * Déclare les réglages profil dans un graphe.
 *
 * Le rappel est une sortie et non une destination : le module ne sait pas vers quoi il
 * revient, ce qui lui évite de dépendre de `:feature:home`.
 */
fun NavGraphBuilder.profileScreen(onClose: () -> Unit) {
    composable<ProfileDestination> {
        ProfileRoute(onClose = onClose)
    }
}
