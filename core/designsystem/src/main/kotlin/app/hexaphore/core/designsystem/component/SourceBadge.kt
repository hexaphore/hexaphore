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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.hexaphore.domain.diary.EntrySource

/**
 * L'étiquette d'origine d'un plat.
 *
 * Un plat, une source : elle appartient au plat et jamais à ses aliments. Le badge
 * se pose donc une fois, en tête du plat.
 *
 * Un contenu **proposé** par un modèle — photo ou description — se distingue par la
 * **forme** et jamais par la teinte : contour en pointillés, glyphe en vague. Trois
 * raisons de ne pas y consacrer une couleur : les six teintes portent un sens et un
 * seul ; la règle de daltonisme du projet interdit de toute façon qu'une couleur
 * porte seule une information, donc un second canal aurait été nécessaire en plus ;
 * et un contour discontinu dit « à vérifier » sans légende, dans les deux thèmes.
 *
 * Le pointillé plutôt qu'un triangle d'alerte : une proposition n'est pas un
 * problème, c'est une valeur moins sûre. Le signal doit le dire sans dramatiser.
 *
 * @see docs/08-design-system.md
 */
@Composable
fun SourceBadge(source: EntrySource, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(Radius.field)
    val label = stringResource(source.labelRes)
    val description = if (source.proposed) stringResource(R.string.ds_source_proposed_a11y, label) else label

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .drawBehind { drawBadgeOutline(ink, source.proposed) }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            // Un badge est une seule information : « Photo, contenu propose » plutot
            // que le libelle puis, separement, la description du glyphe.
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (source.proposed) {
            Canvas(Modifier.size(GlyphSize)) { drawWave(ink) }
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = ink)
    }
}

private val GlyphSize: Dp = 16.dp
private val BorderWidth: Dp = 1.5.dp

// En dp et non en pixels : exprimes en pixels, des tirets de six unites mesurent
// deux millimetres sur une dalle a densite 1 et un quart de millimetre sur une
// dalle a densite 4 — c'est-a-dire rien du tout. C'est ce qui rendait le contour
// des plats proposes indistinguable d'un trait plein sur un telephone recent.
private val DashOn: Dp = 3.dp
private val DashOff: Dp = 2.dp

private const val SOLID_BORDER_ALPHA = 0.4f

private fun DrawScope.drawBadgeOutline(color: Color, proposed: Boolean) {
    val stroke = BorderWidth.toPx()
    // Le trace est centre sur le chemin : sans cet encart, la moitie exterieure du
    // trait sort des limites du noeud et se fait rogner.
    val inset = stroke / 2f

    drawRoundRect(
        // Un contour plein reste discret ; un contour en pointilles doit se voir,
        // sinon il ne signale rien.
        color = if (proposed) color else color.copy(alpha = SOLID_BORDER_ALPHA),
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(Radius.field.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = if (proposed) PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())) else null,
        ),
    )
}

// Points de contrôle de l'ondulation, en fraction de la boîte du glyphe. Les
// exprimer ainsi plutôt qu'en dp fait que la vague suit la taille du texte.
private const val WAVE_QUARTER = 0.25f
private const val WAVE_HALF = 0.5f
private const val WAVE_THREE_QUARTERS = 0.75f
private const val WAVE_AMPLITUDE_RATIO = 0.5f
private const val WAVE_STROKE_RATIO = 0.12f

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

    val path = Path().apply {
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

    drawPath(path = path, color = color, style = Stroke(width = width * WAVE_STROKE_RATIO, cap = StrokeCap.Round))
}

private val EntrySource.labelRes: Int
    get() = when (this) {
        EntrySource.MANUAL -> R.string.ds_source_manual
        EntrySource.SEARCH -> R.string.ds_source_search
        EntrySource.BARCODE -> R.string.ds_source_barcode
        EntrySource.PHOTO_AI -> R.string.ds_source_photo
        EntrySource.TEXT_AI -> R.string.ds_source_text
        EntrySource.FAVORITE -> R.string.ds_source_favorite
    }

// --- Aperçus -----------------------------------------------------------------

@NeonPreviews
@Composable
private fun SourceBadgePreview() {
    PreviewSurface {
        EntrySource.entries.forEach { source -> SourceBadge(source = source) }
    }
}
