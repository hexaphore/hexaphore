package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.DraftLine
import app.hexaphore.domain.diary.EntryDraft
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.FavoriteDishId
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodLookup

/**
 * Ce que l'écran de validation ouvre : un plat, un favori, une fiche, ou rien.
 *
 * **Les quatre entrées de l'écran, en un seul objet.** C'est le point de convergence
 * que [docs/12][plan] désigne depuis la conception, et il n'existait jusqu'ici que
 * comme une suite de branches dans le `ViewModel` : chaque nouveau mode de saisie en
 * ajoutait une, et la tranche 6 en apporte encore deux. Ici, il y a **un** endroit qui
 * sait d'où peut venir un brouillon.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
sealed interface DraftOrigin {
    /** Un plat déjà enregistré, qu'on rouvre pour le corriger. */
    data class Dish(val id: DishId) : DraftOrigin

    /** Un plat favori, qu'on rejoue. */
    data class Favorite(val id: FavoriteDishId) : DraftOrigin

    /** Une saisie neuve, éventuellement préremplie d'une fiche choisie ailleurs. */
    data class New(val food: FoodId? = null) : DraftOrigin
}

/**
 * Ouvre le brouillon que désigne une origine.
 *
 * `null` quand ce qui est désigné n'existe plus — plat supprimé pendant qu'on
 * l'ouvrait, favori retiré entre le choix et l'ouverture. Le cas n'est pas théorique :
 * la suppression est immédiate depuis l'accueil.
 *
 * **Une saisie neuve, elle, ne rend jamais `null`.** Une fiche introuvable donne un
 * brouillon vierge plutôt qu'un écran d'erreur : il reste plus utile de saisir à la
 * main que de repartir de zéro, et la ligne vide dit déjà qu'il n'y a rien.
 */
class OpenDraft(
    private val dishes: GetDishDraft,
    private val favorites: GetFavoriteDraft,
    private val create: CreateDraft,
    private val foods: FoodLookup,
) {
    suspend operator fun invoke(origin: DraftOrigin): EntryDraft? = when (origin) {
        is DraftOrigin.Dish -> dishes(origin.id)
        is DraftOrigin.Favorite -> favorites(origin.id)
        is DraftOrigin.New -> newDraft(origin.food)
    }

    /**
     * La source est la même dans les deux cas : chercher un aliment ou taper ses
     * valeurs, c'est composer son plat soi-même. Ce qui mérite une pastille à part est
     * ce qu'un modèle a **proposé**, et ça n'existe pas encore.
     */
    private suspend fun newDraft(food: FoodId?): EntryDraft = food
        ?.let { id -> runCatching { foods.byId(id) }.getOrNull() }
        ?.let { fiche -> create(EntrySource.MANUAL, fiche) }
        ?: create(EntrySource.MANUAL)
}

/**
 * Ajoute au plat en cours une ligne bâtie sur une fiche choisie dans la recherche.
 *
 * **Elle ne démarre pas un nouveau brouillon.** « Ajouter un aliment » rouvre la même
 * recherche que le bouton de l'accueil, et c'est ce qui rend les deux gestes identiques
 * à apprendre.
 *
 * `null` quand la fiche a disparu entre le choix et le retour : la ligne n'est alors
 * pas ajoutée, plutôt que d'entrer vide dans un plat qu'on croyait complété.
 */
class AddFoodLine(private val foods: FoodLookup, private val create: CreateDraft) {
    suspend operator fun invoke(id: FoodId): DraftLine? =
        runCatching { foods.byId(id) }.getOrNull()?.let { create.line(it) }
}
