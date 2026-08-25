package app.hexavore.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.ai.AiProvider

/** L'écran des fournisseurs, branché sur le graphe d'injection. */
@Composable
internal fun AiSettingsRoute(
    onClose: () -> Unit,
    onOpenExchanges: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AiSettingsScreen(
        state = state,
        onDebug = viewModel::onDebug,
        onOpenExchanges = onOpenExchanges,
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
internal fun AiSettingsScreen(
    state: AiSettingsUiState,
    actions: AiSettingsActions,
    onDebug: (Boolean) -> Unit = {},
    onOpenExchanges: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.ai_title),
                onClose = actions.onClose,
                closeLabel = stringResource(R.string.ai_close),
            )
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
                    inUse = state.inUse,
                    actions = actions,
                )
            }

            UsageCounter(state.usage)

            DebugCard(enabled = state.debug, onToggle = onDebug, onOpenExchanges = onOpenExchanges)

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
    inUse: Boolean,
    actions: AiSettingsActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Une carte en reserve ne se deplie pas : il n'y a rien a y saisir
                // tant que le fournisseur n'a pas ete eprouve sur un vrai compte.
                .then(if (row.suspended) Modifier else Modifier.clickable { actions.onOpen(row.provider) })
                .alpha(if (row.suspended) SUSPENDED_ALPHA else 1f)
                .padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ProviderHeader(row)

            if (open && !row.suspended) {
                ProviderEditor(row.provider, form, probe, inUse, actions)
            }
        }
    }
}

/**
 * Le nom, ce qu'on en met en avant, et où il en est.
 *
 * **« Bientôt disponible » remplace un paragraphe.** Une carte en réserve expliquait en
 * trois lignes pourquoi elle n'était pas proposée ; ce texte occupait plus de place que
 * les cartes qui, elles, servent à quelque chose. La raison n'a pas disparu — elle est
 * dans [AiProvider][app.hexavore.domain.ai.AiProvider], à l'endroit où quelqu'un qui se
 * demande *pourquoi* ira la lire. L'écran, lui, dit ce que l'utilisateur peut faire :
 * rien, pour l'instant.
 *
 * La carte est en outre **estompée**. C'est une propriété de disponibilité, et le
 * projet la signale par la forme autant que par les mots.
 */
@Composable
private fun ProviderHeader(row: ProviderRow) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = row.provider.displayName,
            style = MaterialTheme.typography.titleMedium,
        )
        if (row.provider.recommended) {
            RecommendedBadge(modifier = Modifier.padding(start = Spacing.sm))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(row.statusRes),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * L'étoile de celui qu'on met en avant.
 *
 * **Une forme et un mot, pas une couleur seule** : c'est la règle du projet pour toute
 * propriété qui doit se lire, et un liseré coloré ne dirait rien à qui ne distingue pas
 * cette couleur-là.
 */
@Composable
private fun RecommendedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Spacing.sm))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(BadgeIconSize),
        )
        Text(
            text = stringResource(R.string.ai_provider_recommended),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

private val BadgeIconSize: Dp = 14.dp

private val ProviderRow.statusRes: Int
    get() = when {
        suspended -> R.string.ai_provider_soon
        active -> R.string.ai_active
        configured -> R.string.ai_configured
        else -> R.string.ai_not_configured
    }

/**
 * Ce qu'une carte en réserve garde de son encre.
 *
 * Assez pour rester lisible — on doit pouvoir constater que le fournisseur existe —
 * et assez peu pour que l'œil ne s'y arrête pas en cherchant où coller sa clé.
 */
private const val SUSPENDED_ALPHA = 0.45f

/**
 * L'interrupteur de mise au point, et la porte qu'il ouvre.
 *
 * **En bas de l'écran, après le compteur.** Ce n'est pas un réglage qu'on vient poser :
 * c'est un instrument qu'on allume le jour où quelque chose ne va pas, et le mettre en
 * tête ferait croire qu'il faut s'en occuper.
 *
 * La porte n'apparaît que s'il est allumé — un écran d'échanges vides ne dit rien de ce
 * qu'il montrerait, et laisserait chercher pourquoi il ne montre rien.
 */
@Composable
private fun DebugCard(enabled: Boolean, onToggle: (Boolean) -> Unit, onOpenExchanges: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_debug_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.ai_debug_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                NeonButton(text = stringResource(R.string.ai_debug_open), onClick = onOpenExchanges)
            }
        }
    }
}
