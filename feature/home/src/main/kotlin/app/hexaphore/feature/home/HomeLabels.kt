package app.hexaphore.feature.home

import androidx.annotation.StringRes
import app.hexaphore.domain.nutrition.Macro
import java.text.NumberFormat

internal val Macro.labelRes: Int
    @StringRes get() = when (this) {
        Macro.CALORIES -> R.string.macro_calories
        Macro.PROTEIN -> R.string.macro_protein
        Macro.CARBS -> R.string.macro_carbs
        Macro.SUGARS -> R.string.macro_sugars
        Macro.FAT -> R.string.macro_fat
        Macro.FIBER -> R.string.macro_fiber
    }

/**
 * Une quantité en grammes, telle qu'on l'écrit en français.
 *
 * Séparateur décimal de la locale, et une décimale au plus : « 1,4 g » et non
 * « 1.4000000000000001 g ». Les valeurs entières perdent leur décimale, parce
 * qu'elles sont la majorité et que « 52,0 g » n'apporte rien.
 *
 * Restera ici tant qu'un seul écran en a besoin. `MacroFormatter` de
 * docs/06-architecture.md naîtra quand un deuxième le réclamera.
 */
internal fun formatGrams(value: Double): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
