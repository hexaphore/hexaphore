package app.hexavore.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.ai.AiProvider

/**
 * « Comment obtenir une clé ? », et la réponse.
 *
 * **Le trou le plus large du parcours.** L'écran demandait une clé d'API sans dire ce
 * que c'est, où on l'obtient, ni qui facture — trois questions qu'une personne qui n'en
 * a jamais pris se pose toutes en même temps, et devant lesquelles elle referme
 * l'application. Le champ vide ne les posait pas, il les laissait deviner.
 *
 * **Un lien, pas un mode d'emploi.** Les consoles changent de forme plusieurs fois par
 * an ; des captures d'écran ou une marche à suivre en six étapes seraient fausses avant
 * la fin de l'année, et fausses avec l'autorité d'une documentation. La page du
 * fournisseur, elle, est toujours à jour.
 *
 * @see docs/05-ia.md
 */
@Composable
internal fun KeyHelpLink(provider: AiProvider, onClick: () -> Unit) {
    if (provider.consoleUrl.isEmpty()) return

    Text(
        text = stringResource(R.string.ai_key_help_link),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = Spacing.xs),
    )
}

/**
 * Ce qu'est une clé d'API, où elle vit, et qui facture.
 *
 * Trois paragraphes courts et un bouton qui ouvre la console. **Ce que le dialogue dit
 * de plus important n'est pas la marche à suivre** mais la répartition : la clé reste
 * sur l'appareil, les appels partent en direct, et c'est le fournisseur qui facture —
 * Hexavore n'a pas de serveur et ne peut donc rien voir de ce qui passe.
 *
 * L'ouverture passe par [LocalUriHandler] et non par un `Intent` construit à la main :
 * c'est le navigateur choisi par l'utilisateur qui s'ouvre, et l'appareil sans
 * navigateur ne fait pas tomber l'application.
 */
@Composable
internal fun KeyHelpDialog(provider: AiProvider, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_key_help_title, provider.displayName)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Paragraph(stringResource(R.string.ai_key_help_what))
                Paragraph(stringResource(R.string.ai_key_help_where, provider.displayName))
                Paragraph(stringResource(R.string.ai_key_help_billing))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Le dialogue se ferme avant l'ouverture : revenir du navigateur
                    // sur une boite encore posee donnerait l'impression que rien n'a
                    // eu lieu.
                    onDismiss()
                    uriHandler.openUri(provider.consoleUrl)
                },
            ) {
                Text(stringResource(R.string.ai_key_help_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_key_help_close)) }
        },
    )
}

@Composable
private fun Paragraph(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}
