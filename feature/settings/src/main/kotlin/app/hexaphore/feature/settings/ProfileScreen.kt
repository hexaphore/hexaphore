package app.hexaphore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.component.NeonButtonAvailability
import app.hexaphore.core.designsystem.component.NeonButtonStyle
import app.hexaphore.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

/** Les réglages profil, branchés sur le graphe d'injection. */
@Composable
internal fun ProfileRoute(onClose: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileScreen(
        state = state,
        actions = remember(viewModel, onClose) {
            ProfileActions(
                onForm = viewModel::onForm,
                onLock = viewModel::onLock,
                onRelease = viewModel::onRelease,
                onCounterChange = viewModel::onCounterChange,
                // L'ecran se referme sur l'accueil une fois l'ecriture aboutie, et
                // seulement alors : un retour immediat laisserait croire qu'un echec
                // d'ecriture a ete pris en compte.
                onSave = { viewModel.onSave(onClose) },
                onClose = onClose,
            )
        },
    )
}

/**
 * Un seul écran, un seul bouton, un seul calcul.
 *
 * Les six compteurs suivent chaque correction en direct : le bouton « Recalculer mes
 * objectifs » que [docs/02][parcours] prévoyait n'aurait rien eu à recalculer, et un
 * second chemin de calcul finit toujours par annoncer autre chose que le premier
 * ([D59][decisions]).
 *
 * Le refus se voit : appuyer sur « Enregistrer » alors qu'il manque quelque chose
 * affiche une barre qui dit **quoi**. C'est le principe de [D28][decisions], par le
 * canal que [D56][decisions] a retenu — ce qui interrompt le regard est ce qui se lit.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ProfileScreen(state: ProfileUiState, actions: ProfileActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val blocked = state.blocker?.let { stringResource(it.messageRes) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Sections(state, actions)
            }

            if (state.loaded && !state.unreadable) {
                SaveArea(state, actions) {
                    scope.launch {
                        // Une seule barre a la fois : trois appuis empilaient trois
                        // fois le meme message, le troisieme arrivant dix secondes
                        // plus tard.
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(blocked.orEmpty())
                    }
                }
            }
        }
    }
}

@Composable
private fun Sections(state: ProfileUiState, actions: ProfileActions) {
    when {
        state.unreadable -> Unreadable()
        // Rien tant qu'on ne sait pas : un formulaire vide affiche une seconde
        // laisserait croire qu'aucun profil n'existe.
        !state.loaded -> Unit
        else -> {
            Body(stringResource(R.string.profile_history_note))
            YouSection(state.form, state.today, actions.onForm)
            ActivitySection(state.form, actions.onForm)
            ObjectiveSection(state.form, state.today, state.plan, actions.onForm)
            CountersSection(state, actions)
            if (state.failed) Body(stringResource(R.string.profile_save_failed))
        }
    }
}

/**
 * L'écran qui dit « je n'ai pas pu lire », plutôt que d'afficher un profil vide.
 *
 * Un formulaire vide serait ici plus qu'un mensonge d'affichage : enregistré, il
 * écraserait le profil qui est en base par des champs que personne n'a saisis.
 */
@Composable
private fun Unreadable() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.profile_unreadable_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Body(stringResource(R.string.profile_unreadable_body))
    }
}

@Composable
private fun SaveArea(state: ProfileUiState, actions: ProfileActions, onBlocked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        NeonButton(
            text = stringResource(if (state.saving) R.string.profile_saving else R.string.profile_save),
            // Le bouton indisponible reagit et **repond** : c'est lui qui declenche
            // l'explication, pas un texte permanent (D28, D56).
            onClick = { if (state.canSave) actions.onSave() else onBlocked() },
            modifier = Modifier.fillMaxWidth(),
            style = NeonButtonStyle.FILLED,
            availability = when {
                state.saving -> NeonButtonAvailability.DISABLED
                state.canSave -> NeonButtonAvailability.AVAILABLE
                else -> NeonButtonAvailability.UNAVAILABLE
            },
        )
        NeonButton(
            text = stringResource(R.string.profile_close),
            onClick = actions.onClose,
            modifier = Modifier.fillMaxWidth(),
            style = NeonButtonStyle.OUTLINED,
        )
    }
}

private val ProfileBlocker.messageRes: Int
    get() = when (this) {
        ProfileBlocker.IDENTITY -> R.string.profile_blocked_identity
        ProfileBlocker.ACTIVITY -> R.string.profile_blocked_activity
        ProfileBlocker.OBJECTIVE -> R.string.profile_blocked_objective
        ProfileBlocker.EMPTY_COUNTER -> R.string.profile_blocked_counter
    }
