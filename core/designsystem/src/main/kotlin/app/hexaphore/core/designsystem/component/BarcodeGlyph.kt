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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexaphore.core.designsystem.preview.NeonPreviews
import app.hexaphore.core.designsystem.preview.PreviewSurface

/**
 * Un code-barres, tracé.
 *
 * **`material-icons-core` n'en a pas.** Le jeu embarqué compte une trentaine
 * d'icônes, et aucune ne dit « scanner » — c'est le même manque que `StarBorder`
 * en [D62][decisions], et la même réponse : dessiner plutôt que d'ajouter la
 * bibliothèque étendue pour un seul glyphe, ou d'en détourner un qui veut dire autre
 * chose.
 *
 * Des barres de largeurs inégales, parce que c'est ce qui rend un code-barres
 * reconnaissable : cinq traits réguliers se lisent comme un menu.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun BarcodeGlyph(contentDescription: String, modifier: Modifier = Modifier, size: Dp = GlyphSize) {
    val ink = LocalContentColor.current

    Canvas(
        modifier = modifier
            .size(size)
            // Un glyphe est une seule information : le lecteur d'ecran annonce
            // l'action, pas la forme.
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        drawBars(ink)
    }
}

private val GlyphSize: Dp = 24.dp

/**
 * Les barres, en fractions de la boîte : le glyphe suit alors la taille demandée sans
 * jeu d'assets à décliner en cinq densités.
 *
 * Chaque paire est une position de départ et une largeur.
 */
private val BARS = listOf(
    0.10f to 0.08f,
    0.24f to 0.05f,
    0.35f to 0.11f,
    0.52f to 0.05f,
    0.63f to 0.08f,
    0.77f to 0.13f,
)

private const val BAR_TOP = 0.15f
private const val BAR_HEIGHT = 0.70f

private fun DrawScope.drawBars(color: Color) {
    BARS.forEach { (start, width) ->
        drawRect(
            color = color,
            topLeft = Offset(size.width * start, size.height * BAR_TOP),
            size = Size(size.width * width, size.height * BAR_HEIGHT),
        )
    }
}

// --- Aperçu -------------------------------------------------------------------

@NeonPreviews
@Composable
private fun BarcodeGlyphPreview() {
    PreviewSurface {
        BarcodeGlyph(contentDescription = "Scanner")
    }
}
