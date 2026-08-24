package app.hexavore.domain.nutrition

/**
 * Ce qu'on cherche à faire d'un compteur : l'atteindre, ou ne pas le dépasser.
 *
 * La distinction est portée par le domaine et non par l'interface, parce que c'est
 * une règle nutritionnelle et non un choix d'affichage. Laisser chaque écran la
 * décider produirait tôt ou tard une jauge de sucres qui se remplit comme une
 * récompense — exactement le contresens qu'on veut éviter.
 *
 * @see docs/03-nutrition-calculs.md
 */
enum class MacroGoalKind {
    /**
     * Un objectif à atteindre. Rester en dessous est un manque.
     *
     * Les calories, les protéines et les fibres. Sous-consommer des protéines en
     * déficit coûte de la masse maigre ; manquer de fibres se paie sur le transit
     * et la satiété. Ces trois-là méritent une jauge qui se remplit.
     */
    TARGET,

    /**
     * Une limite à ne pas dépasser. Rester en dessous est le résultat normal.
     *
     * Les glucides, les sucres et les lipides. Ce sont eux qui font déborder le
     * budget calorique quand ils débordent, et aucun d'eux n'a de plancher qu'on
     * cherche activement à atteindre — les lipides ont un plancher physiologique,
     * mais il est garanti par le calcul de l'objectif, pas par la saisie du jour.
     *
     * Une jauge de limite reste éteinte tant qu'on est en dessous : ne pas
     * l'allumer, c'est déjà réussir.
     */
    LIMIT,
}
