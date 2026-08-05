package app.hexaphore.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import app.hexaphore.domain.nutrition.Macro
import kotlin.math.cos
import kotlin.math.sin

// Géométrie de l'hexagone, isolée du tracé : elle se raisonne au crayon, et la
// séparer permet de la relire sans traverser les questions de couleur et de lueur.

/** Hauteur d'un hexagone à sommet plat, pour une largeur de 1. */
internal const val SQRT_THREE = 1.7320508f

internal const val HEXAGON_ASPECT = 2f / SQRT_THREE

private const val VERTEX_COUNT = 6

/** Un tour complet divisé par six : l'ouverture d'un quartier. */
internal const val SECTOR_DEGREES = 360f / VERTEX_COUNT

/** Demi-ouverture, de l'axe d'un quartier à l'un de ses deux sommets. */
internal const val HALF_SECTOR = SECTOR_DEGREES / 2f

/**
 * Plafond de représentation.
 *
 * Sans lui, une quantité saisie à 2 000 % réduirait l'hexagone cible à un point,
 * précisément au moment où il faut le lire pour corriger l'erreur.
 */
internal const val RATIO_CAP = 2f

/**
 * L'axe de chaque macro, en degrés depuis l'est, sens antihoraire.
 *
 * Calories en haut, puis sens horaire — donc angles décroissants. Cet ordre est
 * celui de toute l'application : la position sert de second canal là où un libellé
 * ne tient pas, et elle ne renseigne que si elle est la même partout.
 */
internal val Macro.axisDegrees: Float
    get() = when (this) {
        Macro.CALORIES -> 90f
        Macro.PROTEIN -> 30f
        Macro.FIBER -> 330f
        Macro.CARBS -> 270f
        Macro.SUGARS -> 210f
        Macro.FAT -> 150f
    }

internal fun cappedRatio(quarter: MacroQuarter?): Float = (quarter?.ratio ?: 0f).coerceIn(0f, RATIO_CAP)

/**
 * Un point du plan, à un angle et un rayon donnés.
 *
 * L'ordonnée descend à l'écran : le sinus est donc soustrait, faute de quoi
 * l'hexagone se dessine à l'envers et les calories se retrouvent en bas.
 */
internal fun pointAt(centre: Offset, radius: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(
        x = centre.x + radius * cos(radians).toFloat(),
        y = centre.y - radius * sin(radians).toFloat(),
    )
}

/** Le contour complet, sommets aux multiples de 60° — donc arêtes horizontales en haut et en bas. */
internal fun hexagonPath(centre: Offset, radius: Float): Path = Path().apply {
    val vertices = List(VERTEX_COUNT) { pointAt(centre, radius, it * SECTOR_DEGREES) }
    moveTo(vertices.first().x, vertices.first().y)
    vertices.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

/** Le triangle d'un quartier : centre, puis les deux sommets qui bordent son arête. */
internal fun quarterPath(centre: Offset, radius: Float, axis: Float): Path = Path().apply {
    val left = pointAt(centre, radius, axis - HALF_SECTOR)
    val right = pointAt(centre, radius, axis + HALF_SECTOR)
    moveTo(centre.x, centre.y)
    lineTo(left.x, left.y)
    lineTo(right.x, right.y)
    close()
}
