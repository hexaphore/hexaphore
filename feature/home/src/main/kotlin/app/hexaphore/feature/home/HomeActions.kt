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
    val onDeleteEntry: (Dish, EntryId) -> Unit,
    val onUndo: () -> Unit,
    val onUndoExpired: () -> Unit,
    val onRetry: () -> Unit,
    /** Vers les cinq questions, tant qu'aucun objectif n'a été posé. */
    val onSetUpGoal: () -> Unit,
)
