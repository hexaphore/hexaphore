package app.hexaphore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.hexaphore.feature.settings.navigateToProfile
import app.hexaphore.feature.settings.profileScreen

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
fun HexaphoreNavHost(modifier: Modifier = Modifier, viewModel: StartDestinationViewModel = hiltViewModel()) {
    val start by viewModel.destination.collectAsStateWithLifecycle()

    // Rien tant qu'on ne sait pas. Poser l'accueil puis sauter vers l'onboarding
    // ferait clignoter un journal vide a chaque lancement.
    val destination = start ?: return

    HexaphoreNavHost(
        startDestination = if (destination == StartDestination.ONBOARDING) OnboardingDestination else HomeDestination,
        modifier = modifier,
    )
}

/**
 * Le graphe proprement dit, avec sa destination de départ posée.
 *
 * Séparé pour que le `NavHost` soit construit **une seule fois**, avec un départ déjà
 * connu : changer `startDestination` après coup ne fait rien — Compose Navigation ne le
 * relit pas — et la seule façon d'en tenir compte serait de recréer le contrôleur, donc
 * de perdre la pile.
 */
@Composable
private fun HexaphoreNavHost(startDestination: Any, modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        homeScreen(
            // Un seul bouton, et il ouvre la recherche : la saisie manuelle y est
            // une branche, puisqu'un aliment tape a la main devient une fiche.
            onAddDish = { navController.navigateToSearch() },
            onEditDish = { dishId -> navController.navigateToEntry(dishId) },
            onSetUpGoal = { navController.navigate(OnboardingDestination) },
            // Directement sur « Profil et objectifs » : les quatre autres sections
            // de docs/02 n'ont pas de contenu avant les tranches 6 et 8, et un
            // ecran de reglages qui ne designerait qu'une destination serait un
            // ecran de transit (D59).
            onOpenProfile = { navController.navigateToProfile() },
        )
        onboardingScreen(
            // L'onboarding s'efface derriere l'accueil : y revenir par le bouton
            // « retour » du systeme reposerait cinq questions auxquelles on vient de
            // repondre. `popUpTo(0)` vide la pile entiere, y compris quand
            // l'onboarding **etait** la destination de depart -- cas de la premiere
            // ouverture, ou il n'y a rien derriere lui vers quoi revenir.
            onDone = {
                navController.navigate(HomeDestination) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
        entryScreen(
            onAddFood = { navController.navigateToSearchForDraft() },
            onClose = { navController.popBackStack() },
        )
        profileScreen(onClose = { navController.popBackStack() })
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
