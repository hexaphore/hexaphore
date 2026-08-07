package app.hexaphore.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.component.NeonButtonAvailability
import app.hexaphore.core.designsystem.component.NeonButtonStyle
import app.hexaphore.core.designsystem.component.SourceBadge
import app.hexaphore.core.designsystem.component.SwipeToDelete
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.diary.DraftImpact
import app.hexaphore.domain.nutrition.Macro
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/** L'écran de validation, branché sur le graphe d'injection. */
@Composable
internal fun EntryRoute(onClose: () -> Unit, viewModel: EntryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Enregistre : l'ecran se referme. Un effet plutot qu'un rappel depuis onSave,
    // pour que la fermeture suive l'etat reellement atteint et non l'intention.
    LaunchedEffect(state) {
        if (state is EntryUiState.Saved) onClose()
    }

    EntryScreen(
        state = state,
        actions = remember(viewModel, onClose) {
            EntryActions(
                onLineEdit = viewModel::onLineEdit,
                onAddLine = viewModel::onAddLine,
                onRemoveLine = viewModel::onRemoveLine,
                onSave = viewModel::onSave,
                onRetry = viewModel::onRetry,
                onClose = onClose,
            )
        },
    )
}

/**
 * L'écran de validation, sans état.
 *
 * **Il ne sait rien de la provenance de ce qu'il montre.** Aucune branche, aucun
 * paramètre, aucune chaîne ne distingue une saisie à la main d'une proposition de
 * modèle : la seule chose qui change d'un mode à l'autre est le contenu du
 * brouillon et la pastille en tête. C'est cette propriété qui évite de réécrire
 * l'écran à chaque nouveau mode de saisie, et c'est le piège central que
 * [docs/12][plan] signale depuis la conception.
 *
 * [plan]: docs/12-plan-de-developpement.md
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun EntryScreen(state: EntryUiState, actions: EntryActions, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        when (state) {
            EntryUiState.Loading, EntryUiState.Saved -> Unit
            EntryUiState.Unavailable -> UnavailableDish(actions.onClose)
            is EntryUiState.Error -> WriteFailed(actions.onRetry, actions.onClose)
            is EntryUiState.Content -> DraftEditor(state, actions)
        }
    }
}

@Composable
private fun DraftEditor(state: EntryUiState.Content, actions: EntryActions) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item(key = "en-tete") {
            DraftHeader(state, dateFormatter)
        }

        items(items = state.form.lines, key = { it.id.value }) { line ->
            SwipeToDelete(
                label = stringResource(R.string.entry_remove_line),
                onDelete = { actions.onRemoveLine(line.id) },
            ) {
                LineEditor(line = line, actions = actions)
            }
        }

        item(key = "pied") {
            DraftFooter(state, actions)
        }
    }
}

@Composable
private fun DraftHeader(state: EntryUiState.Content, dateFormatter: DateTimeFormatter) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(
                if (state.form.dishId == null) R.string.entry_title_new else R.string.entry_title_edit,
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceBadge(source = state.form.source)
            Text(
                text = dateFormatter.format(state.form.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DraftFooter(state: EntryUiState.Content, actions: EntryActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        NeonButton(
            text = stringResource(R.string.entry_add_line),
            onClick = actions.onAddLine,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        state.impact?.let { Totals(it) }

        NeonButton(
            text = stringResource(if (state.saving) R.string.entry_saving else R.string.entry_save),
            onClick = actions.onSave,
            modifier = Modifier.fillMaxWidth(),
            style = NeonButtonStyle.FILLED,
            availability = when {
                // Pendant l'ecriture, il n'y a rien a expliquer : le bouton est
                // inerte et TalkBack l'annonce desactive.
                state.saving -> NeonButtonAvailability.DISABLED
                // Incomplet : grise, mais il repond, et son appui dit ce qui manque.
                !state.saveable -> NeonButtonAvailability.UNAVAILABLE
                else -> NeonButtonAvailability.AVAILABLE
            },
        )

        if (!state.saveable && !state.saving) {
            Text(
                text = stringResource(R.string.entry_incomplete_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Ce que la saisie pèse, et ce qu'il restera.
 *
 * Le restant plutôt que le consommé, comme sur l'accueil : c'est l'information dont
 * on a besoin au moment de décider si ça rentre. Un dépassement s'affiche en
 * négatif, sans rouge d'alerte et sans message.
 */
@Composable
private fun Totals(impact: DraftImpact) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.entry_total_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.entry_kcal, impact.draftKcal.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                color = NeonTheme.macros[Macro.CALORIES].base,
            )
        }
        Text(
            text = stringResource(
                if (impact.remainingKcal < 0) R.string.entry_over else R.string.entry_remaining,
                abs(impact.remainingKcal).roundToInt(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnavailableDish(onClose: () -> Unit) {
    Message(
        title = stringResource(R.string.entry_unavailable_title),
        body = stringResource(R.string.entry_unavailable_body),
        action = stringResource(R.string.entry_close),
        onAction = onClose,
    )
}

@Composable
private fun WriteFailed(onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.entry_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.entry_error_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(
            text = stringResource(R.string.entry_error_retry),
            onClick = onRetry,
            style = NeonButtonStyle.FILLED,
        )
        NeonButton(text = stringResource(R.string.entry_close), onClick = onClose)
    }
}

@Composable
private fun Message(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(text = action, onClick = onAction)
    }
}
