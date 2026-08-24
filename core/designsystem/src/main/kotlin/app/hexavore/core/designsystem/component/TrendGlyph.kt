package app.hexavore.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.preview.NeonPreviews
import app.hexavore.core.designsystem.preview.PreviewSurface

/**
 * Une courbe qui descend, ponctuée de ses mesures.
 *
 * **`material-icons-core` n'a pas de graphique**, comme il n'avait pas de code-barres
 * ni de `StarBorder` ([D62][decisions]) : le jeu embarqué compte une trentaine
 * d'icônes. Même réponse qu'à chaque fois — dessiner le glyphe plutôt qu'embarquer la
 * bibliothèque étendue pour un seul trait, ou détourner une icône qui veut dire autre
 * chose.
 *
 * **Elle descend**, et ce n'est pas un jugement sur la direction que doit prendre un
 * poids : c'est la seule pente qu'un glyphe de vingt-quatre points lit sans ambiguïté
 * à côté d'un titre. Une ligne plate dirait « égaliseur », une ligne montante dirait
 * « statistiques ».
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun TrendGlyph(contentDescription: String, modifier: Modifier = Modifier, size: Dp = GlyphSize) {
    val ink = LocalContentColor.current

    Canvas(
        modifier = modifier
            .size(size)
            // Un glyphe est une seule information : le lecteur d'ecran annonce
            // l'action, pas la forme.
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        drawTrend(ink)
    }
}

private val GlyphSize: Dp = 24.dp

/**
 * Les sommets, en fractions de la boîte : le glyphe suit alors la taille demandée sans
 * jeu d'assets à décliner en cinq densités.
 *
 * Le troisième point remonte. Une descente parfaitement régulière se lit comme une
 * flèche ; c'est le rebond qui dit « mesures ».
 */
private val VERTICES = listOf(
    0.12f to 0.26f,
    0.38f to 0.52f,
    0.60f to 0.40f,
    0.88f to 0.76f,
)

private const val LINE_WIDTH_FRACTION = 0.085f
private const val DOT_RADIUS_FRACTION = 0.085f

private fun DrawScope.drawTrend(color: Color) {
    val points = VERTICES.map { (x, y) -> Offset(size.width * x, size.height * y) }

    points.zipWithNext { from, to ->
        drawLine(
            color = color,
            start = from,
            end = to,
            strokeWidth = size.minDimension * LINE_WIDTH_FRACTION,
            cap = StrokeCap.Round,
        )
    }
    // Les sommets, evides : pleins, ils epaississent la ligne au lieu de la ponctuer.
    points.forEach { point ->
        drawCircle(
            color = color,
            radius = size.minDimension * DOT_RADIUS_FRACTION,
            center = point,
            style = Stroke(width = size.minDimension * LINE_WIDTH_FRACTION),
        )
    }
}

// --- Aperçu -------------------------------------------------------------------

@NeonPreviews
@Composable
private fun TrendGlyphPreview() {
    PreviewSurface {
        TrendGlyph(contentDescription = "Journal de poids")
    }
}
