package app.hexaphore.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Les cinq étapes, comme destination. Aucun argument. */
@Serializable
data object OnboardingDestination

/**
 * Déclare l'onboarding dans un graphe.
 *
 * Le rappel est une sortie et non une destination : le module ne sait pas vers quoi
 * il renvoie, ce qui lui évite de dépendre de `:feature:home`.
 */
fun NavGraphBuilder.onboardingScreen(onDone: () -> Unit) {
    composable<OnboardingDestination> {
        OnboardingRoute(onDone = onDone)
    }
}
