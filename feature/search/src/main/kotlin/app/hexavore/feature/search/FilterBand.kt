package app.hexavore.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.NeonChip
import app.hexavore.core.designsystem.component.NeonChipDivider
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.food.FoodCategory
import app.hexavore.domain.food.FoodFilter
import app.hexavore.domain.food.FoodTrait

/**
 * Le bandeau de pastilles, sous la barre de recherche.
 *
 * **Les deux qualités ouvrent le bandeau, puis un trait, puis les huit rayons.**
 * C'est l'ordre qui rend la règle lisible sans l'écrire : ce qui se cumule en ET est
 * d'un côté du trait, ce qui se cumule en OU de l'autre. Trois signaux la portent,
 * parce qu'aucun ne suffit seul — la **position** dans le bandeau, le **trait** qui
 * sépare, et la **forme** de la pastille : ronde avec une icône pour une qualité,
 * rectangle de texte pour un rayon ([D54][decisions]).
 *
 * Les qualités en tête et non à la fin : ce sont les deux seules pastilles qui
 * s'appliquent quel que soit le rayon, et le bandeau défile — mettre à la fin ce qui
 * sert le plus souvent obligerait à faire défiler pour l'atteindre.
 *
 * **Il reste à l'écran dans les cinq états.** Le retirer pendant une recherche ou sur
 * une erreur ferait sauter la mise en page à chaque frappe, et surtout retirerait le
 * seul moyen de défaire un filtre qui ne rend rien.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun FilterBand(filter: FoodFilter, actions: SearchActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TraitChip(FoodTrait.FAVORITE, R.string.search_tag_favorite, Icons.Filled.Favorite, filter, actions)
        TraitChip(FoodTrait.PERSONAL, R.string.search_tag_personal, Icons.Filled.Person, filter, actions)

        NeonChipDivider()

        FoodCategory.entries.forEach { category ->
            val label = stringResource(category.labelRes)
            NeonChip(
                label = label,
                selected = category in filter.categories,
                onClick = { actions.onToggleCategory(category) },
                contentDescription = stringResource(R.string.search_tag_category_a11y, label),
            )
        }
    }
}

@Composable
private fun TraitChip(trait: FoodTrait, labelRes: Int, icon: ImageVector, filter: FoodFilter, actions: SearchActions) {
    val label = stringResource(labelRes)
    NeonChip(
        label = label,
        selected = trait in filter.traits,
        onClick = { actions.onToggleTrait(trait) },
        leading = icon,
        contentDescription = stringResource(R.string.search_tag_trait_a11y, label),
    )
}

/**
 * Les libellés vivent en ressource, pas dans l'énumération.
 *
 * `:domain` ne connaît pas Android, et un rayon est une règle avant d'être un mot :
 * la traduction anglaise promise pour la 1.0 changera les seconds sans toucher à la
 * première.
 */
private val FoodCategory.labelRes: Int
    get() = when (this) {
        FoodCategory.FRUITS -> R.string.search_tag_fruits
        FoodCategory.LEGUMES -> R.string.search_tag_vegetables
        FoodCategory.FECULENTS -> R.string.search_tag_starches
        FoodCategory.VIANDES_POISSONS -> R.string.search_tag_meat_fish
        FoodCategory.PRODUITS_LAITIERS -> R.string.search_tag_dairy
        FoodCategory.BOISSONS -> R.string.search_tag_drinks
        FoodCategory.DESSERTS -> R.string.search_tag_desserts
        FoodCategory.SNACKS -> R.string.search_tag_snacks
    }
