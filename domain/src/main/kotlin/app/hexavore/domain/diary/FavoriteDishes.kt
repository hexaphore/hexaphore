package app.hexavore.domain.diary

import kotlinx.coroutines.flow.Flow

/**
 * Les plats favoris.
 *
 * Port séparé de [DiaryRepository], et non une méthode de plus dessus : un favori
 * n'est pas une entrée de journal. Le journal est un **registre d'événements** — ce
 * qui y est écrit est écrit, et ses lignes figent leurs valeurs ([D05][decisions]) —
 * là où un favori est un **modèle réutilisable**, qui suit les fiches vivantes qu'il
 * cite. Les mêler dans une interface aurait mis les deux régimes sous le même nom.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/07-modele-de-donnees.md
 */
interface FavoriteDishes {
    /**
     * Tous les favoris, les plus utilisés d'abord.
     *
     * Pas de recherche côté port : la liste tient sur un écran et se filtre là où on
     * la lit. Une requête de recherche ici demanderait un index et une normalisation
     * pour trier vingt lignes.
     */
    fun observeAll(): Flow<List<FavoriteDish>>

    /** Un favori et ses composants, ou `null` s'il a été supprimé entre-temps. */
    suspend fun byId(id: FavoriteDishId): FavoriteDish?

    /**
     * Un favori porte-t-il déjà ce nom ?
     *
     * La comparaison ignore la casse et les accents, comme la recherche d'aliments :
     * « Petit-déj » et « petit dej » sont le même nom pour qui parcourt une liste.
     *
     * @param excluding le favori qu'on est en train de renommer, qui ne doit pas
     *   entrer en collision avec lui-même.
     */
    suspend fun nameTaken(name: String, excluding: FavoriteDishId? = null): Boolean

    /**
     * Écrit un favori et **remplace** entièrement ses composants.
     *
     * Même verbe pour créer et pour renommer, et pour la même raison que
     * [DiaryRepository.save] : les deux demandent que le favori soit, après l'appel,
     * ce que dit l'argument.
     */
    suspend fun save(favorite: FavoriteDish)

    /**
     * Retire un favori de la liste.
     *
     * Les plats du journal qui en venaient **restent**, et perdent seulement leur
     * lien : un favori supprimé ne doit pas amputer l'historique, exactement comme
     * supprimer un aliment personnel défait la provenance de ses entrées sans les
     * effacer.
     */
    suspend fun delete(id: FavoriteDishId)

    /** Note un rejeu, pour que la liste remonte ce qui sert. */
    suspend fun markUsed(id: FavoriteDishId)
}
