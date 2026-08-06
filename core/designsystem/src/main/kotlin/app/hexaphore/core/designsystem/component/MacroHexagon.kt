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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
    // Grasse et de la taille d'un titre : ces six lettres sont le second canal
    // exige par la regle de daltonisme, et un second canal qu'il faut chercher des
    // yeux n'en est pas un. En labelSmall, elles se lisaient a peine sur le fond.
    val initialStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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

            // Ce qu'il faut reserver hors de l'hexagone cible : la lueur, un
            // intervalle, puis la lettre. Sans cette reserve, la lueur du quartier
            // le plus rempli sortait de la zone et se faisait rogner net -- un neon
            // coupe au couteau, ce qu'aucun neon ne fait.
            val labelExtent = measurer.measure(Macro.CALORIES.initial, initialStyle).size.let {
                maxOf(it.width, it.height) / 2f
            }
            val labelRadius0 = GlowRoom.toPx() + LabelGap.toPx() + labelExtent
            val radius = min(size.width / 2f, size.height / SQRT_THREE) - labelRadius0 - labelExtent
            val target = radius * fit

            // Deux passes, et l'ordre compte. Les quartiers d'abord, tous ; les
            // lueurs ensuite, par-dessus. Dessinee quartier par quartier, la lueur
            // d'une arete laterale se faisait recouvrir par le remplissage du
            // quartier voisin, et il ne restait de neon que sur l'arete exterieure.
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

            Macro.entries.forEach { macro ->
                val ratio = ratios.getValue(macro)
                if (!macro.glows(ratio)) return@forEach
                drawQuarterGlow(
                    centre = centre,
                    radius = target * ratio,
                    axis = macro.axisDegrees,
                    palette = palettes.getValue(macro),
                    intensity = ratio.coerceAtMost(1f),
                    complete = quarters[macro]?.complete ?: true,
                )
            }

            // Le contour par-dessus les quartiers : c'est la reference a laquelle
            // tout se compare, elle ne doit jamais etre masquee.
            drawPath(hexagonPath(centre, target), outline, style = Stroke(width = OutlineWidth.toPx()))

            drawInitials(centre, radius + labelRadius0, measurer, initialStyle, palettes)
        }
    }
}

private const val OVERSHOOT_SATURATION = 0.30f
private const val FADE_START = 0.85f
private const val ZIGZAG_TEETH = 7
private const val ZIGZAG_DEPTH = 0.05f

/**
 * Le nombre de couches de lueur.
 *
 * Même technique que `NeonButton` : des contours de plus en plus larges et de moins
 * en moins opaques, faute d'un flou disponible partout — `BlurMaskFilter` n'est pas
 * accéléré matériellement et imposerait un rendu logiciel à chaque image animée.
 * Trois couches suffisent à ce que l'œil ne distingue plus les paliers.
 */
private const val GLOW_LAYERS = 3

private val OutlineWidth: Dp = 2.dp

/** Ce que la lueur déborde vers l'extérieur, et qu'il faut donc réserver. */
private val GlowRoom: Dp = 12.dp

/** Entre la lueur et la lettre, pour que la seconde ne baigne pas dans la première. */
private val LabelGap: Dp = 6.dp

private val GlowSpread: Dp = GlowRoom / GLOW_LAYERS

/**
 * `true` quand ce quartier doit luire.
 *
 * Une limite reste éteinte sous son seuil : ne pas l'allumer, c'est déjà réussir.
 * Une journée bien tenue montre donc trois quartiers vifs et trois quartiers sourds.
 */
private fun Macro.glows(ratio: Float): Boolean = ratio > 0f && (goal != MacroGoalKind.LIMIT || ratio > 1f)

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

    drawPath(quarterPath(centre, radius, axis), brush = fillBrush(colour, centre, radius, complete))

    if (ratio >= RATIO_CAP) {
        drawTruncation(centre, radius, axis, colour)
    }
}

/**
 * La lueur d'un quartier, tracée **sur ses trois arêtes**.
 *
 * Un contour et non une silhouette élargie, et c'est toute la différence. La
 * silhouette — un second triangle un peu plus grand, posé derrière — ne se voyait
 * que là où elle dépassait, c'est-à-dire sur la seule arête extérieure : les deux
 * arêtes latérales sont mitoyennes, et le quartier voisin recouvrait ce qui
 * dépassait de son côté. Elle avait en outre un bord franc, puisqu'un triangle
 * plein s'arrête là où il s'arrête. Une lueur qui s'arrête net n'est pas une lueur.
 *
 * Le tracé est centré sur le chemin : chaque couche déborde donc de part et
 * d'autre, à l'intérieur du quartier comme au-dehors, et les six lueurs se
 * rejoignent au centre où les six pointes se touchent.
 */
private fun DrawScope.drawQuarterGlow(
    centre: Offset,
    radius: Float,
    axis: Float,
    palette: MacroPalette,
    intensity: Float,
    complete: Boolean,
) {
    if (palette.glow.alpha == 0f) return
    val path = quarterPath(centre, radius, axis)
    val spread = GlowSpread.toPx()

    repeat(GLOW_LAYERS) { layer ->
        val colour = palette.glow.copy(
            alpha = palette.glow.alpha * intensity / (GLOW_LAYERS * (layer + 1)),
        )
        drawPath(
            path = path,
            brush = fillBrush(colour, centre, radius, complete),
            style = Stroke(width = spread * (layer + 1) * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }
}

/**
 * De quoi peindre un quartier, ou sa lueur.
 *
 * Un total amputé d'une valeur inconnue s'estompe au lieu de s'arrêter net : on ne
 * sait pas où il s'arrête, la figure ne prétend donc pas le savoir. Le dégradé
 * s'applique au remplissage **et** à la lueur, sans quoi l'arête floue du quartier
 * aurait été soulignée d'un trait de néon parfaitement net.
 */
private fun fillBrush(colour: Color, centre: Offset, radius: Float, complete: Boolean): Brush = when {
    complete -> SolidColor(colour)
    else -> Brush.radialGradient(
        colorStops = arrayOf(0f to colour, FADE_START to colour, 1f to Color.Transparent),
        center = centre,
        radius = radius,
    )
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
 * Les six initiales, posées à l'extérieur de la zone.
 *
 * Second canal exigé par la règle de daltonisme : la couleur ne renseigne jamais
 * seule. Une lettre tient là où un libellé complet ne tiendrait pas, même à 200 %
 * de police — à condition qu'elle se voie, d'où une graisse et une taille de titre
 * plutôt que la légende de 12 points qu'elles portaient d'abord.
 *
 * Leur rayon est celui de la **zone** et non du contour : elles ne bougent pas
 * quand l'hexagone cible rétrécit sous l'effet d'un dépassement. Six repères qui se
 * déplaceraient à chaque saisie ne seraient plus des repères.
 *
 * @param radius le rayon auquel poser le centre des lettres, lueur déjà déduite.
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
        val anchor = pointAt(centre, radius, macro.axisDegrees)
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
