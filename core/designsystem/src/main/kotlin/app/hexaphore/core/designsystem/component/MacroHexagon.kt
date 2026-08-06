package app.hexaphore.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexaphore.core.designsystem.preview.NeonPreviews
import app.hexaphore.core.designsystem.preview.PreviewSurface
import app.hexaphore.core.designsystem.theme.MacroPalette
import app.hexaphore.core.designsystem.theme.Motion
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.saturate
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.MacroGoalKind
import kotlin.math.min

/**
 * L'hexagone des macros : six compteurs, six quartiers.
 *
 * La figure qui donne son nom au projet, et celle qui répond à une seule question —
 * comment va ma journée. Elle ne dit pas « 87 / 144 g » : c'est le rôle des barres
 * qui l'accompagnent, et les deux ne se concurrencent pas.
 *
 * Hexagone à sommet plat. Les calories occupent le quartier du haut, puis on tourne
 * dans le sens horaire.
 *
 * **Le contour est l'objectif**, et un quartier peut le dépasser. Pour que rien ne
 * sorte de la zone, le dessin entier se met à l'échelle : quand une macro atteint
 * le plafond de 200 %, l'hexagone cible se réduit de moitié. Ce rétrécissement est
 * lui-même le signal — on voit qu'on a débordé avant d'avoir lu quelle macro.
 *
 * Le rayon est proportionnel à la valeur, **pas la surface**. Même convention que
 * l'anneau et les barres ; l'incohérence serait d'en changer d'un composant à
 * l'autre.
 *
 * @see docs/08-design-system.md — section `MacroHexagon`
 * @see docs/11-decisions.md — D33
 */
@Composable
fun MacroHexagon(quarters: Map<Macro, MacroQuarter>, modifier: Modifier = Modifier) {
    val palettes = Macro.entries.associateWith { NeonTheme.macros[it] }
    val outline = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val initialStyle = MaterialTheme.typography.labelSmall
    val spec = tween<Float>(durationMillis = NeonTheme.motion.gaugeValueMillis, easing = Motion.GaugeEasing)

    // L'ajustement s'anime comme les quartiers : sans cela, la figure sauterait de
    // taille au moment precis ou une macro franchit son objectif.
    val fit by animateFloatAsState(
        targetValue = 1f / maxOf(1f, Macro.entries.maxOf { cappedRatio(quarters[it]) }),
        animationSpec = spec,
        label = "ajustement de l hexagone",
    )
    val ratios = Macro.entries.associateWith { macro ->
        animateFloatAsState(cappedRatio(quarters[macro]), spec, label = "quartier ${macro.name}").value
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(HEXAGON_ASPECT)
            // Exclu de l'arbre d'accessibilite : les six memes valeurs sont juste
            // en dessous, dans les barres, sous une forme qui se lit bien mieux a
            // la voix. Cette exclusion tient tant que les barres restent.
            .clearAndSetSemantics { },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(HEXAGON_ASPECT)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width / 2f, size.height / SQRT_THREE) - LabelMargin.toPx()
            val target = radius * fit

            Macro.entries.forEach { macro ->
                drawQuarter(
                    centre = centre,
                    radius = target * ratios.getValue(macro),
                    axis = macro.axisDegrees,
                    palette = palettes.getValue(macro),
                    isLimit = macro.goal == MacroGoalKind.LIMIT,
                    ratio = ratios.getValue(macro),
                    complete = quarters[macro]?.complete ?: true,
                )
            }

            // Le contour par-dessus les quartiers : c'est la reference a laquelle
            // tout se compare, elle ne doit jamais etre masquee.
            drawPath(hexagonPath(centre, target), outline, style = Stroke(width = OutlineWidth.toPx()))

            drawInitials(centre, radius, measurer, initialStyle, palettes)
        }
    }
}

private const val GLOW_SPREAD = 1.06f
private const val OVERSHOOT_SATURATION = 0.30f
private const val FADE_START = 0.85f
private const val ZIGZAG_TEETH = 7
private const val ZIGZAG_DEPTH = 0.05f

private val OutlineWidth: Dp = 2.dp
private val LabelMargin: Dp = 18.dp

