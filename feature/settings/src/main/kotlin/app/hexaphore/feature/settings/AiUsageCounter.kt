package app.hexaphore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.ai.AiPricing

/**
 * Ce que les analyses ont consommé, et ce que ça a probablement coûté.
 *
 * **Rien tant que rien n'a été consommé** : un compteur à zéro sur une installation
 * neuve n'apprend rien et occupe le bas de l'écran de quelqu'un qui cherche où coller
 * sa clé.
 *
 * La date du relevé est affichée **avec** le montant, comme [docs/05][ia] l'exige :
 * les tarifs changent, et une estimation périmée présentée comme exacte serait pire que
 * pas d'estimation. Un modèle sans tarif connu montre ses jetons sans montant — c'est
 * le cas de tout modèle saisi à la main, et de tous ceux du fournisseur « compatible ».
 *
 * [ia]: docs/05-ia.md
 */
@Composable
internal fun UsageCounter(rows: List<UsageRow>) {
    if (rows.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = stringResource(R.string.ai_usage_title), style = MaterialTheme.typography.titleMedium)

            rows.forEach { row ->
                Text(
                    text = stringResource(
                        R.string.ai_usage_line,
                        row.provider.displayName,
                        row.model,
                        row.calls,
                        row.tokens,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = row.cost
                        ?.let { stringResource(R.string.ai_usage_cost, it) }
                        ?: stringResource(R.string.ai_usage_cost_unknown),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.ai_usage_disclaimer, AiPricing.ASSESSED_ON.toString()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
