package app.hexavore.core.designsystem.component

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
import app.hexavore.core.designsystem.R
import app.hexavore.core.designsystem.preview.NeonPreviews
import app.hexavore.core.designsystem.preview.PreviewSurface
import app.hexavore.core.designsystem.theme.MacroPalette
import app.hexavore.core.designsystem.theme.Motion
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.core.designsystem.theme.saturate
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.MacroGoalKind
import kotlin.math.roundToInt

/**
 * La barre d'une macro : libellé à gauche, valeur à droite, jauge en dessous.
 *
 * La barre pleine vaut l'objectif, et rien d'autre. Tant qu'il n'est pas atteint,
 * l'échelle ne bouge pas et il n'y a aucune graduation à interpréter : le
 * remplissage se lit seul.
 *
 * **Le dépassement rétrécit la barre**, comme il rétrécit l'hexagone. Au-delà de
 * l'objectif, l'échelle suit la valeur et le remplissage recule ; un repère
 * apparaît là où l'objectif se situe désormais. Ce repère n'existe donc que
 * lorsqu'il a quelque chose à dire ([D48][decisions]).
 *
 * Ce que la macro décide encore, et que l'appelant ne choisit pas : le suffixe
 * `max` d'une limite, et la phrase annoncée par TalkBack — « sur une limite de »
 * au lieu de « sur un objectif de ». La distinction est portée par le texte, seul
 * canal où elle ne peut pas se confondre avec un état de remplissage.
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
    // Le repere se pose sur le remplissage, jamais sur la piste : c'est le fond
    // qui lui donne du contraste contre un neon sature, dans les deux themes.
    val markerColor = MaterialTheme.colorScheme.background
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
            val scale = displayScale(animatedRatio)
            drawTrack(trackColor)
            drawGauge(palette, animatedRatio, scale)
            if (scale > 1f) drawGoalMarker(markerColor, scale)
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

/** Largeur du repère d'objectif, en part de la hauteur de la barre. */
private const val MARKER_WIDTH_RATIO = 0.5f

/**
 * Ce que représente la barre pleine, en part de l'objectif.
 *
 * Vaut 1 tant que l'objectif n'est pas atteint : la barre pleine est l'objectif, et
 * une échelle élargie par avance serait une graduation à interpréter avant qu'il y
 * ait quoi que ce soit à y lire.
 *
 * Au-delà, l'échelle suit la valeur — donc le remplissage recule à mesure que la
 * quantité monte, et l'objectif se met à occuper moins que la totalité. C'est le
 * même mécanisme que celui de l'hexagone, et il emprunte son plafond : sans lui,
 * une saisie erronée à 2 000 % tasserait tout contre l'origine, précisément au
 * moment où il faut lire la barre pour corriger.
 */
private fun displayScale(ratio: Float): Float = ratio.coerceIn(1f, RATIO_CAP)

private fun DrawScope.drawTrack(color: Color) {
    drawRoundRect(color = color, cornerRadius = CornerRadius(size.height / 2f))
}

/**
 * Le remplissage, identique pour une cible et pour une limite.
 *
 * **Les six barres s'allument de la même façon**, quel que soit le niveau et quelle
 * que soit la nature de la macro ([D47][decisions]). Ce que la distinction perd
 * ici, elle le garde là où elle se lit sans ambiguïté — le suffixe `max` sur la
 * valeur, et la phrase annoncée par TalkBack.
 *
 * [decisions]: docs/11-decisions.md
 *
 * @param scale ce que représente la barre pleine, en part de l'objectif.
 */
private fun DrawScope.drawGauge(palette: MacroPalette, ratio: Float, scale: Float) {
    val filled = (ratio / scale).coerceIn(0f, 1f)
    if (filled == 0f) return
    val color = if (ratio > 1f) palette.base.saturate(OVERSHOOT_SATURATION) else palette.base
    drawGlow(palette, width = size.width * filled)
    drawFill(color, size.width * filled)
}

/**
 * Où l'objectif se situe, une fois qu'il est dépassé.
 *
 * Il n'apparaît qu'au dépassement, parce qu'avant il tomberait sur l'extrémité de
 * la barre et ne dirait rien que la barre ne dise déjà. Une fois l'échelle élargie,
 * il redevient la seule chose qui permette de lire *de combien* : sans lui, une
 * barre aux trois quarts pleine à 133 % ressemble à une barre aux trois quarts
 * pleine à 75 %.
 *
 * Il est **creusé dans le remplissage**, à la teinte du fond, et non posé sur la
 * piste : au-delà de l'objectif le remplissage le recouvre toujours, donc une
 * teinte de piste s'y perdrait.
 */
private fun DrawScope.drawGoalMarker(color: Color, scale: Float) {
    val width = size.height * MARKER_WIDTH_RATIO
    drawRect(
        color = color,
        topLeft = Offset(size.width / scale - width / 2f, 0f),
        size = Size(width = width, height = size.height),
    )
}

private fun DrawScope.drawFill(color: Color, width: Float) {
    drawRoundRect(
        color = color,
        size = Size(width = width, height = size.height),
        cornerRadius = CornerRadius(size.height / 2f),
    )
}

/**
 * La lueur portée, **à pleine intensité quel que soit le niveau**.
 *
 * Elle était auparavant atténuée proportionnellement au remplissage. C'était la
 * dernière condition posée sur le néon, et elle produisait le défaut que
 * [D47][decisions] avait déjà corrigé ailleurs : une barre peu remplie paraissait
 * mal rendue plutôt que basse. La quantité se lit au remplissage et au chiffre ;
 * la lueur n'a pas à la redire ([D48][decisions]).
 *
 * Le retrait en thème clair n'est pas une condition de la barre mais une propriété
 * de la palette — un halo sur fond blanc ressemble à un défaut d'affichage.
 *
 * [decisions]: docs/11-decisions.md
 */
private fun DrawScope.drawGlow(palette: MacroPalette, width: Float) {
    if (palette.glow.alpha == 0f) return
    val glowHeight = size.height * GLOW_HEIGHT_RATIO
    drawRoundRect(
        color = palette.glow,
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
        // Sous l'objectif : pleine lueur des le premier gramme, aucun repere.
        MacroBar(macro = Macro.FIBER, label = "Fibres", consumed = 3f, goal = 35f)
        MacroBar(macro = Macro.PROTEIN, label = "Protéines", consumed = 87f, goal = 144f)
        MacroBar(macro = Macro.CARBS, label = "Glucides", consumed = 240f, goal = 312f)
        // Au-dela : l'echelle suit, le remplissage recule, le repere apparait.
        // Une cible depassee se comporte exactement comme une limite depassee.
        MacroBar(macro = Macro.SUGARS, label = "Sucres", consumed = 78f, goal = 63f)
        MacroBar(macro = Macro.PROTEIN, label = "Protéines", consumed = 190f, goal = 144f)
        // Au plafond : l'objectif tient les deux tiers de la barre, et n'en bouge plus.
        MacroBar(macro = Macro.FAT, label = "Lipides", consumed = 210f, goal = 70f)
    }
}
