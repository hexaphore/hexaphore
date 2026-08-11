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

    /** Remplace un compteur, les cinq autres inchangés. */
    fun with(macro: Macro, value: Double): DailyGoal = when (macro) {
        Macro.CALORIES -> copy(kcal = value)
        Macro.PROTEIN -> copy(protein = value)
        Macro.CARBS -> copy(carbs = value)
        Macro.SUGARS -> copy(sugars = value)
        Macro.FAT -> copy(fat = value)
        Macro.FIBER -> copy(fiber = value)
    }

    /**
     * Ce que le calcul propose, **sauf** là où l'utilisateur a tranché.
     *
     * C'est la règle qui protège le travail de l'utilisateur : un recalcul déclenché
     * par une correction de taille ou de poids ne réécrit pas un compteur qu'il a fixé
     * lui-même ([Goal.manualFields]). Sans elle, corriger un chiffre du profil ferait
     * disparaître en silence un objectif de protéines choisi trois semaines plus tôt —
     * et rien à l'écran ne dirait qu'il vient d'être remplacé.
     *
     * L'application n'est pas rendue **incohérente** pour autant : les calories font
     * foi et les macros sont des répartitions indicatives ([docs/03][calculs]). Fixer
     * les protéines sans toucher aux calories creuse simplement l'écart que
     * [app.hexaphore.domain.usecase.GoalPlan.energyGap] mesure, et c'est un écart
     * voulu par celui qui l'a saisi.
     *
     * [calculs]: docs/03-nutrition-calculs.md
     */
    fun overriddenBy(manual: Map<Macro, Double>): DailyGoal =
        manual.entries.fold(this) { goal, (macro, value) -> goal.with(macro, value) }

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
