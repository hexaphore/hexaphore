package app.hexaphore.feature.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.hexaphore.domain.diary.DraftLineId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ce qui se passe quand on appuie sur « Enregistrer » et qu'un champ manque.
 *
 * **L'écran désigne le champ et y va.** Le message précédent — « chaque ligne demande
 * un aliment, une quantité et des calories » — était juste et inutilisable : devant
 * vingt-quatre champs dont un seul bloque, il laissait chercher. Trois gestes le
 * remplacent : marquer la ligne, y faire défiler, et nommer ce qui manque.
 *
 * La marque **reste** jusqu'au prochain appui : l'effacer à la première frappe la
 * ferait disparaître au moment précis où l'on s'en sert.
 */
@Composable
internal fun rememberMissingFieldGuide(
    lines: List<EntryFormLine>,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onFlag: (DraftLineId?) -> Unit,
): () -> Unit {
    val labels = MissingField.entries.associateWith { stringResource(it.labelRes) }

    return remember(lines, listState, snackbarHostState, scope, labels) {
        {
            val index = lines.indexOfFirst { it.missing != null }
            val line = lines.getOrNull(index)
            onFlag(line?.id)

            scope.launch {
                if (index >= 0) listState.animateScrollToItem(index + HEADER_ITEMS)

                // Une seule barre a la fois : trois appuis empilaient trois fois le
                // meme message, et le troisieme s'affichait dix secondes plus tard.
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(missingMessage(line, labels))
            }
        }
    }
}

/**
 * Le message qui **nomme** ce qui manque, et où.
 *
 * « Un champ est vide » n'aide personne devant vingt-quatre champs : il faut dire
 * lequel, et de quel aliment. Le nom de la ligne y figure quand elle en a un — c'est
 * souvent le nom qui manque, justement, et la phrase s'adapte.
 */
private fun missingMessage(line: EntryFormLine?, labels: Map<MissingField, String>): String {
    val missing = line?.missing ?: return labels.getValue(MissingField.NAME)
    val label = labels.getValue(missing)
    return if (line.name.isBlank()) label else "$label — ${line.name.trim()}"
}

private val MissingField.labelRes: Int
    @StringRes get() = when (this) {
        MissingField.NAME -> R.string.entry_missing_name
        MissingField.QUANTITY -> R.string.entry_missing_quantity
        MissingField.CALORIES -> R.string.entry_missing_calories
    }

/** Ce qui précède les lignes dans la liste : l'en-tête, et rien d'autre. */
private const val HEADER_ITEMS = 1
