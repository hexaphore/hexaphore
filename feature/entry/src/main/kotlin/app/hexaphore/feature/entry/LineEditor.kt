package app.hexaphore.feature.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
        OutlinedTextField(
            value = line.name,
            onValueChange = { actions.onLineEdit(line.id, LineEdit.Name(it)) },
            label = { Text(stringResource(R.string.entry_field_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberField(
                value = line.quantity,
                onValueChange = { actions.onLineEdit(line.id, LineEdit.Quantity(it)) },
                labelRes = R.string.entry_field_quantity,
                modifier = Modifier.weight(1f),
            )
            QuantityUnit.entries.forEach { unit ->
                FilterChip(
                    selected = line.unit == unit,
                    onClick = { actions.onLineEdit(line.id, LineEdit.Measurement(unit)) },
                    label = { Text(unit.code) },
                )
            }
        }

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
                MacroField(line = line, macro = macro, actions = actions)
            }
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
    NumberField(
        value = line.macros[macro].orEmpty(),
        onValueChange = { actions.onLineEdit(line.id, LineEdit.MacroValue(macro, it)) },
        labelRes = macro.fieldRes,
        modifier = Modifier.fillMaxWidth(),
        labelColor = NeonTheme.macros[macro].base,
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(labelRes), color = labelColor) },
        singleLine = true,
        // Decimal et non Number : le separateur decimal doit etre atteignable, et
        // la virgule est ce que produit un clavier en francais.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
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
