package app.hexaphore.domain.food

import kotlinx.coroutines.flow.Flow

/**
 * Ce que l'écran de recherche montre **avant** toute frappe.
 *
 * C'est l'écran le plus utilisé de l'application, et la liste des derniers aliments
 * y évite la frappe elle-même dans le cas courant : on mange souvent la même chose.
 *
 * Un [Flow] parce que la liste change sans que cet écran ait rien demandé — un plat
 * enregistré ailleurs la réordonne.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
interface RecentFoods {
    /**
     * Les [limit] derniers aliments utilisés, du plus récent au plus ancien.
     *
     * Un aliment créé et jamais servi n'y figure pas. L'y mettre à sa date de
     * création mélangerait deux informations différentes : ce qu'on a saisi une fois
     * et ce qu'on mange.
     */
    fun observeRecent(limit: Int): Flow<List<Food>>
}
