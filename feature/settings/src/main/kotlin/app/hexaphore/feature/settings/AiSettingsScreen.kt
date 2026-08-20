package app.hexaphore.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.DraftTextField
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.component.NeonButtonAvailability
import app.hexaphore.core.designsystem.component.NeonButtonStyle
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.ai.AiProvider

/** L'écran des fournisseurs, branché sur le graphe d'injection. */
@Composable
internal fun AiSettingsRoute(onClose: () -> Unit, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AiSettingsScreen(
        state = state,
        actions = remember(viewModel, onClose) {
            AiSettingsActions(
                onOpen = viewModel::onOpen,
                onKey = viewModel::onKey,
                onModel = viewModel::onModel,
                onBaseUrl = viewModel::onBaseUrl,
                onReveal = viewModel::onReveal,
                onTest = viewModel::onTest,
                onSave = viewModel::onSave,
                onForget = viewModel::onForget,
                onClose = onClose,
            )
        },
    )
}

/**
 * Une carte par fournisseur, une seule dépliée.
 *
 * **La phrase d'introduction n'est pas décorative.** [docs/05][ia] veut que
 * l'application dise clairement où partent les données et qu'elle n'affirme rien à la
 * place du fournisseur. Elle est ici plutôt que dans un dialogue de première
 * utilisation parce que c'est ici qu'on décide de brancher quelque chose.
 *
 * [ia]: docs/05-ia.md
 */
@Composable
internal fun AiSettingsScreen(state: AiSettingsUiState, actions: AiSettingsActions) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.screenMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onClose) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
                Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            Text(
                text = stringResource(R.string.ai_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.rows.forEach { row ->
                ProviderCard(
                    row = row,
                    open = row.provider == state.open,
                    form = state.form,
                    probe = state.probe,
                    actions = actions,
                )
            }

            // De l'air sous la derniere carte : le clavier remonte le contenu, et un
            // bouton colle au bord bas se manque.
            Spacer(modifier = Modifier.padding(bottom = Spacing.xl))
        }
    }
}

@Composable
private fun ProviderCard(
    row: ProviderRow,
    open: Boolean,
    form: ProviderForm,
    probe: ProbeState,
    actions: AiSettingsActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Une carte en reserve ne se deplie pas : il n'y a rien a y saisir
                // tant que le fournisseur n'a pas ete eprouve sur un vrai compte.
                .then(if (row.suspended) Modifier else Modifier.clickable { actions.onOpen(row.provider) })
                .padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ProviderHeader(row)

            if (row.suspended) {
                Text(
                    text = stringResource(R.string.ai_provider_suspended),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (open) {
                ProviderEditor(row.provider, form, probe, actions)
            }
        }
    }
}

@Composable
private fun ProviderHeader(row: ProviderRow) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = row.provider.displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(text = stringResource(row.statusRes), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Le formulaire d'un fournisseur.
 *
 * Le champ de clé est masqué par défaut et se révèle à la demande — [docs/05][ia] veut
 * qu'on puisse vérifier une clé sans la recoller, mais pas qu'elle traîne à l'écran.
 *
 * **Enregistrer est grisé tant que le formulaire est incomplet**, sans explication
 * attachée au bouton : ce qui manque est visible juste au-dessus, dans le champ vide.
 * C'est exactement le cas que `DISABLED` décrit, et non `UNAVAILABLE`, qui existe pour
 * les boutons dont l'indisponibilité demande une phrase.
 *
 * [ia]: docs/05-ia.md
 */
@Composable
private fun ProviderEditor(provider: AiProvider, form: ProviderForm, probe: ProbeState, actions: AiSettingsActions) {
    KeyField(form, actions)

    ModelField(provider, form, actions)

    BaseUrlField(form, actions)

    ProbeResult(probe)

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        NeonButton(
            text = stringResource(if (probe == ProbeState.Running) R.string.ai_testing else R.string.ai_test),
            onClick = actions.onTest,
            availability = form.actionAvailability(probe),
        )
        NeonButton(
            text = stringResource(R.string.ai_save),
            onClick = actions.onSave,
            style = NeonButtonStyle.FILLED,
            availability = form.actionAvailability(probe),
        )
    }

    NeonButton(
        text = stringResource(R.string.ai_forget),
        onClick = actions.onForget,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
private fun KeyField(form: ProviderForm, actions: AiSettingsActions) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DraftTextField(
            initial = form.apiKey,
            onValueChange = actions.onKey,
            label = stringResource(R.string.ai_key_label),
            modifier = Modifier.weight(1f),
            visualTransformation = if (form.revealed) VisualTransformation.None else PasswordVisualTransformation(),
        )
        Text(
            text = stringResource(if (form.revealed) R.string.ai_hide else R.string.ai_reveal),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable(onClick = actions.onReveal).padding(Spacing.sm),
        )
    }
}

@Composable
private fun ModelField(provider: AiProvider, form: ProviderForm, actions: AiSettingsActions) {
    DraftTextField(
        initial = form.model,
        onValueChange = actions.onModel,
        label = stringResource(R.string.ai_model_label),
        modifier = Modifier.fillMaxWidth(),
    )
    if (provider.suggestedModels.isNotEmpty()) {
        Text(
            text = provider.suggestedModels.joinToString(separator = " · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BaseUrlField(form: ProviderForm, actions: AiSettingsActions) {
    DraftTextField(
        initial = form.baseUrl,
        onValueChange = actions.onBaseUrl,
        label = stringResource(R.string.ai_base_url_label),
        modifier = Modifier.fillMaxWidth(),
        keyboardType = KeyboardType.Uri,
    )
}

@Composable
private fun ProbeResult(probe: ProbeState) {
    val message = when (probe) {
        is ProbeState.Succeeded ->
            stringResource(if (probe.vision) R.string.ai_probe_ok_vision else R.string.ai_probe_ok_text_only)

        is ProbeState.Failed -> stringResource(probe.messageRes)
        ProbeState.Idle, ProbeState.Running -> null
    }

    message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

    // Ce que le fournisseur a repondu, sous le message et en plus discret : c est
    // un renseignement pour diagnostiquer, pas la phrase qu on lit d abord.
    (probe as? ProbeState.Failed)?.detail?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Grisé pendant l'essai autant que sur un formulaire incomplet.
 *
 * Un second appui pendant qu'un appel est en vol partirait une seconde fois, et se
 * paierait deux fois.
 */
private fun ProviderForm.actionAvailability(probe: ProbeState) = when {
    probe == ProbeState.Running -> NeonButtonAvailability.DISABLED
    complete -> NeonButtonAvailability.AVAILABLE
    else -> NeonButtonAvailability.DISABLED
}

private val ProviderRow.statusRes: Int
    get() = when {
        suspended -> R.string.ai_provider_soon
        active -> R.string.ai_active
        configured -> R.string.ai_configured
        else -> R.string.ai_not_configured
    }
