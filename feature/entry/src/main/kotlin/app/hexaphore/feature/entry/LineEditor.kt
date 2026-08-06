package app.hexaphore.feature.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.nutrition.Macro

/**
 * Une ligne à saisir : nom, quantité, et les six valeurs.
 *
 * **L'énergie est toujours visible, les cinq autres se déplient.** Ce n'est pas un
 * gain de place : c'est la hiérarchie des obligations. Les calories sont exigées —
 * une ligne sans énergie n'entre pas dans le journal — alors que les cinq autres
 * peuvent rester inconnues, et un champ laissé vide veut dire *inconnu* et non zéro.
 * Les afficher toutes au même niveau laisserait croire qu'il faut les remplir.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun LineEditor(line: EntryFormLine, actions: EntryActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        NameRow(line, actions)
        QuantityRow(line, actions)

        MacroField(line = line, macro = Macro.CALORIES, actions = actions)

        TextButton(onClick = { actions.onLineEdit(line.id, LineEdit.ToggleDetails) }) {
            Text(
                text = stringResource(
                    if (line.expanded) R.string.entry_hide_macros else R.string.entry_show_macros,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (line.expanded) {
            DETAILED_MACROS.forEach { macro ->
                key(macro) { MacroField(line = line, macro = macro, actions = actions) }
            }
        }
    }
}

/**
 * Le nom de l'aliment, et la corbeille.
 *
 * **Le balayage ne suffit pas.** Un geste sans représentation visible est
 * introuvable pour qui ne le connaît pas, hors d'atteinte au lecteur d'écran, et
 * difficile pour une main qui tient mal le téléphone. Une action destructrice a
 * besoin d'un chemin qui se voie ; le balayage reste le raccourci de celui qui le
 * connaît.
 */
@Composable
private fun NameRow(line: EntryFormLine, actions: EntryActions) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DraftTextField(
            initial = line.name,
            onValueChange = { actions.onLineEdit(line.id, LineEdit.Name(it)) },
            label = stringResource(R.string.entry_field_name),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { actions.onRemoveLine(line.id) }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.entry_remove_line_a11y, line.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuantityRow(line: EntryFormLine, actions: EntryActions) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DraftTextField(
            initial = line.quantity,
            onValueChange = { actions.onLineEdit(line.id, LineEdit.Quantity(it)) },
            label = stringResource(R.string.entry_field_quantity),
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Decimal,
            accept = String::isNumberField,
        )
        QuantityUnit.entries.forEach { unit ->
            FilterChip(
                selected = line.unit == unit,
                onClick = { actions.onLineEdit(line.id, LineEdit.Measurement(unit)) },
                label = { Text(unit.code) },
            )
        }
    }
}

/**
 * Le champ d'une macro.
 *
 * Son libellé porte la teinte de la macro, la même que sur l'accueil : c'est ce qui
 * fait qu'une valeur saisie ici se retrouve sans traduction dans la barre du haut.
 * La teinte ne renseigne pas seule — le nom est écrit à côté.
 */
@Composable
private fun MacroField(line: EntryFormLine, macro: Macro, actions: EntryActions) {
    DraftTextField(
        initial = line.macros[macro].orEmpty(),
        onValueChange = { actions.onLineEdit(line.id, LineEdit.MacroValue(macro, it)) },
        label = stringResource(macro.fieldRes),
        modifier = Modifier.fillMaxWidth(),
        labelColor = NeonTheme.macros[macro].base,
        keyboardType = KeyboardType.Decimal,
        accept = String::isNumberField,
    )
}

/**
 * Un champ dont l'affichage ne dépend d'aucun aller-retour d'état.
 *
 * **Le texte affiché vit ici**, et non dans le `ViewModel`. La forme habituelle —
 * `value = state.texte`, `onValueChange = { viewModel.change(it) }` — suppose que
 * l'état revienne avant la frappe suivante. Il ne revient pas toujours : entre la
 * frappe et le nouvel état, il y a un `StateFlow`, un `combine` et une
 * recomposition, et une frappe rapide arrive avant la fin du trajet. Le champ se
 * réaffiche alors avec un texte d'il y a deux caractères, et la position du curseur
 * repart avec lui — on tape « Bolognaise », on lit « Boognaseil ».
 *
 * Ici, chaque frappe est appliquée immédiatement à l'état local ; le `ViewModel` est
 * prévenu ensuite et ne renvoie rien. Il n'y a plus qu'un seul écrivain, donc plus
 * de course.
 *
 * [initial] n'est lu qu'à la première composition. C'est voulu et suffisant : la
 * ligne est identifiée par son [app.hexaphore.domain.diary.DraftLineId] dans la
 * liste, donc rouvrir un plat ou replier les valeurs reconstruit le champ avec le
 * bon texte, et rien d'autre ne réécrit ce que l'utilisateur tape.
 *
 * @param accept ce que le champ laisse entrer. Une frappe refusée ne change rien —
 *   ni ici, ni dans le brouillon —, ce qui évite qu'une ligne devienne
 *   silencieusement inenregistrable à cause d'un caractère parasite.
 */
@Composable
private fun DraftTextField(
    initial: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    keyboardType: KeyboardType = KeyboardType.Text,
    accept: (String) -> Boolean = { true },
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }

    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (accept(candidate.text)) {
                value = candidate
                onValueChange(candidate.text)
            }
        },
        label = { Text(text = label, color = labelColor) },
        singleLine = true,
        // Decimal et non Number : le separateur decimal doit etre atteignable, et
        // la virgule est ce que produit un clavier en francais.
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

/**
 * Les cinq macros repliées, dans l'ordre angulaire de l'hexagone.
 *
 * Le même ordre que les barres de l'accueil et que les quartiers de la figure : la
 * position sert de second canal en cas de daltonisme, et elle ne renseigne que si
 * elle est la même partout.
 */
private val DETAILED_MACROS = listOf(Macro.PROTEIN, Macro.FIBER, Macro.CARBS, Macro.SUGARS, Macro.FAT)

private val Macro.fieldRes: Int
    @StringRes get() = when (this) {
        Macro.CALORIES -> R.string.entry_field_calories
        Macro.PROTEIN -> R.string.entry_field_protein
        Macro.CARBS -> R.string.entry_field_carbs
        Macro.SUGARS -> R.string.entry_field_sugars
        Macro.FAT -> R.string.entry_field_fat
        Macro.FIBER -> R.string.entry_field_fiber
    }
