package app.hexaphore.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Un appareil photo, tracé.
 *
 * **`material-icons-core` n'en a pas non plus**, et c'est la troisième fois : après
 * `StarBorder` ([D62][decisions]) et le code-barres, la réponse ne change pas —
 * dessiner un glyphe de vingt lignes plutôt qu'ajouter `material-icons-extended`, qui
 * embarque plusieurs milliers d'icônes pour en utiliser une.
 *
 * Un corps, un objectif, et la petite bosse du viseur : trois formes, parce qu'un
 * rectangle et un cercle seuls se lisent comme un bouton d'enregistrement.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun CameraGlyph(contentDescription: String, modifier: Modifier = Modifier, size: Dp = CameraGlyphSize) {
    val ink = LocalContentColor.current

    Canvas(
        modifier = modifier
            .size(size)
            // Un glyphe est une seule information : le lecteur d'ecran annonce
            // l'action, pas la forme.
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        drawCamera(ink)
    }
}

private val CameraGlyphSize: Dp = 24.dp

/** Tout en fractions de la boîte : le glyphe suit la taille demandée sans jeu d'assets. */
private fun DrawScope.drawCamera(ink: Color) {
    val stroke = Stroke(width = size.minDimension * STROKE_FRACTION)

    // La bosse du viseur, posee sur le bord haut du corps.
    drawRect(
        color = ink,
        topLeft = Offset(size.width * BUMP_LEFT, size.height * BUMP_TOP),
        size = Size(size.width * BUMP_WIDTH, size.height * BUMP_HEIGHT),
    )
    drawRect(
        color = ink,
        topLeft = Offset(size.width * BODY_LEFT, size.height * BODY_TOP),
        size = Size(size.width * BODY_WIDTH, size.height * BODY_HEIGHT),
        style = stroke,
    )
    drawCircle(
        color = ink,
        radius = size.minDimension * LENS_RADIUS,
        center = Offset(size.width / 2, size.height * LENS_CENTER_Y),
        style = stroke,
    )
}

private const val STROKE_FRACTION = 0.09f
private const val BODY_LEFT = 0.08f
private const val BODY_TOP = 0.28f
private const val BODY_WIDTH = 0.84f
private const val BODY_HEIGHT = 0.56f
private const val BUMP_LEFT = 0.34f
private const val BUMP_TOP = 0.16f
private const val BUMP_WIDTH = 0.22f
private const val BUMP_HEIGHT = 0.12f
private const val LENS_RADIUS = 0.16f
private const val LENS_CENTER_Y = 0.56f
