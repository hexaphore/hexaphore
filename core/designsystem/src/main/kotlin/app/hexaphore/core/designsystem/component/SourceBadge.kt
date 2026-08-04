package app.hexaphore.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexaphore.core.designsystem.R
import app.hexaphore.core.designsystem.preview.NeonPreviews
import app.hexaphore.core.designsystem.preview.PreviewSurface
import app.hexaphore.core.designsystem.theme.Radius
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.nutrition.NutritionSource

/**
 * L'étiquette de provenance d'une valeur nutritionnelle.
 *
 * Toutes les sources sont neutres. Une estimation se distingue par la **forme** —
 * contour en pointillés, glyphe en vague — et jamais par la teinte.
 *
 * C'est ce qui permet à quelqu'un de savoir, six mois plus tard, quelles lignes de
 * son journal sont des suppositions. Trois raisons de ne pas y consacrer une
 * couleur : les six teintes portent un sens et un seul ; la règle de daltonisme du
 * projet interdit de toute façon qu'une couleur porte seule une information, donc
 * un second canal aurait été nécessaire en plus ; et un contour discontinu dit
 * « valeur approximative » sans légende, dans les deux thèmes.
 *
 * Le pointillé plutôt qu'un triangle d'alerte : une estimation n'est pas un
 * problème, c'est une valeur moins précise. Le signal doit le dire sans dramatiser.
 *
 * @see docs/08-design-system.md
 */
@Composable
fun SourceBadge(source: NutritionSource, modifier: Modifier = Modifier) {
    val estimated = source == NutritionSource.AI_ESTIMATE
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(Radius.field)
    val label = stringResource(source.labelRes)
    val description = if (estimated) stringResource(R.string.ds_source_estimate_a11y, label) else label
    val density = LocalDensity.current

    Row(
        modifier =
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .drawBehind { drawBadgeOutline(outline, estimated, with(density) { Radius.field.toPx() }) }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            // Un badge est une seule information : « Estimation IA » plutôt que
            // le libellé puis, séparément, la description du glyphe.
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (estimated) {
            Canvas(Modifier.size(GlyphSize)) { drawWave(ink) }
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = ink)
    }
}

private val BorderWidth: Dp = 1.dp
private val GlyphSize: Dp = 16.dp

private const val DASH_ON = 6f
private const val DASH_OFF = 4f

// Points de contrôle de l'ondulation, en fraction de la boîte du glyphe. Les
// exprimer ainsi plutôt qu'en dp fait que la vague suit la taille du texte.
private const val WAVE_QUARTER = 0.25f
private const val WAVE_HALF = 0.5f
private const val WAVE_THREE_QUARTERS = 0.75f
private const val WAVE_AMPLITUDE_RATIO = 0.5f
private const val WAVE_STROKE_RATIO = 0.12f

private fun DrawScope.drawBadgeOutline(color: Color, estimated: Boolean, cornerPx: Float) {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerPx),
        style =
        Stroke(
            width = BorderWidth.toPx(),
            pathEffect = if (estimated) PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)) else null,
        ),
    )
}

/**
 * Le glyphe : deux ondulations, tracées et non dessinées en ressource.
 *
 * Un chemin de quatre points suit la taille du texte sans jeu d'assets à décliner
 * en cinq densités, et reste net à 200 % de police.
 */
private fun DrawScope.drawWave(color: Color) {
    val width = size.width
    val middle = size.height * WAVE_HALF
    val amplitude = size.height * WAVE_AMPLITUDE_RATIO

    val path =
        Path().apply {
            moveTo(0f, middle)
            cubicTo(
                width * WAVE_QUARTER,
                middle - amplitude,
                width * WAVE_QUARTER,
                middle + amplitude,
                width * WAVE_HALF,
                middle,
            )
            cubicTo(
                width * WAVE_THREE_QUARTERS,
                middle - amplitude,
                width * WAVE_THREE_QUARTERS,
                middle + amplitude,
                width,
                middle,
            )
        }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width * WAVE_STROKE_RATIO, cap = StrokeCap.Round),
    )
}

private val NutritionSource.labelRes: Int
    get() =
        when (this) {
            NutritionSource.CIQUAL -> R.string.ds_source_ciqual
            NutritionSource.OPEN_FOOD_FACTS -> R.string.ds_source_open_food_facts
            NutritionSource.CUSTOM -> R.string.ds_source_custom
            NutritionSource.AI_ESTIMATE -> R.string.ds_source_ai_estimate
            NutritionSource.MANUAL -> R.string.ds_source_manual
        }

// --- Aperçus -----------------------------------------------------------------

@NeonPreviews
@Composable
private fun SourceBadgePreview() {
    PreviewSurface {
        NutritionSource.entries.forEach { source ->
            SourceBadge(source = source)
        }
    }
}
