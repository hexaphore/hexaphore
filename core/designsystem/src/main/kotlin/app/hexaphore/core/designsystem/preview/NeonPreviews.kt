package app.hexaphore.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Les trois aperçus que doit exposer tout composant qui affiche du texte.
 *
 * Le troisième n'est pas un luxe : c'est celui qui révèle les hauteurs fixes et
 * les libellés tronqués, et c'est aussi celui qu'on oublie de regarder si chaque
 * composant doit le déclarer lui-même.
 *
 * @see docs/08-design-system.md
 */
@Preview(name = "Sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Clair", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Police 200 %", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
annotation class NeonPreviews
