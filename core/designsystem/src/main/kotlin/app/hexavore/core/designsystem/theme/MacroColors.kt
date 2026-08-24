package app.hexavore.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.hexavore.domain.nutrition.Macro

/**
 * Les trois déclinaisons d'une teinte de macro.
 *
 * Seule [base] est écrite à la main. Les deux autres sont dérivées par programme :
 * une déclinaison écrite à la main dérive de sa base dès la première retouche, et
 * plus rien ne garantit que la lueur des protéines soit à la même intensité que
 * celle des lipides.
 */
@Immutable
data class MacroPalette(
    /** La valeur du tableau de docs/08-design-system.md, ajustée au thème. */
    val base: Color,
    /** Lueur portée. Transparente en thème clair : un halo sur fond blanc ressemble à un défaut d'affichage. */
    val glow: Color,
    /** État non atteint. */
    val muted: Color,
)

/**
 * La palette complète, pour un thème donné.
 *
 * C'est la seule source de vérité chromatique du projet. Une couleur écrite
 * ailleurs est un défaut, vérifié par la règle detekt `HardcodedColor`.
 */
@Immutable
data class MacroColorScheme(
    val calories: MacroPalette,
    val protein: MacroPalette,
    val carbs: MacroPalette,
    val sugars: MacroPalette,
    val fat: MacroPalette,
    val fiber: MacroPalette,
) {
    /** Accès par macro, pour que les composants n'aient pas à faire ce `when` eux-mêmes. */
    operator fun get(macro: Macro): MacroPalette = when (macro) {
        Macro.CALORIES -> calories
        Macro.PROTEIN -> protein
        Macro.CARBS -> carbs
        Macro.SUGARS -> sugars
        Macro.FAT -> fat
        Macro.FIBER -> fiber
    }
}

/**
 * Les six teintes de référence, telles qu'écrites dans docs/08-design-system.md.
 *
 * Le violet clair des sucres est **dérivé** de celui des glucides : les sucres en
 * sont un sous-ensemble, et cette parenté doit se voir sans légende. C'est ce qui
 * fait comprendre d'un coup d'œil que la barre des sucres est une sous-graduation
 * de celle des glucides.
 */
private object MacroBaseColors {
    val Calories = Color(0xFF00E5FF)
    val Protein = Color(0xFFFF2D95)
    val Carbs = Color(0xFF9D4EDD)
    val Sugars = Color(0xFFD9A5FF)
    val Fat = Color(0xFFFFB020)
    val Fiber = Color(0xFF39FF88)
}

/** Opacité de la lueur portée. */
private const val GLOW_ALPHA = 0.35f

/** Perte de saturation de l'état non atteint. */
private const val MUTED_SATURATION_LOSS = 0.60f

/**
 * Assombrissement des macros en thème clair.
 *
 * Sans lui, un néon conçu pour ressortir sur un fond presque noir passe sous le
 * seuil de contraste sur un fond blanc.
 */
private const val LIGHT_THEME_DARKENING = 0.25f

internal fun macroColorScheme(dark: Boolean): MacroColorScheme = MacroColorScheme(
    calories = macroPalette(MacroBaseColors.Calories, dark),
    protein = macroPalette(MacroBaseColors.Protein, dark),
    carbs = macroPalette(MacroBaseColors.Carbs, dark),
    sugars = macroPalette(MacroBaseColors.Sugars, dark),
    fat = macroPalette(MacroBaseColors.Fat, dark),
    fiber = macroPalette(MacroBaseColors.Fiber, dark),
)

private fun macroPalette(base: Color, dark: Boolean): MacroPalette {
    val tone = if (dark) base else base.darken(LIGHT_THEME_DARKENING)
    return MacroPalette(
        base = tone,
        glow = if (dark) tone.copy(alpha = GLOW_ALPHA) else Color.Transparent,
        muted = tone.desaturate(MUTED_SATURATION_LOSS),
    )
}

/** Mélange vers le noir. */
internal fun Color.darken(amount: Float): Color = lerp(this, Color.Black, amount)

/** Mélange vers le blanc. Sert au dégradé de progression des jauges. */
internal fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount)

/** Retire [amount] de saturation, en conservant teinte et luminosité. */
internal fun Color.desaturate(amount: Float): Color = mapSaturation { it * (1f - amount) }

/** Ajoute [amount] de saturation. Sert à marquer un dépassement d'objectif. */
internal fun Color.saturate(amount: Float): Color = mapSaturation { it + (1f - it) * amount }

// Les nombres qui suivent sont les constantes de la conversion RVB / TSL, pas des
// valeurs de style : les nommer ne les rendrait pas plus lisibles que la formule.
@Suppress("MagicNumber")
private fun Color.mapSaturation(transform: (Float) -> Float): Color {
    val highest = maxOf(red, green, blue)
    val lowest = minOf(red, green, blue)
    val lightness = (highest + lowest) / 2f
    if (highest == lowest) return this

    val delta = highest - lowest
    val saturation =
        if (lightness > 0.5f) {
            delta / (2f - highest - lowest)
        } else {
            delta / (highest + lowest)
        }
    val hue =
        when (highest) {
            red -> (green - blue) / delta + if (green < blue) 6f else 0f
            green -> (blue - red) / delta + 2f
            else -> (red - green) / delta + 4f
        } / 6f

    return hsl(hue, transform(saturation).coerceIn(0f, 1f), lightness, alpha)
}

@Suppress("MagicNumber")
private fun hsl(hue: Float, saturation: Float, lightness: Float, alpha: Float): Color {
    if (saturation == 0f) return Color(lightness, lightness, lightness, alpha)
    val q =
        if (lightness < 0.5f) {
            lightness * (1f + saturation)
        } else {
            lightness + saturation - lightness * saturation
        }
    val p = 2f * lightness - q
    return Color(
        red = hueToChannel(p, q, hue + 1f / 3f),
        green = hueToChannel(p, q, hue),
        blue = hueToChannel(p, q, hue - 1f / 3f),
        alpha = alpha,
    )
}

@Suppress("MagicNumber")
private fun hueToChannel(p: Float, q: Float, rawHue: Float): Float {
    val hue = (rawHue + 1f) % 1f
    return when {
        hue < 1f / 6f -> p + (q - p) * 6f * hue
        hue < 1f / 2f -> q
        hue < 2f / 3f -> p + (q - p) * (2f / 3f - hue) * 6f
        else -> p
    }
}
