package app.hexavore.core.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * Ce qu'un quartier de l'hexagone doit montrer.
 *
 * @param ratio avancement, où 1 vaut l'objectif atteint. Au-delà, le quartier sort
 *   du contour.
 * @param complete `false` quand le total est amputé d'au moins une valeur inconnue.
 *   L'arête extérieure s'estompe alors : on ne sait pas où ça s'arrête, la figure
 *   ne prétend donc pas le savoir.
 */
@Immutable
data class MacroQuarter(val ratio: Float, val complete: Boolean = true)
