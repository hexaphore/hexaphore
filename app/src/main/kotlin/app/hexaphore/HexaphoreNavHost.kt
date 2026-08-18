package app.hexaphore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.hexaphore.feature.entry.EntryDestination
import app.hexaphore.feature.entry.entryScreen
import app.hexaphore.feature.entry.navigateToEntry
import app.hexaphore.feature.entry.navigateToEntryFor
import app.hexaphore.feature.entry.navigateToEntryForFavorite
import app.hexaphore.feature.entry.navigateToEntryForScan
import app.hexaphore.feature.home.HomeDestination
import app.hexaphore.feature.home.homeScreen
import app.hexaphore.feature.onboarding.OnboardingDestination
import app.hexaphore.feature.onboarding.onboardingScreen
import app.hexaphore.feature.scan.navigateToScan
import app.hexaphore.feature.scan.scanScreen
import app.hexaphore.feature.search.navigateToFavorites
import app.hexaphore.feature.search.navigateToManualEntry
import app.hexaphore.feature.search.navigateToManualEntryFor
import app.hexaphore.feature.search.navigateToSearch
import app.hexaphore.feature.search.navigateToSearchForDraft
import app.hexaphore.feature.search.searchScreens
import app.hexaphore.feature.settings.navigateToSettings
import app.hexaphore.feature.settings.settingsScreens

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
            onScan = { navController.navigateToScan() },
            onEditDish = { dishId -> navController.navigateToEntry(dishId) },
            onSetUpGoal = { navController.navigate(OnboardingDestination) },
            // Le hub existe desormais : sa deuxieme section est arrivee, ce qui est
            // exactement l echeance que D59 avait ecrite.
            onOpenSettings = { navController.navigateToSettings() },
            onOpenFavorites = { navController.navigateToFavorites() },
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
        settingsScreens(navController)
        captureScreens(navController)
    }
}

/**
 * Les trois modes de saisie, et l'écran où ils se rejoignent.
 *
 * Ensemble parce qu'ils partagent une règle et une seule : **ils s'effacent derrière
 * la validation**. Revenir en arrière depuis un plat en cours doit rendre l'accueil,
 * jamais l'aperçu caméra ou la liste de résultats qu'on vient de quitter. Écrits au
 * milieu des autres destinations, les trois `popBackStack` se lisaient comme trois
 * précautions séparées.
 *
 * C'est la traduction en graphe de ce que [docs/02][parcours] pose en tête : les
 * quatre modes de saisie convergent sur un seul écran. Le quatrième — l'IA — s'y
 * ajoutera sans que le reste du graphe bouge.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
private fun NavGraphBuilder.captureScreens(navController: NavHostController) {
    entryScreen(
        onAddFood = { navController.navigateToSearchForDraft() },
        onClose = { navController.popBackStack() },
    )
    scanScreen(
        // La modale s'efface derriere la validation, comme la recherche : revenir
        // en arriere depuis un plat en cours doit rendre l'accueil, pas un
        // apercu camera qu'on a deja quitte.
        onProduct = { foodId ->
            navController.popBackStack(HomeDestination, inclusive = false)
            navController.navigateToEntryForScan(foodId)
        },
        // Le code lu voyage avec : c'est ce qui fait que l'aliment cree sera
        // reconnu au prochain scan, et que le produit cesse d'etre un cas
        // particulier apres une seule saisie.
        onCreateFood = { barcode ->
            navController.popBackStack(HomeDestination, inclusive = false)
            navController.navigateToManualEntryFor(barcode)
        },
        onSearchByName = {
            navController.popBackStack(HomeDestination, inclusive = false)
            navController.navigateToSearch()
        },
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
        // Choisir un favori ouvre la validation, prerempli et modifiable : un
        // favori est un modele, pas un raccourci d'ecriture. La liste s'efface
        // derriere, comme la recherche.
        onPickFavorite = { favoriteId ->
            navController.popBackStack(HomeDestination, inclusive = false)
            navController.navigateToEntryForFavorite(favoriteId)
        },
        onClose = { navController.popBackStack() },
    )
}
