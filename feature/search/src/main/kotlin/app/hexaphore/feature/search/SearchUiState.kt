package app.hexaphore.feature.search

import androidx.compose.runtime.Immutable
import app.hexaphore.domain.food.Food

/**
 * Ce que l'écran de recherche montre.
 *
 * Un seul type fermé plutôt qu'un état à drapeaux — `loading`, `error`, `empty` —
 * qui autoriserait des combinaisons qui n'existent pas : chargement **et** erreur,
 * résultats **et** liste vide. Chaque variante décrit un écran possible et un seul.
 */
@Immutable
internal sealed interface SearchUiState {
    /**
     * Avant toute frappe : ce qu'on mange souvent, et ce qu'on a épinglé.
     *
     * C'est l'état le plus vu de l'écran le plus utilisé de l'application, et il
     * évite la frappe elle-même dans le cas courant — on mange souvent la même chose.
     */
    data class Shortcuts(val recent: List<Food>, val favorites: List<Food>) : SearchUiState

    /**
     * La requête est partie, rien n'est encore revenu.
     *
     * Distinct de [Results] avec une liste vide : « je cherche » et « je n'ai rien
     * trouvé » ne demandent pas le même écran, et les confondre ferait clignoter
     * « aucun résultat » à chaque frappe.
     */
    data object Searching : SearchUiState

    data class Results(val query: String, val foods: List<Food>) : SearchUiState

    /**
     * Rien ne correspond.
     *
     * Porte la requête parce que c'est elle que propose le bouton de création :
     * « Créer *« pâtes de mamie »* », avec le nom déjà rempli.
     */
    data class Empty(val query: String) : SearchUiState

    /** La lecture a échoué. Sans détail : une base illisible appelle le même geste. */
    data object Error : SearchUiState
}
