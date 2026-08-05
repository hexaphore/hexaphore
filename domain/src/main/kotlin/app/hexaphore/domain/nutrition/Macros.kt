package app.hexaphore.domain.nutrition

/**
 * Les valeurs nutritionnelles d'une ligne de journal.
 *
 * `null` signifie **inconnu**, jamais zéro. C'est la distinction la plus facile à
 * perdre et la plus coûteuse à récupérer : un aliment dont les fibres ne sont pas
 * renseignées n'apporte pas zéro gramme de fibres, on ne sait simplement pas
 * combien. Les additionner comme des zéros fausse un journal entier en silence,
 * sans qu'aucun écran n'ait l'air anormal.
 *
 * Les calories, elles, ne sont jamais inconnues : une fiche sans énergie n'est pas
 * exploitable, et le parcours de saisie oblige à en fournir une.
 *
 * @see docs/04-sources-de-donnees.md
 * @see docs/07-modele-de-donnees.md
 */
data class Macros(
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val sugars: Double?,
    val fat: Double?,
    val fiber: Double?,
) {
    /** Accès par macro, pour parcourir les six sans les énumérer à la main. */
    operator fun get(macro: Macro): Double? = when (macro) {
        Macro.CALORIES -> kcal
        Macro.PROTEIN -> protein
        Macro.CARBS -> carbs
        Macro.SUGARS -> sugars
        Macro.FAT -> fat
        Macro.FIBER -> fiber
    }

    companion object {
        /** Une ligne dont on ne connaît que l'énergie. Le cas d'un produit mal renseigné. */
        fun caloriesOnly(kcal: Double): Macros =
            Macros(kcal = kcal, protein = null, carbs = null, sugars = null, fat = null, fiber = null)
    }
}
