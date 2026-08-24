package app.hexavore.domain.food

/**
 * Le rayon d'un aliment, tel que le bandeau de la recherche le propose.
 *
 * **Huit, et pas quarante-cinq.** La nomenclature de l'ANSES compte 45 sous-groupes —
 * « légumes » en a 314, « fruits » 191, « boissons sans alcool » 187 — et c'est une
 * classification de laboratoire, pas un bandeau qu'on parcourt au pouce. La
 * correspondance est donc une **table maison, versionnée** dans
 * `:tooling:ciqual-import`, appliquée à l'import.
 *
 * **Elle vit dans `:domain` et non dans l'adaptateur.** Un tag qui ne serait qu'une
 * clause `WHERE` dans une requête SQL ne s'éprouverait que sur un appareil : le
 * filtre est une règle de ce que l'utilisateur voit, et une règle se teste sur la
 * JVM. Le SQL n'en est que l'accélération — ce que le contrat de [FoodSearch]
 * garantit des deux côtés.
 *
 * **Toutes les fiches n'en ont pas.** Les matières grasses, les aides culinaires, les
 * plats composés et les aliments infantiles n'entrent dans aucune de ces huit cases,
 * et leur en forcer une serait mentir sur ce qu'on trouve en tapant dessus. Un
 * aliment personnel n'en a pas non plus ([D54][decisions]) : il ne répond qu'à
 * « Mon aliment ». C'est pourquoi le champ est nullable partout.
 *
 * [decisions]: docs/11-decisions.md
 */
enum class FoodCategory {
    FRUITS,
    LEGUMES,
    FECULENTS,
    VIANDES_POISSONS,
    PRODUITS_LAITIERS,
    BOISSONS,
    DESSERTS,
    SNACKS,
}
