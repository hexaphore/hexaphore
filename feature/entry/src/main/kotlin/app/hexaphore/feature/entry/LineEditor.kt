package app.hexaphore.feature.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.hexaphore.core.designsystem.component.DraftTextField
import app.hexaphore.core.designsystem.component.isNumberField
import app.hexaphore.core.designsystem.component.isWholeNumberField
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing
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

/**
 * La quantité et son unité.
 *
 * Les unités proposées dépendent de la ligne : les grammes et les millilitres
 * toujours, plus les portions que la fiche d'origine déclare — « 1 pomme moyenne »,
 * « 1 tranche ». Une ligne tapée à la main n'en a aucune, faute de fiche pour dire
 * ce que pèse une tranche.
 *
 * Les pastilles passent à la ligne quand elles ne tiennent pas : un aliment peut en
 * proposer trois, et un libellé de portion est long.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuantityRow(line: EntryFormLine, actions: EntryActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DraftTextField(
            initial = line.quantity,
            onValueChange = { actions.onLineEdit(line.id, LineEdit.Quantity(it)) },
            label = stringResource(R.string.entry_field_quantity),
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Decimal,
            accept = String::isNumberField,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            line.units.forEach { unit ->
                FilterChip(
                    selected = line.unit == unit,
                    onClick = { actions.onLineEdit(line.id, LineEdit.Measurement(unit)) },
                    label = { Text(unit.code) },
                )
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
    // La cle porte la revision : un recalcul reconstruit le champ avec sa nouvelle
    // valeur, alors qu'une frappe -- qui ne l'incremente pas -- laisse le curseur ou
    // il est. Sans elle, le brouillon changerait sans que l'ecran bouge.
    key(line.revision) {
        MacroTextField(line, macro, actions)
    }
}

@Composable
private fun MacroTextField(line: EntryFormLine, macro: Macro, actions: EntryActions) {
    DraftTextField(
        initial = line.macros[macro].orEmpty(),
        onValueChange = { actions.onLineEdit(line.id, LineEdit.MacroValue(macro, it)) },
        label = stringResource(macro.fieldRes),
        modifier = Modifier.fillMaxWidth(),
        labelColor = NeonTheme.macros[macro].base,
        // Des entiers : personne ne compte les demi-grammes, et le separateur
        // decimal disparait du clavier comme du filtre.
        keyboardType = KeyboardType.Number,
        accept = String::isWholeNumberField,
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
