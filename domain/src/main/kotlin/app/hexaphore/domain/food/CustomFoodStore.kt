package app.hexaphore.domain.food

/**
 * Créer, modifier et supprimer un aliment personnel.
 *
 * Ce port a été **reporté de la tranche 2** ([D40][decisions]), et la raison vaut
 * d'être relue : un aliment personnel n'a de sens que réutilisable, donc trouvable.
 * Sans recherche, il aurait été écrit dans une table que rien ne lit, derrière un
 * port à une seule implémentation — l'abstraction préventive que le projet refuse.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md
 */
interface CustomFoodStore {
    /** Écrit la fiche, qu'elle soit nouvelle ou déjà connue, et rend son identifiant. */
    suspend fun save(food: Food): FoodId

    /**
     * Retire la fiche du catalogue.
     *
     * **Les entrées de journal qui la citaient survivent**, avec leurs macros figées
     * et leur nom d'affichage ; c'est le lien de provenance qui se défait. Un journal
     * est un registre d'événements, et supprimer un aliment aujourd'hui ne peut pas
     * effacer ce qu'on a mangé il y a six mois ([D05][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    suspend fun delete(id: FoodId)

    /**
     * Combien d'entrées de journal citent cette fiche.
     *
     * Sert à prévenir avant de supprimer. [docs/04][sources] demande une
     * confirmation quand l'aliment a servi, et la question qu'on pose alors — « il
     * est utilisé dans 12 entrées » — est celle à laquelle ce compte répond.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    suspend fun usageCount(id: FoodId): Int
}
