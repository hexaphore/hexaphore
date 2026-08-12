package app.hexaphore.domain.food

/**
 * La recherche **par nom** dans la source distante.
 *
 * Distincte de [ProductSource], qui cherche par code-barres, et pas seulement parce
 * que l'argument diffère : celle-ci ne part **jamais toute seule**. Un code-barres
 * arrive d'un scan, donc d'un geste ; un nom arrive d'un clavier, et interroger le
 * réseau à chaque frappe serait huit requêtes pour taper « chocolat ». Elle est
 * offerte en dernière ligne de résultats et attend qu'on la touche ([docs/02][parcours]).
 *
 * **Aucune exception ne franchit cette frontière**, comme pour [ProductSource] : une
 * panne réseau est une réponse du port.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * @see docs/04-sources-de-donnees.md
 */
fun interface ProductSearch {
    suspend fun byName(query: String, limit: Int): ProductResults
}

/**
 * Ce que la recherche distante peut répondre.
 *
 * Deux cas et non trois : une liste vide **est** une réponse, et elle veut dire « ce
 * service ne connaît rien de ce nom ». Lui donner un cas à part ferait dire à
 * l'écran deux fois la même chose.
 */
sealed interface ProductResults {
    /**
     * Les fiches trouvées, avec leur identifiant provisoire et leur date de
     * récupération — aucune n'est encore au catalogue.
     *
     * **Un produit dont le code n'est pas lisible n'y figure pas.** Sans code
     * canonique, la fiche ne peut ni être mise en cache sans doublon, ni être
     * retrouvée par un scan : elle serait une ligne qu'on ne peut choisir qu'une
     * fois, et qui reviendrait du réseau à chaque recherche.
     */
    data class Found(val products: List<Food>) : ProductResults

    /** La question n'a pas pu être posée : hors ligne, ou le service refuse. */
    data object Unreachable : ProductResults
}
