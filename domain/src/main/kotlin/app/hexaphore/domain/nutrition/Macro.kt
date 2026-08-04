package app.hexaphore.domain.nutrition

/**
 * Les six compteurs de l'application.
 *
 * Ce sont eux qui donnent son nom au projet, et ils sont le vocabulaire commun de
 * toutes les couches : le domaine les calcule, le design system leur associe une
 * teinte, l'interface les affiche. Les énumérer ici plutôt que dans chaque module
 * évite trois listes qui divergent.
 *
 * Chaque macro porte aussi **ce qu'on cherche à en faire** — l'atteindre ou ne pas
 * la dépasser. Sans cette information, une jauge de sucres se lit comme une jauge
 * de protéines, et remplir la première ressemble à une réussite alors que c'en est
 * le contraire.
 *
 * [CALORIES] n'est pas un macronutriment au sens strict. Il figure ici parce que
 * l'application le traite comme les cinq autres — un objectif, une jauge, une
 * couleur — et qu'une septième notion « compteur » à côté de « macro » n'aurait
 * servi qu'à doubler chaque signature.
 *
 * @see docs/03-nutrition-calculs.md
 * @see docs/08-design-system.md
 */
enum class Macro(val goal: MacroGoalKind) {
    /** Mesure principale. Les calories font foi, les macros sont des répartitions. */
    CALORIES(MacroGoalKind.TARGET),

    /** Le besoin le plus contraint : c'est lui qui préserve la masse maigre. */
    PROTEIN(MacroGoalKind.TARGET),

    /**
     * Le solde du budget énergétique, une fois les autres servis.
     *
     * Une limite et non une cible : personne n'a besoin d'« atteindre ses
     * glucides », alors que les dépasser fait déborder les calories.
     */
    CARBS(MacroGoalKind.LIMIT),

    /**
     * Sous-ensemble des glucides, et la limite la plus stricte des six.
     *
     * Plafond de l'OMS à 10 % des calories. Cette différence se traduit jusque
     * dans l'interface, où la jauge des sucres ne s'allume qu'au dépassement.
     */
    SUGARS(MacroGoalKind.LIMIT),

    /** Limite. Le plancher physiologique est garanti par le calcul de l'objectif. */
    FAT(MacroGoalKind.LIMIT),

    /** Objectif à atteindre : 14 g pour 1 000 kcal, plancher 25 g. */
    FIBER(MacroGoalKind.TARGET),
}
