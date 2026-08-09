package app.hexaphore.feature.search

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.hexaphore.domain.food.FoodId
import kotlinx.serialization.Serializable

/**
 * La recherche, comme destination.
 *
 * Aucun argument : elle s'ouvre toujours de la même façon, et ce qu'elle montre
 * avant la frappe ne dépend que du catalogue.
 */
@Serializable
data object SearchDestination

/**
 * Le formulaire d'aliment personnel.
 *
 * Il reçoit le nom déjà tapé dans la recherche. C'est le parcours « aucun résultat »,
 * et retaper « pâtes de mamie » serait la première chose que l'utilisateur
 * reprocherait à cet écran.
 */
@Serializable
data class CustomFoodDestination(val name: String = "") {
    internal companion object {
        /**
         * Le nom sous lequel l'argument arrive dans le `SavedStateHandle`.
         *
         * Lu dans la propriété plutôt que recopié : le recopier créerait deux
         * endroits à renommer là où le compilateur n'en signalerait qu'un.
         */
        val NAME: String = CustomFoodDestination::name.name
    }
}

fun NavController.navigateToSearch() {
    navigate(SearchDestination)
}

/**
 * Déclare les deux écrans dans un graphe.
 *
 * Le module expose son entrée plutôt que ses composables : `:app` assemble des
 * graphes sans avoir à connaître les arguments de chaque écran.
 *
 * [onPick] reçoit la fiche choisie, que ce soit par la recherche ou juste après une
 * création : les deux chemins aboutissent au même endroit, l'écran de validation
 * prérempli. C'est ce qui évite au formulaire de création d'être une impasse.
 */
fun NavGraphBuilder.searchScreens(onPick: (FoodId) -> Unit, onCreate: (String) -> Unit, onClose: () -> Unit) {
    composable<SearchDestination> {
        SearchRoute(onPick = onPick, onCreate = onCreate, onClose = onClose)
    }
    composable<CustomFoodDestination> {
        CustomFoodRoute(onSaved = onPick, onClose = onClose)
    }
}

/** Ouvre le formulaire d'aliment personnel, nom prérempli. */
fun NavController.navigateToCustomFood(name: String) {
    navigate(CustomFoodDestination(name))
}
