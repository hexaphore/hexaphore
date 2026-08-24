package app.hexavore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.theme.Spacing

/**
 * La boîte qui demande son nom au favori.
 *
 * **Une seconde copie de celle de `:feature:entry`**, et c'est une duplication choisie.
 * Les deux écrans mettent un plat en favori, chacun depuis son propre geste — l'étoile
 * pendant la saisie, le menu long depuis la journée — et un `:feature` ne dépend jamais
 * d'un autre. La mettre en commun aurait demandé un module partagé pour une boîte de
 * quarante lignes, ou de faire descendre des chaînes métier dans `:core:designsystem`,
 * qui ne connaît ni les plats ni les favoris.
 *
 * Ce qu'elle porte est en revanche identique, et volontairement : un nom **proposé**
 * depuis les aliments du plat, et un nom déjà pris qui laisse la boîte ouverte avec le
 * texte refusé dedans, pour qu'on le corrige au lieu de tout retaper ([D62][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun FavoriteNameDialog(
    proposal: String,
    nameTaken: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(proposal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_favorite_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.home_favorite_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DraftTextField(
                    initial = proposal,
                    onValueChange = { name = it },
                    label = stringResource(R.string.home_favorite_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nameTaken) {
                    Text(
                        text = stringResource(R.string.home_favorite_name_taken),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.home_favorite_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_favorite_cancel)) }
        },
    )
}
