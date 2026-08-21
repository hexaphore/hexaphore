package app.hexaphore.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.nutrition.Macro
import kotlin.math.roundToInt

/**
 * Un résultat : nom, marque, et calories pour 100 g — assez pour choisir sans ouvrir.
 *
 * **Une énergie inconnue s'affiche « — » et non « 0 kcal ».** 143 aliments de la
 * table sont dans ce cas, et ce ne sont pas des rebuts : la feta, les câpres, la
 * canneberge. Les montrer à zéro calorie serait mentir sur une donnée qui manque.
 *
 * **Ce que l'utilisateur a saisi lui-même se voit**, et lui seul porte une corbeille.
 * C'est la seule fiche qu'il puisse corriger ou retirer, et la seule dont les valeurs
 * n'engagent que lui ; une ligne de la table de l'ANSES est une référence publiée.
 */
@Composable
internal fun FoodRow(food: Food, actions: SearchActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { actions.onPick(food) }
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (food.editable) {
                Text(
                    text = stringResource(R.string.search_own_food),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonTheme.macros[Macro.PROTEIN].base,
                )
            }
            food.brand?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = food.per100g.kcal
                ?.let { stringResource(R.string.search_kcal_per_100g, it.roundToInt()) }
                ?: stringResource(R.string.search_unknown_energy),
            style = MaterialTheme.typography.labelSmall,
            color = NeonTheme.macros[Macro.CALORIES].base,
        )
        IconButton(onClick = { actions.onToggleFavorite(food) }) {
            Icon(
                imageVector = if (food.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(
                    if (food.favorite) R.string.search_unpin else R.string.search_pin,
                    food.displayName,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (food.editable) {
            IconButton(onClick = { actions.onDelete(food) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.search_delete, food.displayName),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