private fun DrawScope.drawQuarter(
    centre: Offset,
    radius: Float,
    axis: Float,
    palette: MacroPalette,
    isLimit: Boolean,
    ratio: Float,
    complete: Boolean,
) {
    if (ratio <= 0f) return
    val exceeded = ratio > 1f

    // Une limite reste eteinte sous son seuil : ne pas l'allumer, c'est deja
    // reussir. Elle ne prend la teinte vive qu'au depassement.
    val colour = when {
        isLimit && !exceeded -> palette.muted
        exceeded -> palette.base.saturate(OVERSHOOT_SATURATION)
        else -> palette.base
    }

    if (palette.glow.alpha > 0f && (!isLimit || exceeded)) {
        drawPath(
            path = quarterPath(centre, radius * GLOW_SPREAD, axis),
            color = palette.glow.copy(alpha = palette.glow.alpha * ratio.coerceAtMost(1f)),
        )
    }

    if (complete) {
        drawPath(quarterPath(centre, radius, axis), colour)
    } else {
        // Degrade radial : le quartier s'estompe au lieu de s'arreter net.
        drawPath(
            path = quarterPath(centre, radius, axis),
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to colour, FADE_START to colour, 1f to Color.Transparent),
                center = centre,
                radius = radius,
            ),
        )
    }

    if (ratio >= RATIO_CAP) {
        drawTruncation(centre, radius, axis, colour)
    }
}

/**
 * L'arête d'un quartier tronqué, en dents de scie.
 *
 * Convention de rupture d'échelle des graphiques : elle dit que la valeur continue
 * au-delà, sans prétendre montrer jusqu'où.
 */
private fun DrawScope.drawTruncation(centre: Offset, radius: Float, axis: Float, colour: Color) {
    val depth = radius * ZIGZAG_DEPTH
    val path = Path()
    for (tooth in 0..ZIGZAG_TEETH) {
        val fraction = tooth.toFloat() / ZIGZAG_TEETH
        val degrees = axis - HALF_SECTOR + fraction * SECTOR_DEGREES
        val point = pointAt(centre, radius + if (tooth % 2 == 0) depth else -depth, degrees)
        if (tooth == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, colour, style = Stroke(width = OutlineWidth.toPx(), cap = StrokeCap.Round))
}

/**
 * Les six initiales, posées à l'extérieur du contour.
 *
 * Second canal exigé par la règle de daltonisme : la couleur ne renseigne jamais
 * seule. Une lettre tient là où un libellé complet ne tiendrait pas, même à 200 %
 * de police.
 */
private fun DrawScope.drawInitials(
    centre: Offset,
    radius: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    palettes: Map<Macro, MacroPalette>,
) {
    Macro.entries.forEach { macro ->
        val layout = measurer.measure(macro.initial, style)
        val anchor = pointAt(centre, radius + LabelMargin.toPx() / 2f, macro.axisDegrees)
        drawText(
            textLayoutResult = layout,
            color = palettes.getValue(macro).base,
            topLeft = Offset(anchor.x - layout.size.width / 2f, anchor.y - layout.size.height / 2f),
        )
    }
}

/**
 * L'initiale d'une macro.
 *
 * Écrite ici et non en ressource : ce sont des symboles de figure, pas des libellés
 * à traduire — les six sont les mêmes en français comme en anglais.
 */
private val Macro.initial: String
    get() = when (this) {
        Macro.CALORIES -> "C"
        Macro.PROTEIN -> "P"
        Macro.CARBS -> "G"
        Macro.SUGARS -> "S"
        Macro.FAT -> "L"
        Macro.FIBER -> "F"
    }

// --- Aperçus -----------------------------------------------------------------

@NeonPreviews
@Composable
private fun MacroHexagonJourneeNormalePreview() {
    PreviewSurface {
        MacroHexagon(
            mapOf(
                Macro.CALORIES to MacroQuarter(0.61f),
                Macro.PROTEIN to MacroQuarter(0.78f),
                Macro.FIBER to MacroQuarter(0.34f),
                Macro.CARBS to MacroQuarter(0.72f),
                Macro.SUGARS to MacroQuarter(0.65f),
                Macro.FAT to MacroQuarter(0.58f),
            ),
        )
    }
}

@NeonPreviews
@Composable
private fun MacroHexagonDepassementPreview() {
    PreviewSurface {
        MacroHexagon(
            mapOf(
                Macro.CALORIES to MacroQuarter(1.12f),
                Macro.PROTEIN to MacroQuarter(0.9f),
                Macro.FIBER to MacroQuarter(0.4f),
                Macro.CARBS to MacroQuarter(1.4f),
                Macro.SUGARS to MacroQuarter(2.3f),
                Macro.FAT to MacroQuarter(0.8f),
            ),
        )
    }
}

@NeonPreviews
@Composable
private fun MacroHexagonTotalIncompletPreview() {
    PreviewSurface {
        MacroHexagon(
            mapOf(
                Macro.CALORIES to MacroQuarter(0.55f),
                Macro.PROTEIN to MacroQuarter(0.6f),
                Macro.FIBER to MacroQuarter(0.3f, complete = false),
                Macro.CARBS to MacroQuarter(0.5f),
                Macro.SUGARS to MacroQuarter(0.45f, complete = false),
                Macro.FAT to MacroQuarter(0.4f),
            ),
        )
    }
}
