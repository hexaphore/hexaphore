package app.hexavore.domain.goal

import app.hexavore.domain.nutrition.KCAL_PER_GRAM_CARB
import app.hexavore.domain.nutrition.KCAL_PER_GRAM_FAT
import app.hexavore.domain.nutrition.KCAL_PER_GRAM_FIBER
import app.hexavore.domain.nutrition.KCAL_PER_GRAM_PROTEIN
import app.hexavore.domain.nutrition.Macro

/**
 * Les six objectifs d'une journée.
 *
 * Trois sont des cibles à atteindre et trois des limites à ne pas dépasser ; la
 * nature de chacun est portée par [Macro] et non répétée ici.
 *
 * @see docs/03-nutrition-calculs.md
 */
data class DailyGoal(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val sugars: Double,
    val fat: Double,
    val fiber: Double,
) {
    operator fun get(macro: Macro): Double = when (macro) {
        Macro.CALORIES -> kcal
        Macro.PROTEIN -> protein
        Macro.CARBS -> carbs
        Macro.SUGARS -> sugars
        Macro.FAT -> fat
        Macro.FIBER -> fiber
    }

    /**
     * Remplace un compteur, les cinq autres inchangés.
     *
     * Sert à construire un objectif **manuel** compteur par compteur, au fil de la
     * saisie ([D60][decisions]). Un objectif calculé, lui, n'est jamais retouché ainsi :
     * ses six chiffres sortent ensemble de `MacroDistributionPolicy`, et les prendre un
     * par un rendrait la répartition incohérente sans que rien ne le dise.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun with(macro: Macro, value: Double): DailyGoal = when (macro) {
        Macro.CALORIES -> copy(kcal = value)
        Macro.PROTEIN -> copy(protein = value)
        Macro.CARBS -> copy(carbs = value)
        Macro.SUGARS -> copy(sugars = value)
        Macro.FAT -> copy(fat = value)
        Macro.FIBER -> copy(fiber = value)
    }

    /**
     * Ce que les quatre macros énergétiques représentent, en kcal.
     *
     * Les sucres n'y figurent pas : ils sont **inclus dans les glucides**, et les
     * compter en plus doublerait une partie du budget. C'est la même famille d'erreur
     * que les fibres distribuées deux fois ([D24][decisions]), à l'endroit exact où
     * elle se glisserait le plus facilement.
     *
     * [decisions]: docs/11-decisions.md
     */
    val macroEnergy: Double
        get() = KCAL_PER_GRAM_PROTEIN * protein +
            KCAL_PER_GRAM_FAT * fat +
            KCAL_PER_GRAM_FIBER * fiber +
            KCAL_PER_GRAM_CARB * carbs
}
