package app.hexavore.feature.search

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.FoodId
import kotlinx.serialization.Serializable

/**
 * La recherche, comme destination.
 *
 * **C'est le seul point d'entrée d'une saisie**, et la saisie manuelle en est une
 * branche plutôt qu'une porte à côté : un aliment tapé à la main devient une fiche,
 * donc il se cherche, se reprend et se recalcule comme les autres.
 *
 * [addToDraft] dit **à qui rendre le choix**, et c'est la seule chose qui distingue
 * ses deux usages : ouverte depuis l'accueil elle commence un plat, ouverte depuis
 * une saisie en cours elle y ajoute une ligne. L'écran lui-même ne le lit pas — il
 * rend un aliment, et c'est le graphe qui sait quoi en faire.
 */
@Serializable
data class SearchDestination(val addToDraft: Boolean = false)

/**
 * Le formulaire d'aliment personnel, c'est-à-dire la saisie manuelle.
 *
 * Il reçoit le nom déjà tapé dans la recherche. C'est le parcours « aucun résultat »,
 * et retaper « pâtes de mamie » serait la première chose que l'utilisateur
 * reprocherait à cet écran.
 */
@Serializable
data class CustomFoodDestination(
    val name: String = "",
    val addToDraft: Boolean = false,
    /**
     * Le code-barres, quand le formulaire s'ouvre depuis un scan infructueux.
     *
     * C'est ce qui fait tenir « un produit absent d'Open Food Facts se crée à la main
     * **en conservant son code-barres** » : la fiche devient scannable comme n'importe
     * quelle autre, et le produit cesse d'être un cas particulier après une seule
     * saisie.
     */
    val barcode: String? = null,
) {
    internal companion object {
        /**
         * Le nom sous lequel l'argument arrive dans le `SavedStateHandle`.
         *
         * Lu dans la propriété plutôt que recopié : le recopier créerait deux
         * endroits à renommer là où le compilateur n'en signalerait qu'un.
         */
        val NAME: String = CustomFoodDestination::name.name

        /** Idem, pour le code lu quand le formulaire vient d'un scan. */
        val BARCODE: String = CustomFoodDestination::barcode.name
    }
}

/**
 * Les plats favoris, comme destination. Aucun argument.
 *
 * Elle vit dans ce module et non dans un `:feature:favorites` : [docs/02][parcours]
 * range les favoris — aliments **et** plats — dans la modale de sélection, et c'est
 * bien ce que fait cet écran. Un module de plus pour une liste et un champ aurait
 * ajouté un `build.gradle.kts` sans rien séparer.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Serializable
data object FavoritesDestination

/** Ouvre la liste des plats favoris. */
fun NavController.navigateToFavorites() {
    navigate(FavoritesDestination)
}

/** Ouvre la recherche pour commencer un plat. */
fun NavController.navigateToSearch() {
    navigate(SearchDestination(addToDraft = false))
}

/** Ouvre la recherche pour ajouter une ligne au plat en cours de saisie. */
fun NavController.navigateToSearchForDraft() {
    navigate(SearchDestination(addToDraft = true))
}

/** Ouvre la saisie manuelle, nom prérempli. */
fun NavController.navigateToManualEntry(name: String, addToDraft: Boolean) {
    navigate(CustomFoodDestination(name = name, addToDraft = addToDraft))
}

/** Ouvre la saisie manuelle depuis un scan infructueux, code-barres conservé. */
fun NavController.navigateToManualEntryFor(barcode: Barcode) {
    navigate(CustomFoodDestination(barcode = barcode.value))
}

/**
 * Déclare les deux écrans dans un graphe.
 *
 * Le module expose son entrée plutôt que ses composables : `:app` assemble des
 * graphes sans avoir à connaître les arguments de chaque écran.
 *
 * [onPick] reçoit la fiche choisie **et la façon de la rendre**. Les deux chemins —
 * choisir dans le catalogue, ou créer puis utiliser — aboutissent au même endroit,
 * ce qui évite au formulaire de création d'être une impasse.
 */
fun NavGraphBuilder.searchScreens(
    onPick: (FoodId, addToDraft: Boolean) -> Unit,
    onManualEntry: (name: String, addToDraft: Boolean) -> Unit,
    onPickFavorite: (FavoriteDishId) -> Unit,
    onEditFavorite: (FavoriteDishId) -> Unit,
    onClose: () -> Unit,
) {
    composable<FavoritesDestination> {
        FavoritesRoute(onPick = onPickFavorite, onEdit = onEditFavorite, onClose = onClose)
    }
    composable<SearchDestination> { entry ->
        val addToDraft = entry.toRoute<SearchDestination>().addToDraft
        SearchRoute(
            onPick = { foodId -> onPick(foodId, addToDraft) },
            onManualEntry = { name -> onManualEntry(name, addToDraft) },
            onClose = onClose,
        )
    }
    composable<CustomFoodDestination> { entry ->
        val addToDraft = entry.toRoute<CustomFoodDestination>().addToDraft
        CustomFoodRoute(onSaved = { foodId -> onPick(foodId, addToDraft) }, onClose = onClose)
    }
}
