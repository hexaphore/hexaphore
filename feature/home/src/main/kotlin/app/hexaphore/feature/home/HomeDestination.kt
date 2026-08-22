package app.hexaphore.feature.home

import androidx.compose.runtime.Immutable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.hexaphore.domain.diary.DishId
import kotlinx.serialization.Serializable

/** L'accueil, comme destination. Aucun argument : c'est l'écran de départ. */
@Serializable
data object HomeDestination

/**
 * Les sorties de l'accueil, en un seul objet.
 *
 * **Des sorties et non des destinations** : le module ne sait pas vers quoi il envoie,
 * ce qui lui évite de dépendre de `:feature:entry`, de `:feature:capture`, de
 * `:feature:onboarding` ni de `:feature:settings`. C'est `:app` qui relie, parce que
 * c'est lui qui assemble.
 *
 * Rassemblées le jour où les quatre modes de saisie ont été là : huit rappels passés
 * un par un font une signature qu'on ne lit plus, et c'est la forme que le projet a
 * déjà retenue pour [HomeActions].
 */
@Immutable
data class HomeRoutes(
    val onAddDish: () -> Unit,
    val onScan: () -> Unit,
    val onDescribe: () -> Unit,
    val onPhotograph: () -> Unit,
    val onEditDish: (DishId) -> Unit,
    val onSetUpGoal: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenFavorites: () -> Unit,
)

/**
 * Une journee passee, relue.
 *
 * La date voyage en texte ISO plutot qu'en `LocalDate` : une route est serialisee
 * dans l'etat de navigation, et `AAAA-MM-JJ` s'y relit sans convertisseur -- son
 * ordre lexicographique est d'ailleurs son ordre chronologique, ce dont la requete
 * de plage se sert deja.
 */
@Serializable
data class DayDestination(val date: String) {
    companion object {
        /** Le nom de l'argument, partage par la route et le `SavedStateHandle`. */
        const val DATE = "date"
    }
}

/** Declare l'accueil et la journee relue dans un graphe. Le mois vit dans l'accueil. */
fun NavGraphBuilder.homeScreen(routes: HomeRoutes, navController: NavController) {
    composable<HomeDestination> {
        HomeRoute(
            routes = routes,
            onOpenDay = { date -> navController.navigate(DayDestination(date.toString())) },
        )
    }
    composable<DayDestination> {
        DayRoute(routes = routes, onClose = { navController.popBackStack() })
    }
}
