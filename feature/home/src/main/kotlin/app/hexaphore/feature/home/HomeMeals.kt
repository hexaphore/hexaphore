package app.hexaphore.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexaphore.core.designsystem.component.SourceBadge
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.diary.FoodEntry
import app.hexaphore.domain.diary.MealSummary
import app.hexaphore.domain.diary.MealType
import app.hexaphore.domain.nutrition.Macro
import kotlin.math.roundToInt

/**
 * La liste des repas de la journée, avec leurs sous-totaux.
 *
 * Seuls les repas qui contiennent quelque chose apparaissent : un repas est créé
 * paresseusement, à sa première entrée. Les repas vides et leur ligne « Rien pour
 * l'instant » arrivent en tranche 2, avec le bouton d'ajout qui leur donne un sens.
 */
@Composable
internal fun MealList(meals: List<MealSummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        meals.forEach { meal -> MealBlock(meal) }
    }
}

@Composable
private fun MealBlock(summary: MealSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(summary.meal.type.labelRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                stringResource(
                    R.string.home_meal_subtotal,
                    summary.totals[Macro.CALORIES].value.roundToInt(),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        summary.entries.forEach { entry -> EntryRow(entry) }
    }
}

/**
 * Une ligne de journal.
 *
 * Rendue ici et non dans le design system : le composant `EntryRow` de docs/08
 * porte le balayage de suppression et le menu d'appui long, qui n'existeront qu'en
 * tranche 2. Un composant partagé à moitié fait coûte plus cher que la ligne qu'il
 * remplace.
 */
@Composable
private fun EntryRow(entry: FoodEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_entry_quantity, formatQuantity(entry.quantity), entry.unit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SourceBadge(source = entry.nutritionSource)
        Text(
            text = stringResource(R.string.home_entry_kcal, entry.macros.kcal.roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** « 1 » plutôt que « 1.0 » pour les quantités entières, qui sont la majorité. */
private fun formatQuantity(quantity: Double): String {
    val rounded = quantity.roundToInt()
    return if (quantity == rounded.toDouble()) rounded.toString() else quantity.toString()
}

private val MealType.labelRes: Int
    @StringRes get() = when (this) {
        MealType.BREAKFAST -> R.string.home_meal_breakfast
        MealType.LUNCH -> R.string.home_meal_lunch
        MealType.DINNER -> R.string.home_meal_dinner
        MealType.SNACK -> R.string.home_meal_snack
        // Un repas personnalise porte son propre nom ; en attendant l'ecran de
        // reglages qui permet d'en creer, il s'affiche comme une collation.
        MealType.CUSTOM -> R.string.home_meal_snack
    }

internal val Macro.labelRes: Int
    @StringRes get() = when (this) {
        Macro.CALORIES -> R.string.home_title
        Macro.PROTEIN -> R.string.macro_protein
        Macro.CARBS -> R.string.macro_carbs
        Macro.SUGARS -> R.string.macro_sugars
        Macro.FAT -> R.string.macro_fat
        Macro.FIBER -> R.string.macro_fiber
    }
