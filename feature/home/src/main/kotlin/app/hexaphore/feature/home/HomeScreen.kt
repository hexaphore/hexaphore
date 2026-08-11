package app.hexaphore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.MacroBar
import app.hexaphore.core.designsystem.component.MacroHexagon
import app.hexaphore.core.designsystem.component.MacroQuarter
import app.hexaphore.core.designsystem.component.MacroUnit
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.core.designsystem.theme.Timing
import app.hexaphore.domain.diary.DaySummary
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.nutrition.Macro
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** L'accueil, branché sur le graphe d'injection. */
@Composable
fun HomeRoute(
    onAddDish: () -> Unit,
    onEditDish: (DishId) -> Unit,
    onSetUpGoal: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        pendingUndo = pendingUndo,
        actions = remember(viewModel, onAddDish, onEditDish, onSetUpGoal, onOpenProfile) {
            HomeActions(
                onAddDish = onAddDish,
                onEditDish = onEditDish,
                onDeleteDish = viewModel::onDeleteDish,
                onDeleteEntry = viewModel::onDeleteEntry,
                onUndo = viewModel::onUndo,
                onUndoExpired = viewModel::onUndoExpired,
                onRetry = viewModel::retry,
                onSetUpGoal = onSetUpGoal,
                onOpenProfile = onOpenProfile,
            )
        },
    )
}

/**
 * L'accueil, sans état.
 *
 * Tout tient en un défilement vertical : le bloc de la journée, puis les plats.
 * Le bandeau calendrier arrive avec la tranche qui lui donne une destination — un
 * bandeau qui n'ouvre rien n'est pas une avance.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
fun HomeScreen(state: HomeUiState, pendingUndo: Dish?, actions: HomeActions, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.home_entry_deleted)
    val undo = stringResource(R.string.home_entry_undo)

    // La barre reste affichee cinq secondes, ni quatre ni dix : SnackbarDuration
    // n'offre que ces deux-la, donc la fenetre est tenue par le delai et la barre
    // par un affichage indefini. Voir Timing dans :core:designsystem.
    LaunchedEffect(pendingUndo) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = withTimeoutOrNull(Timing.UNDO_WINDOW_MILLIS) {
            snackbarHostState.showSnackbar(
                message = deleted,
                actionLabel = undo,
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (result == SnackbarResult.ActionPerformed) actions.onUndo() else actions.onUndoExpired()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Un seul bouton, et non l'arc de quatre actions de docs/02 : le scan
            // et l'IA n'existent pas encore, et un bouton qui n'ouvre rien n'est pas
            // une avance. Il ouvre la recherche, qui porte aussi la saisie manuelle
            // -- un aliment tape a la main devient une fiche, donc il se cherche
            // comme les autres et il n'y a plus deux portes a distinguer.
            ExtendedFloatingActionButton(onClick = actions.onAddDish) {
                Text(text = stringResource(R.string.home_add_dish))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Une icone seule, sans libelle : c'est la porte la moins frequentee
                // de l'ecran, et le titre du jour doit rester ce qu'on lit en premier.
                IconButton(onClick = actions.onOpenProfile) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = stringResource(R.string.home_open_profile),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (state) {
                HomeUiState.Loading -> Unit
                is HomeUiState.Content -> DayContent(state.summary, actions)
                HomeUiState.Error -> UnreadableDay(actions.onRetry)
            }
        }
    }
}

/**
 * La journée, avec ou **sans** objectif.
 *
 * Une journée sans objectif n'est pas une journée à zéro : c'est une journée qu'on ne
 * peut comparer à rien, parce qu'aucun objectif ne courait ce jour-là — avant
 * l'onboarding, ou pour une journée notée avant qu'un objectif soit posé
 * ([D04][decisions]). Les six totaux s'affichent alors sans jauge, et l'écran invite à
 * répondre aux questions plutôt que d'inventer une cible.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun DayContent(summary: DaySummary, actions: HomeActions) {
    val goal = summary.goal
    if (goal != null) {
        RemainingBlock(summary, goal)
        MacroBars(summary, goal)
    } else {
        NoGoal(actions.onSetUpGoal)
        MacroTotalsOnly(summary)
    }
    if (summary.logged) {
        DishList(dishes = summary.dishes, zone = summary.zone, actions = actions)
    } else {
        EmptyDay()
    }
}

/**
 * L'hexagone des macros, puis le grand chiffre.
 *
 * Le chiffre est sous la figure et non en son centre : les six quartiers prennent
 * naissance au centre, un texte y serait recouvert dès la première bouchée.
 *
 * C'est le **restant** qui s'affiche, pas le consommé — l'information dont on a
 * besoin au moment de décider quoi manger. Un dépassement l'affiche en négatif,
 * sans rouge d'alerte ni message : c'est une donnée, pas un jugement.
 */
