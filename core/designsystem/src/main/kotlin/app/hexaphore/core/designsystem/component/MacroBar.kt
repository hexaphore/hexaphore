package app.hexaphore.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexaphore.core.designsystem.R
import app.hexaphore.core.designsystem.preview.NeonPreviews
import app.hexaphore.core.designsystem.preview.PreviewSurface
import app.hexaphore.core.designsystem.theme.MacroPalette
import app.hexaphore.core.designsystem.theme.Motion
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.core.designsystem.theme.saturate
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.MacroGoalKind
import kotlin.math.roundToInt

/**
 * La barre d'une macro : libellé à gauche, valeur à droite, jauge en dessous.
 *
 * Le comportement n'est pas choisi par l'appelant, il est **déduit de la macro**.
 * Une jauge de sucres qui se remplirait comme une jauge de protéines ferait passer
 * un dépassement pour une réussite ; laisser chaque écran trancher, c'est accepter
 * que l'un d'eux se trompe un jour.
 *
 * Trois signaux distinguent une limite d'un objectif, et ils sont redondants
 * exprès — l'un d'eux suffit à lever le doute :
 *
 * 1. la valeur porte un `max` (« 41 / 63 g max ») ;
 * 2. la barre porte un repère au niveau de la limite, et son échelle va au-delà
 *    pour qu'un dépassement ait où s'afficher ;
 * 3. TalkBack annonce « sur une limite de » au lieu de « sur un objectif de ».
 *
 * La barre ne reste plus **éteinte** sous le seuil. C'était le quatrième signal, et
 * il coûtait trop cher : une interface où trois macros sur six brillent et trois
 * non se lit comme un défaut d'affichage ([D47][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 *
 * @see docs/03-nutrition-calculs.md
 * @see docs/08-design-system.md
 */
@Composable
fun MacroBar(
    macro: Macro,
    label: String,
    consumed: Float,
    goal: Float,
    modifier: Modifier = Modifier,
    unit: MacroUnit = MacroUnit.GRAM,
) {
    val palette = NeonTheme.macros[macro]
    val trackColor = MaterialTheme.colorScheme.outline
    val durationMillis = NeonTheme.motion.gaugeValueMillis
    val isLimit = macro.goal == MacroGoalKind.LIMIT

    val ratio = if (goal > 0f) consumed / goal else 0f
    val animatedRatio by animateFloatAsState(
        targetValue = ratio.coerceAtLeast(0f),
        animationSpec = tween(durationMillis = durationMillis, easing = Motion.GaugeEasing),
        label = "remplissage de la barre",
    )

    val description =
        stringResource(
            if (isLimit) R.string.ds_gauge_description_cap else R.string.ds_gauge_description,
            label,
            consumed.roundToInt(),
            stringResource(unit.spokenRes),
            goal.roundToInt(),
            (ratio * PERCENT).roundToInt(),
        )

    Column(
        // Une jauge est un seul nœud d'accessibilité : trois nœuds à traverser
        // pour une information qui tient en une phrase, c'est trois de trop.
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        MacroBarHeader(label = label, consumed = consumed, goal = goal, unit = unit, isLimit = isLimit)
        Canvas(
            modifier =
            Modifier
                .padding(top = Spacing.xs)
                .fillMaxWidth()
                .height(MacroBarDefaults.Height),
        ) {
            drawTrack(trackColor)
            drawGauge(palette, animatedRatio, if (isLimit) LIMIT_SCALE else 1f)
            if (isLimit) drawLimitMarker(trackColor)
        }
    }
}

