package app.hexaphore.domain.food

/**
 * Combien d'entrées de journal citent une fiche.
 *
 * Sert à prévenir avant de supprimer : la question qu'on pose alors — « elle est
 * utilisée dans 12 entrées » — est celle à laquelle ce compte répond, et c'est elle
 * qui fait la différence entre « êtes-vous sûr » et une vraie information.
 *
 * **C'est un port à part, et il l'est pour une raison de test.** Il vivait dans
 * [FoodStore], donc dans un catalogue qui ne connaît pas le journal : le faux ne
 * pouvait qu'exposer une carte posée à la main pendant que Room comptait de vraies
 * lignes. Le faux était plus indulgent que le vrai — la forme exacte que
 * [D53][decisions] proscrit, et par laquelle quatre défauts sont passés — et rien ne
 * pouvait éprouver la seule propriété qui compte ici : **noter un plat fait monter
 * le compte**. Séparé, le port s'adosse au journal des deux côtés, et cette
 * propriété devient une ligne du contrat ([D71][decisions]).
 *
 * Le compte porte sur les **entrées**, pas sur les plats : deux lignes du même repas
 * qui citent la même fiche comptent deux, parce que c'est ce que la phrase affichée
 * annonce.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md
 */
fun interface FoodCitations {
    /** Combien d'entrées de journal citent [id]. Zéro si la fiche n'a jamais servi. */
    suspend fun count(id: FoodId): Int
}
