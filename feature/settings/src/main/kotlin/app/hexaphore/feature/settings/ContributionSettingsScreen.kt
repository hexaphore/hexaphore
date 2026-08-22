package app.hexaphore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.DraftTextField
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.component.NeonButtonAvailability
import app.hexaphore.core.designsystem.theme.Spacing

@Composable
internal fun ContributionSettingsRoute(onClose: () -> Unit) {
    val viewModel: ContributionSettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ContributionSettingsScreen(
        state = state,
        onSave = viewModel::onSave,
        onForget = viewModel::onForget,
        onSandboxChange = viewModel::onSandboxChange,
        onClose = onClose,
    )
}

/**
 * Le compte sous lequel on contribue, et où l'on envoie.
 *
 * **Le compte de l'utilisateur, jamais un compte de l'application** : celui-ci aurait
 * son mot de passe dans un APK publié sous GPL ([D90][decisions]). Sans compte
 * renseigné ici, la proposition de contribuer n'apparaît simplement pas — comme les
 * modes d'IA sans clé.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ContributionSettingsScreen(
    state: ContributionUiState,
    onSave: (String, String) -> Unit,
    onForget: () -> Unit,
    onSandboxChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Spacing.screenMargin)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Header(onClose)
            AccountCard(state, onSave, onForget)
            SandboxCard(state.sandbox, onSandboxChange)
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.contribution_close))
        }
        Text(
            text = stringResource(R.string.contribution_title),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * Les deux champs, et ce que « enregistrer » veut dire ici.
 *
 * **Le mot de passe part vide à chaque composition** et n'est jamais relu : on ne
 * corrige pas un mot de passe, on le remplace. La clé de composition le fait renaître
 * quand le compte change, sans quoi le champ garderait le texte d'avant après un
 * oubli ([D45][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun AccountCard(state: ContributionUiState, onSave: (String, String) -> Unit, onForget: () -> Unit) {
    var userId by remember(state.userId) { mutableStateOf(state.userId) }
    var password by remember(state.connected) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.contribution_account_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.contribution_account_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            key(state.userId) {
                DraftTextField(
                    initial = userId,
                    onValueChange = { userId = it },
                    label = stringResource(R.string.contribution_user_id),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            key(state.connected) {
                DraftTextField(
                    initial = "",
                    onValueChange = { password = it },
                    label = stringResource(R.string.contribution_password),
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            NeonButton(
                text = stringResource(R.string.contribution_save),
                onClick = { onSave(userId, password) },
                modifier = Modifier.fillMaxWidth(),
                // Inerte plutot qu'explicatif : ce qui manque est visible juste
                // au-dessus, dans les deux champs vides (D28).
                availability = when {
                    userId.isBlank() || password.isBlank() -> NeonButtonAvailability.DISABLED
                    else -> NeonButtonAvailability.AVAILABLE
                },
            )
            if (state.connected) {
                NeonButton(
                    text = stringResource(R.string.contribution_forget),
                    onClick = onForget,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * L'instance visée.
 *
 * **Éteint par défaut**, et c'est le bon défaut malgré la prudence qu'on pourrait
 * vouloir : une contribution qui partirait par défaut vers un bac à sable serait une
 * contribution qui n'existe pas, offerte à quelqu'un qui croit contribuer.
 */
@Composable
private fun SandboxCard(sandbox: Boolean, onSandboxChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.contribution_sandbox_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.contribution_sandbox_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = sandbox, onCheckedChange = onSandboxChange)
        }
    }
}
