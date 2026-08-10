package app.hexaphore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.hexaphore.feature.entry.EntryDestination
import app.hexaphore.feature.entry.entryScreen
import app.hexaphore.feature.entry.navigateToEntry
import app.hexaphore.feature.entry.navigateToEntryFor
import app.hexaphore.feature.home.HomeDestination
import app.hexaphore.feature.home.homeScreen
import app.hexaphore.feature.onboarding.OnboardingDestination
import app.hexaphore.feature.onboarding.onboardingScreen
import app.hexaphore.feature.search.navigateToManualEntry
import app.hexaphore.feature.search.navigateToSearch
import app.hexaphore.feature.search.navigateToSearchForDraft
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
            // Un seul bouton, et il ouvre la recherche : la saisie manuelle y est
            // une branche, puisqu'un aliment tape a la main devient une fiche.
            onAddDish = { navController.navigateToSearch() },
            onEditDish = { dishId -> navController.navigateToEntry(dishId) },
            onSetUpGoal = { navController.navigate(OnboardingDestination) },
        )
        onboardingScreen(
            // L'onboarding s'efface derriere l'accueil : y revenir par le bouton
            // « retour » du systeme reposerait cinq questions auxquelles on vient de
            // repondre.
            onDone = { navController.popBackStack(HomeDestination, inclusive = false) },
        )
        entryScreen(
            onAddFood = { navController.navigateToSearchForDraft() },
            onClose = { navController.popBackStack() },
        )
        searchScreens(
            onPick = { foodId, addToDraft ->
                if (addToDraft) {
                    // Le choix revient a l'ecran qui l'a demande, par le canal que
                    // la navigation prevoit pour un resultat. Ouvrir une seconde
                    // validation aurait perdu le plat en cours.
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(EntryDestination.PICKED_FOOD, foodId.value)
                    navController.popBackStack()
                } else {
                    // La recherche s'efface derriere la validation : revenir en
                    // arriere depuis un plat en cours doit rendre l'accueil, pas une
                    // liste de resultats qu'on a deja quittee.
                    navController.popBackStack(HomeDestination, inclusive = false)
                    navController.navigateToEntryFor(foodId)
                }
            },
            onManualEntry = { name, addToDraft -> navController.navigateToManualEntry(name, addToDraft) },
            onClose = { navController.popBackStack() },
        )
    }
}
