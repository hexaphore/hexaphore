package app.hexaphore.domain.food

/**
 * Le catalogue **local**, interrogé par code-barres.
 *
 * C'est ce port qui rend le deuxième scan d'un produit instantané et disponible en
 * mode avion : il répond en quelques millisecondes ou ne répond pas, là où
 * [ProductSource] demande le réseau. Les deux se lisent dans cet ordre, et
 * `LookupBarcode` est ce qui tient l'ordre.
 *
 * **Il ne rend jamais un aliment de la table de l'ANSES.** `source_ref` y porte un
 * code CIQUAL, qui n'est pas un code-barres et ne désigne pas le même monde. Les
 * confondre ferait sortir « Pomme, chair et peau, crue » sur un scan de code 13039.
 *
 * **Un aliment personnel passe devant un produit en cache**, quand les deux portent le
 * même code : ce que l'utilisateur a pris la peine de saisir est ce qu'il mange
 * vraiment ([docs/04][sources]), et c'est la même règle qui classe les résultats de
 * recherche.
 *
 * [sources]: docs/04-sources-de-donnees.md
 * @see docs/06-architecture.md
 */
fun interface BarcodeLookup {
    suspend fun byBarcode(code: Barcode): Food?
}
