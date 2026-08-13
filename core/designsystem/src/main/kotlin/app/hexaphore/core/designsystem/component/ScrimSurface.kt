package app.hexaphore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexaphore.core.designsystem.preview.NeonPreviews
import app.hexaphore.core.designsystem.preview.PreviewSurface
import app.hexaphore.core.designsystem.theme.Radius
import app.hexaphore.core.designsystem.theme.ScrimBackground
import app.hexaphore.core.designsystem.theme.ScrimInk
import app.hexaphore.core.designsystem.theme.Spacing

/**
 * Un panneau posé sur une image qu'on ne maîtrise pas : un aperçu caméra, une photo.
 *
 * **C'est une `Surface`, et c'est tout l'objet du composant.** Un voile peint au
 * `Modifier.background()` teint le fond sans poser [LocalContentColor][local] ;
 * Material 3 retombe alors sur du noir, et l'écran de scan a été livré avec du texte
 * noir sur un voile sombre. Le fond d'un voile et l'encre qui va dessus ne sont pas
 * deux décisions : les séparer, c'est en oublier une. Les lier ici est le seul moyen
 * de ne pas les redécouper de travers au prochain appel.
 *
 * Le voile est sombre dans les deux thèmes, pour la raison écrite en
 * [ScrimBackground] : une image de caméra n'est pas un fond dont on hérite, et les
 * teintes néon du thème clair ne tiennent pas le contraste sur du blanc.
 *
 * [local]: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#LocalContentColor()
 * @see docs/08-design-system.md
 */
@Composable
fun ScrimSurface(modifier: Modifier = Modifier, shape: Shape = RectangleShape, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = ScrimBackground,
        contentColor = ScrimInk,
        content = content,
    )
}

// --- Aperçus -----------------------------------------------------------------

/** La hauteur du faux aperçu caméra, assez pour que le voile ne remplisse pas la vignette. */
private val PreviewFrameHeight: Dp = 160.dp

@NeonPreviews
@Composable
private fun ScrimSurfacePreview() {
    PreviewSurface {
        // Un aplat blanc tient lieu d'apercu camera : c'est le pire fond que le voile
        // ait a couvrir, et le seul qui rende cet apercu utile. Sur le fond de
        // l'application, un voile sombre sur du sombre ne prouverait rien.
        Box(
            Modifier
                .fillMaxWidth()
                .height(PreviewFrameHeight)
                .background(Color.White),
        ) {
            ScrimSurface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Produit inconnu", style = MaterialTheme.typography.titleMedium)
                    NeonButton(text = "Creer cet aliment", onClick = {})
                    TextButton(onClick = {}) { Text("Scanner a nouveau") }
                }
            }
        }
    }
}
