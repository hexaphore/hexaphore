package app.hexaphore.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexaphore.core.designsystem.preview.NeonPreviews
import app.hexaphore.core.designsystem.preview.PreviewSurface
import app.hexaphore.core.designsystem.theme.Motion
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Radius
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.core.designsystem.theme.TouchTarget
import app.hexaphore.domain.nutrition.Macro

/**
 * Le bouton du projet : bordure et texte en teinte néon, lueur externe au repos.
 *
 * Un seul bouton [NeonButtonStyle.FILLED] par écran. La règle n'est pas esthétique :
 * c'est elle qui empêche l'inflation visuelle, où trois boutons se disputent
 * l'attention et où plus aucun ne l'obtient.
 *
 * Le fond de la variante pleine reste sombre et teinté plutôt que néon plein : un
 * aplat néon imposerait du texte foncé par-dessus, ce que la charte interdit —
 * illisible en extérieur, et le néon doit rester l'élément clair de la paire.
 *
 * @see docs/08-design-system.md
 */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    macro: Macro = Macro.CALORIES,
    style: NeonButtonStyle = NeonButtonStyle.OUTLINED,
    enabled: Boolean = true,
) {
    val palette = NeonTheme.macros[macro]
    val durationMillis = NeonTheme.motion.buttonPressMillis
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val accent = if (enabled) palette.base else palette.muted
    val pressSpec = tween<Float>(durationMillis = durationMillis, easing = Motion.PressEasing)
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = pressSpec,
        label = "echelle du bouton",
    )
    val glowIntensity by animateFloatAsState(
        targetValue =
        when {
            !enabled -> 0f
            pressed -> PRESSED_GLOW
            else -> RESTING_GLOW
        },
        animationSpec = pressSpec,
        label = "lueur du bouton",
    )

    val background =
        when {
            style == NeonButtonStyle.FILLED ->
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = FILLED_TOP_ALPHA), accent.copy(alpha = FILLED_BOTTOM_ALPHA)),
                )
            pressed -> SolidColor(accent.copy(alpha = PRESSED_BACKGROUND_ALPHA))
            else -> SolidColor(Color.Transparent)
        }

    Box(
        modifier =
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Avant le clip : une lueur clippée ne déborde plus, donc ne luit plus.
            .drawBehind { drawButtonGlow(palette.glow, glowIntensity, GlowSpread.toPx()) }
            .clip(Radius.pill)
            .background(background, Radius.pill)
            .border(BorderWidth, accent, Radius.pill)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            ).defaultMinSize(minHeight = TouchTarget.min)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
    }
}

/** Contour ou fond plein. Un seul bouton plein par écran. */
enum class NeonButtonStyle {
    OUTLINED,
    FILLED,
}

private val BorderWidth: Dp = 1.5.dp
private val GlowSpread: Dp = 4.dp

private const val PRESSED_SCALE = 0.97f
private const val RESTING_GLOW = 0.6f
private const val PRESSED_GLOW = 1f
private const val PRESSED_BACKGROUND_ALPHA = 0.12f
private const val FILLED_TOP_ALPHA = 0.28f
private const val FILLED_BOTTOM_ALPHA = 0.12f
private const val GLOW_LAYERS = 3

private fun DrawScope.drawButtonGlow(glow: Color, intensity: Float, spreadPx: Float) {
    if (glow.alpha == 0f || intensity == 0f) return
    repeat(GLOW_LAYERS) { layer ->
        val spread = spreadPx * (layer + 1)
        val height = size.height + spread * 2f
        drawRoundRect(
            color = glow.copy(alpha = glow.alpha * intensity / (GLOW_LAYERS * (layer + 1))),
            topLeft = Offset(-spread, -spread),
            size = Size(width = size.width + spread * 2f, height = height),
            cornerRadius = CornerRadius(height / 2f),
        )
    }
}

// --- Aperçus -----------------------------------------------------------------

@NeonPreviews
@Composable
private fun NeonButtonPreview() {
    PreviewSurface {
        NeonButton(text = "Enregistrer", onClick = {}, style = NeonButtonStyle.FILLED)
        NeonButton(text = "Ajouter une ligne", onClick = {})
        NeonButton(text = "Creer cet aliment", onClick = {}, macro = Macro.FIBER)
        NeonButton(text = "Analyser", onClick = {}, enabled = false)
    }
}
