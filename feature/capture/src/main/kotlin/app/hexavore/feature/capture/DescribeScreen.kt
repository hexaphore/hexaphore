package app.hexavore.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonAvailability
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.component.aiErrorMessage
import app.hexavore.core.designsystem.component.diagnostic
import app.hexavore.core.designsystem.theme.Spacing

/** La modale « Décrire », branchée sur le graphe d'injection. */
@Composable
internal fun DescribeRoute(
    onProposal: () -> Unit,
    onClose: () -> Unit,
    viewModel: DescribeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // La proposition est deposee : l ecran cede la place. `onNavigated` referme le
    // drapeau, sans quoi revenir corriger une phrase repartirait aussitot vers une
    // validation dont le depot est vide.
    LaunchedEffect(state.analysed) {
        if (state.analysed) {
            viewModel.onNavigated()
            onProposal()
        }
    }

    DescribeScreen(
        state = state,
        onDescription = viewModel::onDescription,
        onAnalyse = viewModel::onAnalyse,
        onClose = onClose,
    )
}

/**
 * Une zone de texte, un exemple, un bouton.
 *
 * C'est tout ce que [docs/02][parcours] demande, et c'est tout ce qu'il y a. **Aucun
 * réglage de fournisseur ici** : celui qui décrit son repas n'a pas à choisir un
 * modèle, et celui qui n'a pas de clé n'arrive jamais jusqu'ici — l'accueil grise le
 * bouton et explique.
 *
 * L'exemple est un **indice de saisie** et non un texte prérempli : prérempli, il
 * faudrait l'effacer avant d'écrire, et une analyse partirait sur l'exemple le jour où
 * quelqu'un tape « Analyser » trop vite.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun DescribeScreen(
    state: DescribeUiState,
    onDescription: (String) -> Unit,
    onAnalyse: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.describe_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.describe_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            DraftTextField(
                initial = state.description,
                onValueChange = onDescription,
                label = stringResource(R.string.describe_hint),
                modifier = Modifier.fillMaxWidth(),
                minLines = DESCRIPTION_LINES,
            )
            Text(
                text = stringResource(R.string.describe_example),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NeonButton(
                text = stringResource(R.string.describe_analyse),
                onClick = onAnalyse,
                modifier = Modifier.fillMaxWidth(),
                style = NeonButtonStyle.FILLED,
                availability = if (state.analysable) {
                    NeonButtonAvailability.AVAILABLE
                } else {
                    NeonButtonAvailability.DISABLED
                },
            )

            Waiting(state)
            Failure(state)
        }
    }
}

/**
 * L'attente, **en ligne et sans dialogue**.
 *
 * Un dialogue modal empêcherait de relire sa phrase pendant que l'analyse tourne, et
 * couperait le seul geste utile de cette attente : préparer la correction.
 */
@Composable
private fun Waiting(state: DescribeUiState) {
    if (!state.analysing) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(Spacing.xs))
        Text(text = stringResource(R.string.describe_analysing), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * L'échec, avec ce que le fournisseur a répondu quand il a répondu quelque chose.
 *
 * La description reste dans le champ : un échec réseau ne doit jamais faire retaper
 * une phrase, et c'est le `ViewModel` qui le garantit — l'écran ne la réécrit pas.
 */
@Composable
private fun Failure(state: DescribeUiState) {
    val error = state.error ?: return

    Text(text = aiErrorMessage(error), style = MaterialTheme.typography.bodyMedium)
    error.diagnostic?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Assez pour une phrase de repas, pas assez pour cacher le bouton sous le clavier. */
private const val DESCRIPTION_LINES = 3
