package app.hexaphore.domain.goal

import app.hexaphore.domain.nutrition.Macro

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
