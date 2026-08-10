package app.hexaphore.feature.search

import androidx.compose.runtime.Immutable
import app.hexaphore.domain.food.Food

/**
 * Ce que l'écran de recherche peut déclencher.
 *
 * Un objet plutôt que six paramètres, comme sur les autres écrans du projet : la
 * ligne d'un résultat est deux niveaux plus bas que l'écran, et des lambdas
 * transmises de main en main rendraient chaque signature intermédiaire illisible.
 */
@Immutable
internal data class SearchActions(
    val onQueryChange: (String) -> Unit,
    /**
     * Rend la fiche entière et non son identifiant : elle n'est peut-être pas encore
     * au catalogue, et c'est le `ViewModel` qui l'y verse avant de la laisser partir.
     */
    val onPick: (Food) -> Unit,
    val onToggleFavorite: (Food) -> Unit,
    val onDelete: (Food) -> Unit,
    val onManualEntry: (String) -> Unit,
    val onClose: () -> Unit,
)
