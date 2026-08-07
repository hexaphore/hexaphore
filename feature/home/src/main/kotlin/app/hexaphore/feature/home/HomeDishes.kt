package app.hexaphore.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
private fun DishBlock(summary: DishSummary, zone: ZoneId, timeFormatter: DateTimeFormatter, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        summary.entries.forEach { entry ->
            SwipeToDelete(
                label = stringResource(R.string.home_entry_delete),
                onDelete = { actions.onDeleteEntry(summary.dish, entry.id) },
            ) {
                EntryRow(entry = entry, onClick = { actions.onEditDish(summary.dish.id) })
            }
        }
        DishMacros(summary)
    }
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
 * Un tap ouvre la validation du **plat** entier, et non de la seule ligne : les
 * lignes d'un plat ont été saisies ensemble, et les corriger séparément
 * obligerait à ressortir et rentrer autant de fois qu'il y a d'aliments.
 *
 * La ligne est un **seul** nœud d'accessibilité, avec son action de suppression :
 * trois nœuds à traverser pour une information qui tient en une phrase, c'est trois
 * de trop.
 */
@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit) {
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
            .clickable(onClick = onClick)
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
