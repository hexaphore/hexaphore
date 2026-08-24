package app.hexavore.core.designsystem.component

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.R
import app.hexavore.core.designsystem.preview.NeonPreviews
import app.hexavore.core.designsystem.preview.PreviewSurface
import app.hexavore.core.designsystem.theme.Motion
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.core.designsystem.theme.TouchTarget
import app.hexavore.domain.nutrition.Macro

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
    availability: NeonButtonAvailability = NeonButtonAvailability.AVAILABLE,
) {
    val palette = NeonTheme.macros[macro]
    val durationMillis = NeonTheme.motion.buttonPressMillis
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val clickable = availability != NeonButtonAvailability.DISABLED
    val unavailableState = stringResource(R.string.ds_button_unavailable)
    val accent =
        if (availability == NeonButtonAvailability.AVAILABLE) palette.base else palette.muted
    val pressSpec = tween<Float>(durationMillis = durationMillis, easing = Motion.PressEasing)
    val scale by animateFloatAsState(
        // Un bouton indisponible réagit quand même : c'est la seule chose qui dise
        // à l'utilisateur que son appui a été reçu, avant l'explication qui suit.
        targetValue = if (pressed && clickable) PRESSED_SCALE else 1f,
        animationSpec = pressSpec,
        label = "echelle du bouton",
    )
    val glowIntensity by animateFloatAsState(
        targetValue = glowTarget(availability, pressed),
        animationSpec = pressSpec,
        label = "lueur du bouton",
    )
    val background = buttonBackground(style, accent, pressed)

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
                enabled = clickable,
                onClick = onClick,
            ).defaultMinSize(minHeight = TouchTarget.min)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md)
            .then(
                if (availability == NeonButtonAvailability.UNAVAILABLE) {
                    Modifier.semantics { stateDescription = unavailableState }
                } else {
                    Modifier
                },
            ),
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

/**
 * Ce que le bouton accepte, et ce qu'il en montre.
 *
 * Deux façons d'être éteint, et elles ne disent pas la même chose. Le cas qui
 * manquait est [UNAVAILABLE] : un bouton grisé mais qui répond, parce qu'un appui
 * sans le moindre retour laisse croire que l'application n'a rien reçu. C'est
 * exactement ce que demande docs/02 pour les modes IA sans clé configurée —
 * « visible mais grisé ; un tap ouvre une explication courte ».
 */
enum class NeonButtonAvailability {
    /** Utilisable. Teinte pleine, lueur au repos. */
    AVAILABLE,

    /**
     * Momentanément indisponible, mais l'appui est reçu et doit expliquer pourquoi.
     *
     * Grisé et sans lueur au repos, il réagit néanmoins à l'appui — réduction
     * d'échelle et lueur brève — puis appelle `onClick`, à qui il revient de dire
     * ce qui manque. Masquer le bouton laisserait croire que la fonctionnalité
     * n'existe pas ; le rendre inerte laisse croire que l'appareil ne répond plus.
     */
    UNAVAILABLE,

    /**
     * Inerte. Aucune réaction, et le lecteur d'écran l'annonce désactivé.
     *
     * Pour ce qui n'a rien à expliquer : une action déjà en cours, un formulaire
     * incomplet dont le manque est visible ailleurs à l'écran.
     */
    DISABLED,
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

/**
 * Intensité de la lueur visée.
 *
 * Un bouton indisponible n'a pas de lueur au repos — c'est ce qui le distingue —
 * mais il en émet une à l'appui, parce qu'un appui sans retour ne se distingue pas
 * d'une application figée.
 */
private fun glowTarget(availability: NeonButtonAvailability, pressed: Boolean): Float = when {
    availability == NeonButtonAvailability.DISABLED -> 0f
    pressed -> PRESSED_GLOW
    availability == NeonButtonAvailability.UNAVAILABLE -> 0f
    else -> RESTING_GLOW
}

private fun buttonBackground(style: NeonButtonStyle, accent: Color, pressed: Boolean): Brush = when {
    style == NeonButtonStyle.FILLED ->
        Brush.verticalGradient(
            listOf(accent.copy(alpha = FILLED_TOP_ALPHA), accent.copy(alpha = FILLED_BOTTOM_ALPHA)),
        )
    pressed -> SolidColor(accent.copy(alpha = PRESSED_BACKGROUND_ALPHA))
    else -> SolidColor(Color.Transparent)
}

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
        NeonButton(text = "Créer cet aliment", onClick = {}, macro = Macro.FIBER)
        NeonButton(
            text = "Analyser",
            onClick = {},
            availability = NeonButtonAvailability.UNAVAILABLE,
        )
        NeonButton(
            text = "Enregistrement…",
            onClick = {},
            availability = NeonButtonAvailability.DISABLED,
        )
    }
}