/** Libellé à gauche, valeur à droite. Une limite se signale par son suffixe. */
@Composable
private fun MacroBarHeader(label: String, consumed: Float, goal: Float, unit: MacroUnit, isLimit: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
            stringResource(
                if (isLimit) R.string.ds_gauge_value_limit else R.string.ds_gauge_value,
                consumed.roundToInt(),
                goal.roundToInt(),
                stringResource(unit.shortRes),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Unités affichées par une jauge.
 *
 * Deux libellés par unité : l'abrégé pour l'œil, le mot entier pour TalkBack.
 * Faire lire « g » à une synthèse vocale donne « gé », ce qui n'est pas une unité.
 */
enum class MacroUnit(@StringRes internal val shortRes: Int, @StringRes internal val spokenRes: Int) {
    GRAM(R.string.ds_unit_gram_short, R.string.ds_unit_gram_spoken),
    MILLILITRE(R.string.ds_unit_millilitre_short, R.string.ds_unit_millilitre_spoken),
    KCAL(R.string.ds_unit_kcal_short, R.string.ds_unit_kcal_spoken),
}

/** Géométrie de la barre. */
object MacroBarDefaults {
    val Height: Dp = 6.dp
}

private const val PERCENT = 100f
private const val OVERSHOOT_SATURATION = 0.30f
private const val GLOW_HEIGHT_RATIO = 2.5f

/**
 * Part de l'échelle occupée par la limite.
 *
 * La barre va au-delà du seuil, sinon un dépassement n'aurait nulle part où
 * s'afficher et le repère se confondrait avec l'extrémité.
 */
private const val LIMIT_SCALE = 1.25f

private fun DrawScope.drawTrack(color: Color) {
    drawRoundRect(color = color, cornerRadius = CornerRadius(size.height / 2f))
}

/**
 * Le remplissage, identique pour une cible et pour une limite.
 *
 * **Les six barres s'allument de la même façon**, quel que soit le niveau. Une
 * limite ne reste plus éteinte sous son seuil : une interface où trois macros sur
 * six brillent et trois non se lit comme un défaut d'affichage plutôt que comme une
 * information ([D47][decisions]). Ce que la distinction perd ici, elle le garde là
 * où elle se lit sans ambiguïté — le suffixe `max` sur la valeur, et la phrase
 * annoncée par TalkBack.
 *
 * [decisions]: docs/11-decisions.md
 *
 * @param scale la part de l'échelle occupée par l'objectif. Une limite en occupe
 *   moins que la totalité, pour qu'un dépassement ait où s'afficher.
 */
private fun DrawScope.drawGauge(palette: MacroPalette, ratio: Float, scale: Float) {
    val filled = (ratio / scale).coerceIn(0f, 1f)
    if (filled == 0f) return
    val color = if (ratio > 1f) palette.base.saturate(OVERSHOOT_SATURATION) else palette.base
    drawGlow(palette, width = size.width * filled, intensity = filled)
    drawFill(color, size.width * filled)
}

/**
 * Le repère du seuil, sur une limite.
 *
 * En teinte de piste et non de macro : ce n'est pas une valeur, c'est une
 * graduation. Il dit où se situe le plafond sur une barre qui va au-delà, et c'est
 * le seul signal visuel qui reste pour distinguer une limite d'une cible.
 */
private fun DrawScope.drawLimitMarker(trackColor: Color) {
    drawRect(
        color = trackColor,
        topLeft = Offset(size.width / LIMIT_SCALE, 0f),
        size = Size(width = size.height / 2f, height = size.height),
    )
}

private fun DrawScope.drawFill(color: Color, width: Float) {
    drawRoundRect(
        color = color,
        size = Size(width = width, height = size.height),
        cornerRadius = CornerRadius(size.height / 2f),
    )
}

private fun DrawScope.drawGlow(palette: MacroPalette, width: Float, intensity: Float) {
    if (palette.glow.alpha == 0f) return
    val glowHeight = size.height * GLOW_HEIGHT_RATIO
    drawRoundRect(
        color = palette.glow.copy(alpha = palette.glow.alpha * intensity),
        topLeft = Offset(0f, (size.height - glowHeight) / 2f),
        size = Size(width = width, height = glowHeight),
        cornerRadius = CornerRadius(glowHeight / 2f),
    )
}

// --- Aperçus -----------------------------------------------------------------

@NeonPreviews
@Composable
private fun MacroBarPreview() {
    PreviewSurface {
        // Objectifs : la barre se remplit, la lueur croît.
        MacroBar(macro = Macro.PROTEIN, label = "Protéines", consumed = 87f, goal = 144f)
        MacroBar(macro = Macro.FIBER, label = "Fibres", consumed = 12f, goal = 35f)
        // Limites : éteintes en dessous du seuil, allumées seulement au-delà.
        MacroBar(macro = Macro.CARBS, label = "Glucides", consumed = 240f, goal = 312f)
        MacroBar(macro = Macro.SUGARS, label = "Sucres", consumed = 41f, goal = 63f)
        MacroBar(macro = Macro.SUGARS, label = "Sucres", consumed = 78f, goal = 63f)
        MacroBar(macro = Macro.FAT, label = "Lipides", consumed = 52f, goal = 70f)
    }
}
