package app.hexavore.domain.food

import kotlinx.coroutines.flow.Flow

/**
 * Chercher un aliment par son nom.
 *
 * Un port par capacité, plutôt qu'un `FoodRepository` fourre-tout : l'écran de
 * recherche ne dépend pas de la création ni de la suppression, et son test n'a donc
 * pas besoin d'un faux objet à quinze méthodes ([docs/06][archi]).
 *
 * L'implémentation fusionne les provenances et les ordonne — ce qui suppose de lire
 * deux bases, la table de l'ANSES et le catalogue local, et c'est pour cela que le
 * classement lui appartient. Ce que le contrat garantit à l'appelant :
 *
 * - les aliments que l'utilisateur mange vraiment passent devant ([04][sources]) ;
 * - accents et casse sont sans effet, des deux côtés de la comparaison ;
 * - la liste est bornée par [limit], et l'appelant n'a rien à tronquer.
 *
 * **Le seuil de deux caractères et l'anti-rebond n'appartiennent pas ici.** Ce sont
 * des règles d'ergonomie de saisie ([D23][decisions]), et un port qui les porterait
 * les imposerait à un appelant qui n'a pas de clavier — le résolveur de la tranche 6,
 * qui interroge cette même recherche avec un mot déjà complet.
 *
 * [archi]: docs/06-architecture.md
 * [sources]: docs/04-sources-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
interface FoodSearch {
    /**
     * Les aliments dont le nom correspond à [query] et que [filter] retient, **et ce
     * qu'ils deviennent**.
     *
     * Un flux et non une lecture unique, parce que le catalogue change sous les
     * yeux de celui qui regarde ses résultats : épingler un aliment, supprimer une
     * fiche personnelle, verser au catalogue l'aliment qu'on vient de choisir. Une
     * `suspend fun` rend un instantané, et un instantané ne peut pas se démentir —
     * il fallait relancer la recherche pour voir l'étoile changer, alors que les
     * raccourcis, eux, se rafraîchissaient. C'est cette asymétrie qui a fait le
     * défaut ([D53][decisions]).
     *
     * L'implémentation décide quand ré-émettre. Room le sait : il invalide sur
     * écriture, exactement comme pour [RecentFoods.observeRecent].
     *
     * **Une requête vide et un filtre suffisent** : c'est le mode parcours, où l'on
     * demande « les fruits » sans rien taper. Vides tous les deux, en revanche, ne
     * rendent rien — le catalogue entier n'est pas une réponse.
     *
     * Rend une liste vide plutôt qu'une erreur quand rien ne correspond : ne rien
     * trouver est une réponse.
     */
    fun search(query: String, filter: FoodFilter = FoodFilter.NONE, limit: Int): Flow<List<Food>>
}
