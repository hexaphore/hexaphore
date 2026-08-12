package app.hexaphore.domain.food

/**
 * La source **distante** de fiches produit, interrogée par code-barres.
 *
 * Elle n'est pas `BarcodeLookup`, et la distinction porte tout le parcours du scan :
 * le catalogue local répond en quelques millisecondes ou ne répond pas, ce port-là
 * demande le réseau. Les deux se lisent dans cet ordre, et c'est ce qui rend le
 * deuxième scan d'un produit instantané et disponible en mode avion.
 *
 * **Une fonction, pas un objet à quinze méthodes** : l'écran de scan ne dépend que
 * de ça, donc son test n'a besoin que de ça ([docs/06][architecture] § I).
 *
 * **Aucune exception ne franchit cette frontière.** Une panne réseau est une réponse
 * possible du port, pas un accident : elle a son cas dans [ProductLookup], et une
 * implémentation qui lèverait une `IOException` casserait tous les appelants
 * ([docs/06][architecture] § L).
 *
 * [architecture]: docs/06-architecture.md
 * @see docs/04-sources-de-donnees.md
 */
fun interface ProductSource {
    suspend fun byBarcode(code: Barcode): ProductLookup
}

/**
 * Ce que la source distante peut répondre.
 *
 * Trois cas et pas deux, parce que [docs/02][parcours] en attend trois écrans
 * différents. « Le produit n'existe pas là-bas » invite à le créer ; « je n'ai pas
 * pu demander » invite à le créer **aussi**, mais dit que la question reste posée et
 * que le code est mémorisé pour un prochain passage connecté. Les confondre ferait
 * annoncer une absence qu'on n'a pas vérifiée.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
sealed interface ProductLookup {
    /**
     * La fiche, à l'un de ses deux âges.
     *
     * **Rendue par ce port**, elle porte un identifiant provisoire — comme un résultat
     * de la table de l'ANSES avant son versement au catalogue ([D51][decisions]).
     * **Rendue par `LookupBarcode`**, elle est celle du catalogue, avec son identifiant
     * définitif et ses compteurs. Le port propose, le cas d'usage verse.
     *
     * Un seul type pour les deux, parce que l'écran ne voit que le second et n'a aucune
     * raison de connaître le premier.
     *
     * [decisions]: docs/11-decisions.md
     */
    data class Found(val food: Food) : ProductLookup

    /**
     * Open Food Facts a répondu, et ne connaît pas ce code.
     *
     * **Une fiche sans nom compte comme inconnue.** Le nom est le seul champ
     * bloquant ([docs/04][sources]) : une fiche qu'on ne peut ni afficher ni
     * retrouver n'est pas une fiche, et l'annoncer comme trouvée n'offrirait à
     * l'utilisateur qu'un écran vide à la place du formulaire de création.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    data object Unknown : ProductLookup

    /** La question n'a pas pu être posée : hors ligne, ou le service refuse. */
    data object Unreachable : ProductLookup
}
