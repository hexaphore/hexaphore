package app.hexaphore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.hexaphore.feature.entry.entryScreen
import app.hexaphore.feature.entry.navigateToEntry
import app.hexaphore.feature.home.HomeDestination
import app.hexaphore.feature.home.homeScreen

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
            onEditDish = { dishId -> navController.navigateToEntry(dishId) },
        )
        entryScreen(onClose = { navController.popBackStack() })
    }
}
