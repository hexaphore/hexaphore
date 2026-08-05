package app.hexaphore.domain.nutrition

/**
 * Le cumul d'une macro sur plusieurs lignes, et ce qu'on sait de sa fiabilité.
 *
 * [complete] vaut `false` dès qu'une seule ligne du cumul avait une valeur
 * inconnue. Le total reste affichable — c'est la somme de ce qu'on sait — mais
 * l'interface doit dire qu'il est minoré, sinon l'utilisateur lit un chiffre faux
 * en croyant lire un chiffre exact.
 *
 * Sans ce drapeau, additionner des `null` comme des zéros donne exactement le même
 * nombre, et plus rien ne permet de faire la différence.
 */
data class MacroTotal(val value: Double, val complete: Boolean)

/**
 * Les six cumuls d'une journée ou d'un repas.
 *
 * @see docs/07-modele-de-donnees.md
 */
data class MacroTotals(
    val calories: MacroTotal,
    val protein: MacroTotal,
    val carbs: MacroTotal,
    val sugars: MacroTotal,
    val fat: MacroTotal,
    val fiber: MacroTotal,
) {
    /** Accès par macro. Même forme que la palette du design system, à dessein. */
    operator fun get(macro: Macro): MacroTotal = when (macro) {
        Macro.CALORIES -> calories
        Macro.PROTEIN -> protein
        Macro.CARBS -> carbs
        Macro.SUGARS -> sugars
        Macro.FAT -> fat
        Macro.FIBER -> fiber
    }

    companion object {
        /** Aucune ligne : tout à zéro, et complet — zéro connu n'est pas zéro ignoré. */
        val Empty: MacroTotals = of(emptyList())

        /**
         * Cumule des lignes, en retenant lesquelles étaient incomplètes.
         *
         * Une liste vide donne des totaux complets à zéro : ne rien avoir mangé de
         * noté est une information exacte, pas une lacune.
         */
        fun of(entries: List<Macros>): MacroTotals {
            fun total(macro: Macro) = MacroTotal(
                value = entries.sumOf { it[macro] ?: 0.0 },
                complete = entries.all { it[macro] != null },
            )

            return MacroTotals(
                calories = total(Macro.CALORIES),
                protein = total(Macro.PROTEIN),
                carbs = total(Macro.CARBS),
                sugars = total(Macro.SUGARS),
                fat = total(Macro.FAT),
                fiber = total(Macro.FIBER),
            )
        }
    }
}
