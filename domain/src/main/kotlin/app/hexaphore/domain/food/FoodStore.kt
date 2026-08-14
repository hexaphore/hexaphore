package app.hexaphore.domain.food

/**
 * Le côté **écriture** du catalogue d'aliments.
 *
 * Il s'appelait `CustomFoodStore` tant qu'il ne servait qu'au formulaire d'aliment
 * personnel ([D40][decisions]). Il en fait davantage depuis que [place] existe, et
 * un nom qui ment est celui par lequel on finit par écrire un défaut : garder
 * « custom » aurait laissé croire qu'y verser un aliment de la table de l'ANSES
 * était un détournement, alors que c'est le fonctionnement normal du catalogue.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md
 */
interface FoodStore {
    /**
     * Verse la fiche au catalogue si elle n'y est pas, et rend **celle qui y est**.
     *
     * C'est le geste qui donne à un aliment de la table de l'ANSES une existence
     * durable. Avant lui, un résultat de recherche n'a qu'un identifiant provisoire :
     * copier les 3 484 lignes à l'installation gonflerait la base, les sauvegardes
     * et la recherche avec 99 % de contenu jamais utilisé ([docs/07][modele]).
     *
     * **Elle rend la fiche stockée et non celle qu'on lui passe**, et c'est tout
     * l'intérêt : l'appelant récupère l'identifiant définitif, ses compteurs d'usage
     * et les corrections qu'elle a pu recevoir. Une fiche déjà connue n'est jamais
     * réécrite — sans quoi choisir un aliment dans une liste remettrait ses
     * compteurs à zéro, et un brouillon ouvert depuis dix minutes défairait une
     * correction faite entre-temps.
     *
     * [modele]: docs/07-modele-de-donnees.md
     */
    suspend fun place(food: Food): Food

    /** Écrit la fiche, qu'elle soit nouvelle ou déjà connue, **en écrasant** ses valeurs. */
    suspend fun save(food: Food): FoodId

    /**
     * Retire la fiche du catalogue.
     *
     * **Les entrées de journal qui la citaient survivent**, avec leurs macros figées
     * et leur nom d'affichage ; c'est le lien de provenance qui se défait. Un journal
     * est un registre d'événements, et supprimer un aliment aujourd'hui ne peut pas
     * effacer ce qu'on a mangé il y a six mois ([D05][decisions]).
     *
     * Combien d'entrées seront ainsi détachées se demande à [FoodCitations], qui est
     * un port distinct : ce catalogue ne connaît pas le journal, et un port qui
     * prétend le contraire ne peut être doublé qu'en trichant ([D71][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    suspend fun delete(id: FoodId)
}
