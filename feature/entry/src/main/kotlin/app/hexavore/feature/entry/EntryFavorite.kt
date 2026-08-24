package app.hexavore.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.nutrition.Macro

/**
 * L'étoile qui met le plat en favori.
 *
 * **Une seule icône pour les deux états**, teintée ou éteinte. `material-icons-core`
 * n'a pas de `StarBorder` — il n'y a qu'une trentaine d'icônes dans ce paquet — et
 * embarquer la bibliothèque étendue pour un contour aurait coûté plusieurs mégaoctets.
 * La couleur ne travaille donc pas seule : `stateDescription` dit l'état à TalkBack,
 * et c'est de toute façon ce que la charte exige partout ([08][design]).
 *
 * [design]: docs/08-design-system.md
 */
@Composable
internal fun FavoriteStar(favorite: Boolean, onToggle: () -> Unit) {
    val marked = stringResource(R.string.entry_favorite_on)
    val unmarked = stringResource(R.string.entry_favorite_off)

    IconButton(
        onClick = onToggle,
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = if (favorite) marked else unmarked
        },
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(R.string.entry_favorite),
            tint = if (favorite) {
                NeonTheme.macros[Macro.CALORIES].base
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * La boîte qui demande son nom au favori.
 *
 * **Le nom est proposé, pas imposé** : « Flocons, Lait, Banane » décrit le plat sans
 * qu'on ait rien à taper, et « Petit-déj » se retrouve en trois caractères dans une
 * liste. Un nom entièrement dérivé aurait rendu deux plats aux mêmes aliments
 * indiscernables ; un champ vide aurait fait renoncer à l'étoile une fois sur deux.
 *
 * **Un nom déjà pris laisse la boîte ouverte**, avec le nom refusé dedans. C'est ce qui
 * permet de le corriger plutôt que de tout retaper, et c'est la raison pour laquelle la
 * réponse revient par l'état et non par la valeur de retour de l'appel.
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
        title = { Text(stringResource(R.string.entry_favorite_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.entry_favorite_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DraftTextField(
                    initial = proposal,
                    onValueChange = { name = it },
                    label = stringResource(R.string.entry_favorite_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nameTaken) {
                    Text(
                        text = stringResource(R.string.entry_favorite_name_taken),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.entry_favorite_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.entry_favorite_cancel)) }
        },
    )
}
