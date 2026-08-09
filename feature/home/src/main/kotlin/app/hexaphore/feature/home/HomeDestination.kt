package app.hexaphore.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.hexaphore.domain.diary.DishId
import kotlinx.serialization.Serializable

/** L'accueil, comme destination. Aucun argument : c'est l'écran de départ. */
@Serializable
data object HomeDestination

/**
 * Déclare l'accueil dans un graphe.
 *
 * Les deux rappels sont des sorties et non des destinations : le module ne sait pas
 * vers quoi il envoie, ce qui lui évite de dépendre de `:feature:entry`. C'est
 * `:app` qui relie, parce que c'est lui qui assemble.
 */
fun NavGraphBuilder.homeScreen(onAddDish: () -> Unit, onSearchFood: () -> Unit, onEditDish: (DishId) -> Unit) {
    composable<HomeDestination> {
        HomeRoute(onAddDish = onAddDish, onSearchFood = onSearchFood, onEditDish = onEditDish)
    }
}
