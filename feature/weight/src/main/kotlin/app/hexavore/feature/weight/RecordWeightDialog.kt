package app.hexavore.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.NeonDateField
import app.hexavore.core.designsystem.theme.Spacing
import java.time.LocalDate

/**
 * Ajouter une pesée : un poids, une date, et c'est tout ([docs/02][parcours]).
 *
 * **La date par défaut est aujourd'hui**, et le poids proposé est le dernier connu :
 * une pesée varie de quelques centaines de grammes d'une fois sur l'autre, et
 * reproposer le dernier chiffre transforme la saisie en correction.
 *
 * **Aucune date à venir n'est proposable.** [NeonDateField] borne son sélecteur à
 * aujourd'hui plutôt que de refuser après coup : un choix impossible qu'on peut faire
 * quand même est un choix qu'il faut ensuite expliquer.
 *
 * Le champ est **non contrôlé** ([D45][decisions]) : il garde sa propre valeur, et
 * l'écran n'y écrit rien pendant la frappe. Un champ décimal contrôlé par un `Double`
 * efface la virgule que l'on vient de taper, parce que « 82, » ne se relit pas en
 * nombre.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun RecordWeightDialog(
    today: LocalDate,
    initialKg: Double?,
    onConfirm: (LocalDate, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf(today) }
    var typed by remember { mutableStateOf(initialKg?.let { formatKg(it) }.orEmpty()) }
    val weightKg = typed.toWeightKg()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                DraftTextField(
                    initial = typed,
                    onValueChange = { typed = it },
                    label = stringResource(R.string.weight_field),
                    keyboardType = KeyboardType.Decimal,
                )
                NeonDateField(
                    value = date,
                    onValueChange = { date = it },
                    label = stringResource(R.string.weight_date_field),
                    yearRange = (today.year - JOURNAL_YEARS)..today.year,
                    latest = today,
                )
            }
        },
        confirmButton = {
            // Desactive plutot que refuse : le domaine garde la meme regle en filet,
            // mais un bouton qui echoue en silence ne dit pas ce qui manque.
            TextButton(onClick = { weightKg?.let { onConfirm(date, it) } }, enabled = weightKg != null) {
                Text(stringResource(R.string.weight_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.weight_add_cancel)) }
        },
    )
}

/**
 * Le nombre tapé, ou `null` s'il n'en est pas un.
 *
 * **La virgule est acceptée**, parce que c'est la touche que porte un clavier décimal
 * français et que `toDoubleOrNull` ne la connaît pas. Refuser « 82,4 » là où le clavier
 * ne propose rien d'autre serait refuser la saisie elle-même.
 */
private fun String.toWeightKg(): Double? = replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0.0 }

/** Sans décimale inutile : « 82 » plutôt que « 82.0 ». */
private fun formatKg(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** Dix ans de journal : au-delà, la grille des années n'aide plus à retrouver un jour. */
private const val JOURNAL_YEARS = 10
