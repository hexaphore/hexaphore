package app.hexaphore.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.food.ContributionOutcome
import app.hexaphore.domain.food.FoodContribution
import app.hexaphore.domain.nutrition.Macro
import kotlin.math.roundToInt

/**
 * La question posée **au moment où la fiche vient d'être écrite**.
 *
 * C'est tout le choix de ce dialogue : ni un bouton dans une liste, ni une entrée de
 * menu, mais l'instant qui suit la création d'une fiche pour un produit que le scan
 * n'a pas trouvé. À ce moment-là, le travail vient d'être fait, on sait pourquoi on
 * l'a fait, et c'est celui où il a le plus de valeur pour quelqu'un d'autre
 * ([D91][decisions]).
 *
 * **Le récapitulatif montre les champs exacts**, et à chaque fois. Un accord donné une
 * seule fois pour toutes porterait sur des données qu'on n'avait pas encore saisies ;
 * ici on lit ce qui part avant que ça parte, parce que c'est public et définitif.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ContributionDialog(state: CustomFoodUiState.Offering, actions: CustomFoodActions) {
    when (val outcome = state.outcome) {
        null -> ContributionOffer(state, actions)
        else -> ContributionResult(outcome, actions)
    }
}

@Composable
private fun ContributionOffer(state: CustomFoodUiState.Offering, actions: CustomFoodActions) {
    AlertDialog(
        onDismissRequest = actions.onDeclineContribution,
        title = { Text(stringResource(R.string.contribute_title)) },
        text = { ContributionSummary(state.contribution) },
        confirmButton = {
            TextButton(onClick = actions.onContribute, enabled = !state.sending) {
                Text(stringResource(if (state.sending) R.string.contribute_sending else R.string.contribute_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDeclineContribution, enabled = !state.sending) {
                Text(stringResource(R.string.contribute_decline))
            }
        },
    )
}

/**
 * Ce qui part, champ par champ.
 *
 * **Les teneurs inconnues ne figurent pas**, parce qu'elles ne partent pas. Les
 * afficher vides laisserait croire qu'on envoie des blancs là où l'on n'envoie
 * simplement rien — et sur une base publique, envoyer un blanc effacerait ce que
 * quelqu'un d'autre y aurait mis.
 */
@Composable
private fun ContributionSummary(contribution: FoodContribution) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.contribute_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Line(stringResource(R.string.contribute_field_barcode), contribution.barcode.value)
        Line(stringResource(R.string.contribute_field_name), contribution.name)
        contribution.brand?.let { Line(stringResource(R.string.contribute_field_brand), it) }
        Macro.entries.forEach { macro ->
            contribution.per100g[macro]?.let { value ->
                // Le libelle porte deja son unite : « Calories (kcal) : 39 kcal » la dirait deux fois.
                Line(stringResource(macro.contributionLabel), value.roundToInt().toString())
            }
        }
        Text(
            text = stringResource(R.string.contribute_warning),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Text(
        text = "$label : $value",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Ce que le service a répondu.
 *
 * Quatre issues et quatre phrases : réessayer n'a de sens que pour deux d'entre elles,
 * et proposer de réessayer à quelqu'un dont le mot de passe est faux le ferait
 * recommencer sans fin. Le refus du service garde **sa propre phrase**, en anglais
 * s'il le veut : la traduire demanderait de connaître à l'avance ce qu'il peut
 * refuser.
 */
@Composable
private fun ContributionResult(outcome: ContributionOutcome, actions: CustomFoodActions) {
    val message = when (outcome) {
        ContributionOutcome.Sent -> stringResource(R.string.contribute_sent)
        ContributionOutcome.Rejected -> stringResource(R.string.contribute_rejected)
        ContributionOutcome.Unreachable -> stringResource(R.string.contribute_unreachable)
        is ContributionOutcome.Refused -> stringResource(R.string.contribute_refused, outcome.reason)
    }
    val retryable = outcome is ContributionOutcome.Unreachable

    AlertDialog(
        onDismissRequest = actions.onDeclineContribution,
        title = { Text(stringResource(R.string.contribute_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = if (retryable) actions.onContribute else actions.onDeclineContribution) {
                Text(stringResource(if (retryable) R.string.contribute_retry else R.string.contribute_close))
            }
        },
        dismissButton = {
            if (retryable) {
                TextButton(onClick = actions.onDeclineContribution) {
                    Text(stringResource(R.string.contribute_close))
                }
            }
        },
    )
}

private val Macro.contributionLabel: Int
    get() = when (this) {
        Macro.CALORIES -> R.string.custom_field_calories
        Macro.PROTEIN -> R.string.custom_field_protein
        Macro.CARBS -> R.string.custom_field_carbs
        Macro.SUGARS -> R.string.custom_field_sugars
        Macro.FAT -> R.string.custom_field_fat
        Macro.FIBER -> R.string.custom_field_fiber
    }
