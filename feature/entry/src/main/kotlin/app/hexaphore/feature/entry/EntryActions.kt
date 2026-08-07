package app.hexaphore.feature.entry

import androidx.compose.runtime.Immutable
import app.hexaphore.domain.diary.DraftLineId

/**
 * Ce que l'écran de validation peut déclencher.
 *
 * Un objet plutôt que six paramètres : une composable sans état expose ses
 * événements en paramètres, mais des lambdas transmises de main en main sur trois
 * niveaux rendent chaque signature illisible et chaque ajout coûteux. Regroupées,
 * elles se transmettent d'un mot et ne changent plus la forme des fonctions qui les
 * traversent.
 *
 * [Immutable] parce qu'aucun de ces champs ne change après construction : Compose
 * peut alors sauter la recomposition des sous-arbres qui ne reçoivent que ça.
 */
@Immutable
internal data class EntryActions(
    val onLineEdit: (DraftLineId, LineEdit) -> Unit,
    val onAddLine: () -> Unit,
    val onRemoveLine: (DraftLineId) -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    val onClose: () -> Unit,
)
