package app.hexaphore.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.hexaphore.core.designsystem.component.SourceBadge
import app.hexaphore.core.designsystem.component.SwipeToDelete
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.diary.DishSummary
import app.hexaphore.domain.diary.FoodEntry
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.MacroTotal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Les plats de la journée, du plus ancien au plus récent.
 *
 * Pas de repas nommés : un plat est une saisie, et l'heure suffit à le situer.
 */
@Composable
internal fun DishList(dishes: List<DishSummary>, zone: ZoneId, actions: HomeActions) {
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        dishes.forEach { dish -> DishBlock(dish, zone, timeFormatter, actions) }
    }
}

/**
 * Un plat, du badge de source à ses apports.
 *
 * **Le plat entier est la cible tactile.** Il l'est parce qu'il est l'unité de
 * saisie ([D31][decisions]) : ses lignes ont été entrées ensemble et se corrigent
 * ensemble. Restreindre le tap aux seules lignes d'aliment laissait l'heure, la
 * pastille, le total et les apports inertes — c'est-à-dire la moitié de la surface
 * du plat, sans que rien ne dise pourquoi elle ne répond pas ([D48][decisions]).
 *
 * Le balayage de suppression continue de fonctionner : il est capté plus bas dans
 * l'arbre, et un tap n'est pas un glissement.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DishBlock(summary: DishSummary, zone: ZoneId, timeFormatter: DateTimeFormatter, actions: HomeActions) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Aucun rognage : les coins arrondis coupaient la pastille de source et
            // le total de calories, qui sont aux deux extremites de la premiere
            // ligne. L'ondulation deborde donc en rectangle, ce qui est le prix a
            // payer pour que rien ne soit tronque.
            .combinedClickable(
                onClickLabel = stringResource(R.string.home_dish_edit),
                onLongClickLabel = stringResource(R.string.home_dish_menu),
                onLongClick = { menuOpen = true },
                onClick = { actions.onEditDish(summary.dish.id) },
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        DishMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onEdit = { actions.onEditDish(summary.dish.id) },
            onDelete = { confirming = true },
        )
        if (confirming) {
            DeleteDishConfirmation(
                lines = summary.entries.size,
                onConfirm = {
                    confirming = false
                    actions.onDeleteDish(summary.dish)
                },
                onDismiss = { confirming = false },
            )
        }
        DishHeader(summary, zone, timeFormatter)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        summary.entries.forEach { entry ->
            SwipeToDelete(
                label = stringResource(R.string.home_entry_delete),
                onDelete = { actions.onDeleteEntry(summary.dish, entry.id) },
            ) {
                EntryRow(entry = entry)
            }
        }
        DishMacros(summary)
    }
}

/** La pastille de source, l'heure, et le total du plat. */
@Composable
private fun DishHeader(summary: DishSummary, zone: ZoneId, timeFormatter: DateTimeFormatter) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceBadge(source = summary.dish.source)
        Text(
            text = timeFormatter.format(summary.dish.loggedAt.atZone(zone)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.home_dish_kcal, summary.totals[Macro.CALORIES].value.roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            color = NeonTheme.macros[Macro.CALORIES].base,
        )
    }
}

/**
 * Le menu de l'appui long : modifier, supprimer.
 *
 * **Il double le tap plutôt que de le remplacer.** Le tap ouvre la modification, qui
 * reste le geste courant ; l'appui long donne accès à ce qui n'a pas sa place sur la
 * surface du plat. « Modifier » y figure quand même — un menu dont la moitié des
 * entrées manque oblige à se souvenir de quel geste sert à quoi.
 */
@Composable
private fun DishMenu(expanded: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_dish_menu_edit)) },
            onClick = {
                onDismiss()
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_dish_menu_delete)) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

/**
 * La confirmation avant de supprimer un plat entier.
 *
 * Un dialogue, là où le balayage d'une ligne se contente de sa barre d'annulation :
 * celui-ci emporte *n* lignes d'un coup, et le nombre est dit. La barre reste offerte
 * ensuite — la confirmation évite l'accident, la barre rattrape le regret.
 */
@Composable
private fun DeleteDishConfirmation(lines: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_dish_delete_title)) },
        text = { Text(pluralStringResource(R.plurals.home_dish_delete_body, lines, lines)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_dish_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dish_delete_cancel)) }
        },
    )
}

private val DishSummary.entries: List<FoodEntry> get() = dish.entries

/**
 * Ce que le plat a apporté, au-delà des calories.
 *
 * Sans cette ligne, un plat ne se lit que par son énergie — or la question qu'on se
 * pose en relisant sa journée est rarement « combien de calories », c'est « d'où
 * viennent mes protéines » ou « qu'est-ce qui a fait grimper les sucres ».
 *
 * La couleur reprend celle des barres du haut, et l'initiale porte la même
 * information : une couleur ne renseigne jamais seule.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishMacros(summary: DishSummary) {
    // `map` est inline, donc stringResource y reste appelable ; `joinToString` ne
    // l'est pas, d'ou les deux etapes.
    val parts = CHIP_MACROS.map { macro ->
        val total = summary.totals[macro]
        val value = stringResource(R.string.home_macro_grams, formatGrams(total.value))
        val label = stringResource(macro.labelRes)
        if (total.complete) "$label $value" else stringResource(R.string.home_macro_at_least, label, value)
    }
    val spoken = parts.joinToString(separator = ", ")

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CHIP_MACROS.forEach { macro -> MacroChip(macro, summary.totals[macro]) }
    }
}

@Composable
private fun MacroChip(macro: Macro, total: MacroTotal) {
    val value = stringResource(R.string.home_macro_grams, formatGrams(total.value))
    Text(
        // « ≥ » et non une valeur nue : le total est amputé d'au moins une valeur
        // inconnue, donc la vraie quantité est supérieure.
        text = stringResource(
            if (total.complete) R.string.home_macro_chip else R.string.home_macro_chip_partial,
            stringResource(macro.initialRes),
            value,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = NeonTheme.macros[macro].base,
    )
}

/**
 * Une ligne d'aliment.
 *
 * Elle ne porte pas le clic : c'est le plat qui l'a, et il l'a en entier. Ce qu'elle
 * garde est sa **phrase** — nom, quantité, calories en une fois plutôt qu'en trois
 * arrêts. Le plat étant devenu une cible tactile, il fusionne les nœuds qu'il
 * contient ; cette phrase est ce qui rend l'annonce du plat lisible au lieu d'être
 * une suite de fragments.
 */
@Composable
private fun EntryRow(entry: FoodEntry) {
    val description = stringResource(
        R.string.home_entry_a11y,
        entry.displayName,
        formatGrams(entry.quantity),
        entry.unit,
        entry.macros.kcal.roundToInt(),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.home_entry_quantity, formatGrams(entry.quantity), entry.unit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.home_entry_kcal, entry.macros.kcal.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Les cinq macros affichées par plat. Les calories ont déjà leur chiffre en tête. */
private val CHIP_MACROS = listOf(Macro.PROTEIN, Macro.CARBS, Macro.SUGARS, Macro.FAT, Macro.FIBER)

internal val Macro.initialRes: Int
    @StringRes get() = when (this) {
        Macro.CALORIES -> R.string.macro_initial_calories
        Macro.PROTEIN -> R.string.macro_initial_protein
        Macro.CARBS -> R.string.macro_initial_carbs
        Macro.SUGARS -> R.string.macro_initial_sugars
        Macro.FAT -> R.string.macro_initial_fat
        Macro.FIBER -> R.string.macro_initial_fiber
    }
