package app.hexaphore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.hexaphore.feature.entry.entryScreen
import app.hexaphore.feature.entry.navigateToEntry
import app.hexaphore.feature.entry.navigateToEntryFor
import app.hexaphore.feature.home.HomeDestination
import app.hexaphore.feature.home.homeScreen
import app.hexaphore.feature.search.navigateToCustomFood
import app.hexaphore.feature.search.navigateToSearch
import app.hexaphore.feature.search.searchScreens

/**
 * Le graphe de navigation.
 *
 * Il vit dans `:app` parce que c'est le seul module qui a le droit de connaître
 * tous les autres. Chaque `:feature` déclare sa destination et ses sorties ; aucun
 * ne sait vers quoi il envoie, ce qui les laisse indépendants les uns des autres.
 *
 * Routes typées : une destination est une `data class` sérialisable, et un argument
 * oublié devient une erreur de compilation plutôt qu'un `null` à l'exécution.
 *
 * @see docs/06-architecture.md
 */
@Composable
fun HexaphoreNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeDestination, modifier = modifier) {
        homeScreen(
            onAddDish = { navController.navigateToEntry() },
            onSearchFood = { navController.navigateToSearch() },
            onEditDish = { dishId -> navController.navigateToEntry(dishId) },
        )
        entryScreen(onClose = { navController.popBackStack() })
        searchScreens(
            // La recherche s'efface derriere la validation : revenir en arriere
            // depuis un plat en cours doit rendre l'accueil, pas une liste de
            // resultats qu'on a deja quittee.
            onPick = { foodId ->
                navController.popBackStack(HomeDestination, inclusive = false)
                navController.navigateToEntryFor(foodId)
            },
            onCreate = { name -> navController.navigateToCustomFood(name) },
            onClose = { navController.popBackStack() },
        )
    }
}
