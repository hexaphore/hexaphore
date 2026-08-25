package app.hexavore.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.ai.AiExchange
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ce qui est parti chez le fournisseur, et ce qui en est revenu.
 *
 * **C'est de la mise au point, et l'écran le porte sur lui.** Pas de mise en forme du
 * JSON, pas de coloration, pas de pliage : du texte à chasse fixe qu'on lit ou qu'on
 * copie. Embellir un instrument de diagnostic revient à en cacher une partie, et la
 * partie cachée est toujours celle qu'on cherchait.
 *
 * **Rien n'est sur le disque** : la liste vit en mémoire et disparaît avec le
 * processus. Fermer l'application est la façon la plus sûre de l'effacer, et le bouton
 * est là pour ceux qui ne veulent pas attendre.
 */
@Composable
internal fun AiExchangesRoute(onClose: () -> Unit, viewModel: AiExchangesViewModel = hiltViewModel()) {
    val exchanges by viewModel.exchanges.collectAsStateWithLifecycle()
    // **Mis en page par defaut.** Le brut reste a un geste, pour le jour ou l'on doute
    // de la mise en page elle-meme -- mais ce n'est pas ce qu'on vient lire.
    var indented by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.exchanges_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.exchanges_close),
            ) {
                TextButton(onClick = viewModel::onClear) { Text(stringResource(R.string.exchanges_clear)) }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.exchanges_indent),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = indented, onCheckedChange = { indented = it })
                }
            }

            if (exchanges.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.exchanges_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(exchanges) { exchange -> ExchangeCard(exchange, indented) }
        }
    }
}

@Composable
private fun ExchangeCard(exchange: AiExchange, indented: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(text = exchange.at.hourMinuteSecond(), style = MaterialTheme.typography.labelMedium)
                Text(
                    text = exchange.status.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    // Le code HTTP porte la couleur parce qu'il est ce qu'on cherche
                    // en premier ; c'est un ecran de diagnostic, pas une liste a lire.
                    color = if (exchange.status in SUCCESS) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = exchange.endpoint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Dump(stringResource(R.string.exchanges_sent), exchange.request, indented)
            Dump(stringResource(R.string.exchanges_received), exchange.response, indented)
        }
    }
}

/**
 * Un corps, mis en page ou tel quel.
 *
 * **Les deux modes ne défilent pas pareil, et c'est le fond du problème.** Un JSON
 * d'une seule ligne fait quelques milliers de caractères : le replier en paragraphe le
 * rend illisible, donc le brut glisse horizontalement, comme dans un terminal. Une fois
 * indenté, chaque ligne est courte et le retour à la ligne redevient le bon
 * comportement — c'est ce qui permet de lire de haut en bas plutôt que de balayer.
 */
@Composable
private fun Dump(title: String, body: String, indented: Boolean) {
    Text(text = title, style = MaterialTheme.typography.labelSmall)
    Text(
        text = if (indented) body.indentedJson() else body,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = if (indented) Modifier else Modifier.horizontalScroll(rememberScrollState()),
    )
}

private fun java.time.Instant.hourMinuteSecond(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"))

private val SUCCESS = 200..299
