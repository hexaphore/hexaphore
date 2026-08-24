package app.hexavore.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.preview.NeonPreviews
import app.hexavore.core.designsystem.preview.PreviewSurface
import app.hexavore.core.designsystem.theme.MacroPalette
import app.hexavore.core.designsystem.theme.Motion
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.lighten
import app.hexavore.core.designsystem.theme.saturate
import app.hexavore.domain.nutrition.Macro

/**
 * L'anneau de progression d'une macro.
 *
 * La lueur croît avec l'avancement : l'anneau s'allume à mesure qu'on approche de
 * l'objectif. C'est la seule récompense visuelle de l'application, et elle suffit.
 * Un dépassement ne bascule pas en rouge et n'affiche aucun message : c'est une
 * donnée, pas un jugement.
 *
 * @param progress avancement, où 1 vaut l'objectif atteint. Au-delà de 1, un second
 *   arc plus fin se superpose.
 * @param contentDescription phrase lue par TalkBack. Quand elle est fournie,
 *   l'anneau devient un nœud d'accessibilité unique : une phrase utile plutôt que
 *   « barre de progression, 61 % » suivie du contenu du centre.
 *
 * @see docs/08-design-system.md
 */
@Composable
fun MacroRing(
    macro: Macro,
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = MacroRingDefaults.HomeDiameter,
    strokeWidth: Dp = MacroRingDefaults.StrokeWidth,
    contentDescription: String? = null,
    center: @Composable () -> Unit = {},
) {
    val palette = NeonTheme.macros[macro]
    val trackColor = MaterialTheme.colorScheme.outline
    val durationMillis = NeonTheme.motion.gaugeValueMillis

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceAtLeast(0f),
        animationSpec = tween(durationMillis = durationMillis, easing = Motion.GaugeEasing),
        label = "progression de l anneau",
    )

    Box(
        modifier =
        modifier
            .size(diameter)
            .describedAs(contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawTrack(trackColor, strokeWidth.toPx())
            drawProgress(palette, animatedProgress, strokeWidth.toPx())
            drawOvershoot(palette, animatedProgress, MacroRingDefaults.OvershootStrokeWidth.toPx(), strokeWidth.toPx())
        }
        center()
    }
}

/** Diamètres et épaisseurs de l'anneau, aux trois échelles où il apparaît. */
object MacroRingDefaults {
    /** Anneau de calories de l'accueil. Diamètre de référence. */
    val HomeDiameter: Dp = 180.dp

    /** Pastille du bandeau calendrier. */
    val CalendarDiameter: Dp = 44.dp

    /** Case de la vue mensuelle. */
    val MonthDiameter: Dp = 28.dp

    val StrokeWidth: Dp = 8.dp

    val OvershootStrokeWidth: Dp = 4.dp
}

private const val START_ANGLE = -90f
private const val FULL_TURN = 360f
private const val GLOW_LAYERS = 3
private const val GLOW_SPREAD_RATIO = 0.7f
private const val PROGRESS_LIGHTEN = 0.20f
private const val OVERSHOOT_SATURATION = 0.30f

private fun DrawScope.arcTopLeft(strokeWidth: Float) = Offset(strokeWidth / 2f, strokeWidth / 2f)

private fun DrawScope.arcSize(strokeWidth: Float) = Size(size.width - strokeWidth, size.height - strokeWidth)

private fun DrawScope.drawTrack(color: Color, strokeWidth: Float) {
    drawArc(
        color = color,
        startAngle = START_ANGLE,
        sweepAngle = FULL_TURN,
        useCenter = false,
        topLeft = arcTopLeft(strokeWidth),
        size = arcSize(strokeWidth),
        style = Stroke(width = strokeWidth),
    )
}

private fun DrawScope.drawProgress(palette: MacroPalette, progress: Float, strokeWidth: Float) {
    val filled = progress.coerceIn(0f, 1f)
    if (filled == 0f) return
    val sweep = FULL_TURN * filled

    drawGlow(palette, filled, sweep, strokeWidth)

    drawArc(
        brush = Brush.linearGradient(listOf(palette.base, palette.base.lighten(PROGRESS_LIGHTEN))),
        startAngle = START_ANGLE,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = arcTopLeft(strokeWidth),
        size = arcSize(strokeWidth),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

/**
 * Lueur portée, obtenue par empilement d'arcs élargis.
 *
 * Un vrai flou passerait par `Modifier.blur`, indisponible avant Android 12, ou par
 * un `BlurMaskFilter` ignoré par l'accélération matérielle avant Android 9. Le
 * dégradé en escalier rend le même service sur tout le parc visé.
 */
private fun DrawScope.drawGlow(palette: MacroPalette, filled: Float, sweep: Float, strokeWidth: Float) {
    if (palette.glow.alpha == 0f) return
    repeat(GLOW_LAYERS) { layer ->
        val spread = strokeWidth * (layer + 1) * GLOW_SPREAD_RATIO
        drawArc(
            color = palette.glow.copy(alpha = palette.glow.alpha * filled / (GLOW_LAYERS * (layer + 1))),
            startAngle = START_ANGLE,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft(strokeWidth),
            size = arcSize(strokeWidth),
            style = Stroke(width = strokeWidth + spread, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawOvershoot(
    palette: MacroPalette,
    progress: Float,
    overshootWidth: Float,
    strokeWidth: Float,
) {
    val overshoot = (progress - 1f).coerceIn(0f, 1f)
    if (overshoot == 0f) return
    drawArc(
        color = palette.base.saturate(OVERSHOOT_SATURATION),
        startAngle = START_ANGLE,
        sweepAngle = FULL_TURN * overshoot,
        useCenter = false,
        topLeft = arcTopLeft(strokeWidth),
        size = arcSize(strokeWidth),
        style = Stroke(width = overshootWidth, cap = StrokeCap.Round),
    )
}

private fun Modifier.describedAs(description: String?): Modifier = if (description == null) {
    this
} else {
    clearAndSetSemantics { contentDescription = description }
}

// --- Aperçus -----------------------------------------------------------------

private class RingProgressProvider : PreviewParameterProvider<Float> {
    override val values = sequenceOf(0.35f, 1f, 1.2f)
}

@NeonPreviews
@Composable
private fun MacroRingPreview(@PreviewParameter(RingProgressProvider::class) progress: Float) {
    PreviewSurface {
        MacroRing(
            macro = Macro.CALORIES,
            progress = progress,
            contentDescription = "Calories, 1 220 sur 2 000, 61 %",
        ) {
            Text(text = "780", style = MaterialTheme.typography.displayLarge)
        }
    }
}
