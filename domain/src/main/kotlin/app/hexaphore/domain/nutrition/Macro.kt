package app.hexaphore.domain.nutrition

/**
 * Les six compteurs de l'application.
 *
 * Ce sont eux qui donnent son nom au projet, et ils sont le vocabulaire commun de
 * toutes les couches : le domaine les calcule, le design system leur associe une
 * teinte, l'interface les affiche. Les énumérer ici plutôt que dans chaque module
 * évite trois listes qui divergent.
 *
 * [CALORIES] n'est pas un macronutriment au sens strict. Il figure ici parce que
 * l'application le traite comme les cinq autres — un objectif, une jauge, une
 * couleur — et qu'une septième notion « compteur » à côté de « macro » n'aurait
 * servi qu'à doubler chaque signature.
 *
 * @see docs/08-design-system.md
 */
enum class Macro {
    /** Mesure principale. Les calories font foi, les macros sont des répartitions. */
    CALORIES,

    PROTEIN,

    CARBS,

    /**
     * Sous-ensemble des glucides, et **plafond** plutôt que cible.
     *
     * Cette différence n'est pas cosmétique : atteindre ses protéines est un
     * objectif, atteindre son plafond de sucres n'en est pas un. Elle se traduit
     * jusque dans l'interface, où la jauge des sucres ne s'allume qu'au
     * dépassement.
     *
     * @see docs/03-nutrition-calculs.md
     */
    SUGARS,

    FAT,

    FIBER,
}
