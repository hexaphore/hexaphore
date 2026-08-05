package app.hexaphore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.MacroBar
import app.hexaphore.core.designsystem.component.MacroRing
import app.hexaphore.core.designsystem.component.MacroUnit
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.diary.DaySummary
import app.hexaphore.domain.nutrition.Macro
import kotlin.math.abs
import kotlin.math.roundToInt

/** L'accueil, branché sur le graphe d'injection. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state = state)
}

/**
 * L'accueil, sans état.
 *
 * Tout tient en un défilement vertical : le bloc de la journée, puis les repas.
 * Le bandeau calendrier et le bouton d'ajout arrivent avec les tranches qui leur
 * donnent une destination — un bouton qui n'ouvre rien n'est pas une avance.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
fun HomeScreen(state: HomeUiState, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            when (state) {
                HomeUiState.Loading -> Unit
                is HomeUiState.Content -> DayContent(state.summary)
            }
        }
    }
}

@Composable
private fun DayContent(summary: DaySummary) {
    RemainingBlock(summary)
    MacroBars(summary)
    if (summary.logged) {
        MealList(summary.meals)
    } else {
        EmptyDay()
    }
}

/**
 * L'anneau de calories et le grand chiffre.
 *
 * Le chiffre est le **restant**, pas le consommé : c'est l'information dont on a
 * besoin au moment de décider quoi manger. Un dépassement l'affiche en négatif,
 * sans rouge d'alerte ni message — c'est une donnée, pas un jugement.
 */
@Composable
private fun RemainingBlock(summary: DaySummary) {
    val consumed = summary.totals[Macro.CALORIES].value
    val goal = summary.goal.kcal
    val remaining = goal - consumed
    val ratio = if (goal > 0.0) (consumed / goal) else 0.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        MacroRing(
            macro = Macro.CALORIES,
            progress = ratio.toFloat(),
            contentDescription =
            stringResource(
                R.string.home_ring_a11y,
                consumed.roundToInt(),
                goal.roundToInt(),
                (ratio * PERCENT).roundToInt(),
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = abs(remaining).roundToInt().toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                    stringResource(
                        if (remaining < 0) R.string.home_over_label else R.string.home_remaining_label,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.home_consumed_of_goal, consumed.roundToInt(), goal.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MacroBars(summary: DaySummary) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BAR_MACROS.forEach { macro ->
            MacroBar(
                macro = macro,
                label = stringResource(macro.labelRes),
                consumed = summary.totals[macro].value.toFloat(),
                goal = summary.goal[macro].toFloat(),
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

/** Les cinq barres, dans l'ordre de docs/02. Les calories ont l'anneau. */
private val BAR_MACROS =
    listOf(Macro.PROTEIN, Macro.CARBS, Macro.SUGARS, Macro.FAT, Macro.FIBER)

private const val PERCENT = 100.0
