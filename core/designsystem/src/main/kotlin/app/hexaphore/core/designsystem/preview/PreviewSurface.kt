package app.hexaphore.core.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing

/**
 * Cadre commun des aperçus : le thème, le fond de l'application, les marges.
 *
 * Un aperçu qui oublie [NeonTheme] plante à l'ouverture plutôt que d'afficher un
 * composant faux : les locaux de composition n'ont pas de valeur par défaut, et
 * c'est voulu.
 */
@Composable
internal fun PreviewSurface(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable ColumnScope.() -> Unit) {
    NeonTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}
