package app.hexaphore.feature.home

import androidx.compose.runtime.Immutable
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.EntryId

/**
 * Ce que l'accueil peut déclencher.
 *
 * Regroupées plutôt que passées une par une : la liste des plats est trois niveaux
 * plus bas que l'écran, et six lambdas transmises de main en main feraient de
 * chaque signature intermédiaire une liste de paramètres à rallonge.
 */
@Immutable
data class HomeActions(
    val onAddDish: () -> Unit,
    val onEditDish: (DishId) -> Unit,
    /**
     * Supprime le plat entier, ses *n* lignes avec lui.
     *
     * Atteint par l'appui long, et **confirmé par un dialogue** : le balayage retire
     * une ligne et se rattrape à la barre, celui-ci en retire plusieurs d'un coup et
     * mérite d'être voulu ([D61][decisions]). La barre reste offerte ensuite — la
     * confirmation évite l'accident, la barre rattrape le regret.
     *
     * [decisions]: docs/11-decisions.md
     */
    val onDeleteDish: (Dish) -> Unit,
    val onDeleteEntry: (Dish, EntryId) -> Unit,
    val onUndo: () -> Unit,
    val onUndoExpired: () -> Unit,
    val onRetry: () -> Unit,
    /** Vers les cinq questions, tant qu'aucun objectif n'a été posé. */
    val onSetUpGoal: () -> Unit,
    /**
     * Vers « Profil et objectifs ».
     *
     * Directement, sans écran de réglages au-dessus : les quatre autres sections que
     * [docs/02][parcours] prévoit dépendent de tranches à venir, et un écran de transit
     * qui ne désigne qu'une destination est un écran de trop ([D59][decisions]).
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     * [decisions]: docs/11-decisions.md
     */
    val onOpenProfile: () -> Unit,
)