@Composable
private fun RemainingBlock(summary: DaySummary, dailyGoal: DailyGoal) {
    val consumed = summary.totals[Macro.CALORIES].value
    val goal = dailyGoal.kcal
    val remaining = goal - consumed

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Un intervalle de plus sous la figure : les six lettres touchent le bas de
        // sa zone, et le « G » des glucides venait buter contre le grand chiffre.
        MacroHexagon(quarters = summary.quarters(dailyGoal), modifier = Modifier.padding(bottom = Spacing.md))
        Text(
            text = abs(remaining).roundToInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(if (remaining < 0) R.string.home_over_label else R.string.home_remaining_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.home_consumed_of_goal, consumed.roundToInt(), goal.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Les six quartiers, dérivés des totaux et de l'objectif du jour.
 *
 * Un objectif nul rendrait un ratio infini : le quartier est alors vide, ce qui est
 * la seule lecture honnête d'une cible qui n'existe pas.
 */
private fun DaySummary.quarters(goal: DailyGoal): Map<Macro, MacroQuarter> = Macro.entries.associateWith { macro ->
    val total = totals[macro]
    val target = goal[macro]
    MacroQuarter(
        ratio = if (target > 0.0) (total.value / target).toFloat() else 0f,
        complete = total.complete,
    )
}

@Composable
private fun MacroBars(summary: DaySummary, goal: DailyGoal) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BAR_MACROS.forEach { macro ->
            MacroBar(
                macro = macro,
                label = stringResource(macro.labelRes),
                consumed = summary.totals[macro].value.toFloat(),
                goal = goal[macro].toFloat(),
                unit = MacroUnit.GRAM,
            )
        }

        // D29 : un total ampute d'une valeur inconnue ne doit pas se lire comme
        // exact. Le dire une fois sous les barres, plutot que de bruiter chacune.
        if (BAR_MACROS.any { !summary.totals[it].complete }) {
            Text(
                text = stringResource(R.string.home_incomplete_totals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Ce qu'on voit tant qu'aucun objectif n'a été posé.
 *
 * Une invitation, pas une jauge à zéro : afficher six barres vides laisserait croire
 * qu'un objectif existe et qu'il n'est pas atteint. Le bouton mène aux cinq questions
 * qui permettent de le calculer.
 */
@Composable
private fun NoGoal(onSetUpGoal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.home_no_goal_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.home_no_goal_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(text = stringResource(R.string.home_no_goal_action), onClick = onSetUpGoal)
    }
}

/**
 * Les six totaux, sans référence à quoi que ce soit.
 *
 * Ce qui a été mangé reste une information exacte même sans objectif ; c'est la
 * **comparaison** qui manque, pas la mesure. Les afficher en texte plutôt qu'en jauge
 * dit exactement cela.
 */
@Composable
private fun MacroTotalsOnly(summary: DaySummary) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Macro.entries.forEach { macro ->
            val total = summary.totals[macro]
            Text(
                text = stringResource(
                    if (total.complete) R.string.home_total_line else R.string.home_total_line_partial,
                    stringResource(macro.labelRes),
                    total.value.roundToInt(),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * L'écran qui dit « je n'ai pas pu lire », plutôt que de montrer zéro.
 *
 * Sans lui, un échec de lecture s'afficherait comme une journée vide — et une
 * journée vide est une affirmation, pas une absence de réponse. C'est exactement
 * le genre de mensonge que le reste de l'application s'interdit sur les valeurs
 * inconnues ; il n'y a aucune raison de se l'autoriser sur la journée entière.
 *
 * Encart inline et non dialogue : rien n'est détruit, rien n'est irréversible, et
 * un dialogue bloquerait un écran qu'il suffit de relire.
 */
@Composable
private fun UnreadableDay(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.home_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.home_error_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(text = stringResource(R.string.home_error_retry), onClick = onRetry)
    }
}

@Composable
private fun EmptyDay() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Les cinq barres, dans l'**ordre angulaire des quartiers**.
 *
 * Les calories n'en ont pas : elles ont le quartier du haut et le grand chiffre.
 * L'ordre suit celui de l'hexagone pour que l'œil passe de l'un à l'autre sans
 * traduction — deux ordres différents rendraient la couleur seule porteuse du lien.
 */
private val BAR_MACROS =
    listOf(Macro.PROTEIN, Macro.FIBER, Macro.CARBS, Macro.SUGARS, Macro.FAT)
