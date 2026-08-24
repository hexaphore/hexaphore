package app.hexavore.feature.search

import androidx.compose.runtime.Immutable
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodCategory
import app.hexavore.domain.food.FoodTrait

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
    val onToggleCategory: (FoodCategory) -> Unit,
    val onToggleTrait: (FoodTrait) -> Unit,
    val onManualEntry: (String) -> Unit,
    /**
     * Va chercher ce nom chez Open Food Facts.
     *
     * Sur un tap, jamais à la frappe : la recherche locale coûte une lecture SQLite,
     * celle-ci un aller-retour réseau.
     */
    val onSearchRemotely: () -> Unit,
    val onClose: () -> Unit,
)
