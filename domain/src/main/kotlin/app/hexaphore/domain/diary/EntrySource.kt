package app.hexaphore.domain.diary

/**
 * Par quel chemin un plat est entré dans le journal.
 *
 * **Un plat, une source.** Elle appartient au plat et non aux aliments qu'il
 * contient : on ne photographie pas une assiette aliment par aliment, on la
 * photographie une fois. Attribuer une source à chaque ligne obligerait à répondre
 * à une question qui ne se pose jamais.
 *
 * **Elle ne change plus.** Un plat reste éditable à la main indéfiniment, mais son
 * origine reste ce qu'elle était : c'est un fait historique, pas un état. Sans
 * cela, corriger une quantité sur une proposition de l'IA la ferait passer pour une
 * saisie manuelle, et on perdrait la seule trace de ce qui a été deviné.
 *
 * @see docs/11-decisions.md — D32
 */
enum class EntrySource {
    /**
     * Composé par l'utilisateur, qu'il ait cherché ses aliments ou tapé leurs
     * valeurs.
     *
     * **Il n'y a plus de source « recherche ».** Elle se confondait avec celle-ci :
     * un même plat mêle couramment un aliment trouvé dans la table et un autre saisi
     * à la main, et un plat porte **une** source. Distinguer les deux revenait à
     * choisir laquelle mentir ([D52][decisions]).
     *
     * Ce que la distinction voulait dire reste vrai et reste utile ailleurs : ce qui
     * compte est de savoir ce qui a été **proposé** par un modèle, et c'est
     * [proposed] qui le dit.
     *
     * Une base écrite par une version antérieure porte encore `SEARCH` ; elle se
     * relit ici, la lecture retombant sur cette valeur.
     *
     * [decisions]: docs/11-decisions.md
     */
    MANUAL,

    /** Scanné, puis complété depuis Open Food Facts. */
    BARCODE,

    /** Proposé par un modèle à partir d'une photo. */
    PHOTO_AI,

    /** Proposé par un modèle à partir d'une description écrite. */
    TEXT_AI,

    /** Rejoué depuis un plat enregistré comme favori. */
    FAVORITE,

    ;

    /**
     * `true` quand le contenu du plat a été **proposé** plutôt que choisi.
     *
     * L'interface le signale par la forme — contour en pointillés — parce qu'une
     * proposition de modèle mérite un regard que ne mérite pas un code-barres.
     */
    val proposed: Boolean get() = this == PHOTO_AI || this == TEXT_AI
}
