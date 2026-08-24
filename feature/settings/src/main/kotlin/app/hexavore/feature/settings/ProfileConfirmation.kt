package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.nutrition.Macro
import kotlin.math.roundToInt

/**
 * Ce qui change, montré **avant** d'écrire.
 *
 * Corriger sa taille de deux centimètres déplace un objectif quotidien, et c'est le
 * genre de conséquence qu'on ne doit pas découvrir sur l'accueil le lendemain. Les six
 * compteurs s'affichent donc face aux anciens, et rien n'est écrit tant que la boîte
 * n'a pas été acceptée ([D60][decisions]).
 *
 * **Un dialogue, contre l'usage de [docs/02][parcours]**, qui les réserve au destructif
 * et à l'irréversible. L'écart est assumé : il n'y a ici aucun autre endroit où poser
 * six lignes de chiffres, une barre n'en porterait pas trois, et un encart replié sous
 * les champs serait sous le pouce — le défaut exact que [D56][decisions] a corrigé.
 * L'action reste réversible, mais elle ouvre une version de l'objectif, et c'est cela
 * qu'on annonce.
 *
 * La boîte n'apparaît **que si les six chiffres bougent**. Un dialogue qui répète ce
 * qu'on vient de lire s'apprend à fermer sans le lire, et il ne protégerait plus rien
 * le jour où il aurait quelque chose à dire.
 *
 * [decisions]: docs/11-decisions.md
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun ConfirmationDialog(before: DailyGoal?, after: DailyGoal, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Body(stringResource(R.string.profile_confirm_body))
                Macro.entries.forEach { macro -> ChangeLine(macro, before?.get(macro), after[macro]) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.profile_confirm_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_confirm_cancel)) }
        },
    )
}

/**
 * « Protéines : 144 → 150 g ».
 *
 * L'ancien chiffre est là, et il compte autant que le neuf : un objectif quotidien n'a
 * de sens que rapporté à celui qu'on tenait. Quand il n'y en avait pas — première
 * correction après une base migrée sans objectif — la ligne ne montre que le nouveau
 * plutôt qu'une flèche partant de nulle part.
 */
@Composable
private fun ChangeLine(macro: Macro, before: Double?, after: Double) {
    val label = stringResource(macro.labelRes)
    val unit = stringResource(macro.unitRes)
    ReadLine(
        if (before == null) {
            stringResource(R.string.profile_confirm_line_new, label, after.roundToInt(), unit)
        } else {
            stringResource(R.string.profile_confirm_line, label, before.roundToInt(), after.roundToInt(), unit)
        },
    )
}
