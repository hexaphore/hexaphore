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

    companion object {
        /**
         * Objectif provisoire, en dur. **Dette assumée de la tranche 1.**
         *
         * Le calcul réel demande un profil, un poids cible et une échéance, qui
         * n'existent qu'à partir de la tranche 4. Plutôt que d'attendre, l'accueil
         * se construit contre une valeur plausible : c'est un raccourci écrit,
         * daté, et dont la suppression est un critère de fin de la tranche 4.
         *
         * La répartition suit les règles de docs/03 pour 2 000 kcal en maintien,
         * sur un poids de référence de 70 kg — protéines 1,6 g/kg, lipides 30 %
         * des calories, fibres 14 g pour 1 000 kcal, glucides en solde une fois
         * les fibres déduites. Le contrôle de cohérence retombe à 1 kcal près :
         * 448 + 603 + 56 + 892 = 1 999.
         *
         * @see docs/11-decisions.md — D30
         */
        val Placeholder: DailyGoal =
            DailyGoal(
                kcal = 2000.0,
                protein = 112.0,
                carbs = 223.0,
                sugars = 50.0,
                fat = 67.0,
                fiber = 28.0,
            )
    }
}
