package app.hexavore.feature.search

import androidx.compose.runtime.Immutable
import app.hexavore.domain.nutrition.Macro

/**
 * Ce que le formulaire d'aliment personnel peut déclencher.
 *
 * Un objet plutôt que sept paramètres, comme sur les autres écrans du projet : des
 * lambdas transmises de main en main sur trois niveaux rendent chaque signature
 * illisible et chaque ajout coûteux.
 *
 * [Immutable] parce qu'aucun de ces champs ne change après construction : Compose
 * peut alors sauter la recomposition des sous-arbres qui ne reçoivent que ça.
 */
@Immutable
internal data class CustomFoodActions(
    val onNameChange: (String) -> Unit,
    val onBrandChange: (String) -> Unit,
    val onServingChange: (String) -> Unit,
    val onMacroChange: (Macro, String) -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    /** Accepte la proposition de reverser la fiche a Open Food Facts. */
    val onContribute: () -> Unit,
    /** Refuse, ou ferme apres coup : la fiche reste enregistree dans les deux cas. */
    val onDeclineContribution: () -> Unit,
    val onClose: () -> Unit,
)
