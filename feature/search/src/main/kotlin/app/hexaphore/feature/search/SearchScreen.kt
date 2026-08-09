package app.hexaphore.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.component.NeonButtonStyle
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.nutrition.Macro
import kotlin.math.roundToInt

/** L'écran de recherche, branché sur le graphe d'injection. */
@Composable
internal fun SearchRoute(
    onPick: (FoodId) -> Unit,
    onCreate: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onPick = onPick,
        onToggleFavorite = viewModel::onToggleFavorite,
        onCreate = { onCreate(query.trim()) },
        onClose = onClose,
    )
}

/**
 * La recherche, sans état.
 *
 * **Le champ tient son propre texte** ([D45][decisions]) : faire transiter la frappe
 * par un `StateFlow` fait reculer le curseur en frappe rapide, et c'est justement
 * ici que la frappe est rapide.
 *
 * [decisions]: docs/11-decisions.md
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onPick: (FoodId) -> Unit,
    onToggleFavorite: (Food) -> Unit,
    onCreate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            QueryField(onQueryChange)

            when (state) {
                is SearchUiState.Shortcuts -> Shortcuts(state, onPick, onToggleFavorite)
                SearchUiState.Searching -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is SearchUiState.Results -> FoodList(state.foods, onPick, onToggleFavorite)
                is SearchUiState.Empty -> NoResult(state.query, onCreate)
                SearchUiState.Error -> ReadFailed(onClose)
            }
        }
    }
}

/**
 * Le champ, focalisé et clavier ouvert dès l'ouverture.
 *
 * C'est l'écran le plus utilisé de l'application : un tap de plus pour poser le
 * curseur serait un tap payé à chaque saisie.
 */
@Composable
private fun QueryField(onQueryChange: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onQueryChange(it)
        },
        label = { Text(stringResource(R.string.search_field_label)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Search,
        ),
    )
}

@Composable
private fun Shortcuts(state: SearchUiState.Shortcuts, onPick: (FoodId) -> Unit, onToggleFavorite: (Food) -> Unit) {
    if (state.recent.isEmpty() && state.favorites.isEmpty()) {
        Empty(
            title = stringResource(R.string.search_first_title),
            body = stringResource(R.string.search_first_body),
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (state.favorites.isNotEmpty()) {
            item(key = "favoris") { SectionTitle(stringResource(R.string.search_favorites)) }
            items(state.favorites, key = { "fav-${it.id.value}" }) { FoodRow(it, onPick, onToggleFavorite) }
        }
        if (state.recent.isNotEmpty()) {
            item(key = "recents") { SectionTitle(stringResource(R.string.search_recent)) }
            items(state.recent, key = { "rec-${it.id.value}" }) { FoodRow(it, onPick, onToggleFavorite) }
        }
    }
}

@Composable
private fun FoodList(foods: List<Food>, onPick: (FoodId) -> Unit, onToggleFavorite: (Food) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items(foods, key = { it.id.value }) { FoodRow(it, onPick, onToggleFavorite) }
    }
}

/**
 * Un résultat : nom, marque, et calories pour 100 g — assez pour choisir sans ouvrir.
 *
 * **Une énergie inconnue s'affiche « — » et non « 0 kcal ».** 143 aliments de la
 * table sont dans ce cas, et ce ne sont pas des rebuts : la feta, les câpres, la
 * canneberge. Les montrer à zéro calorie serait mentir sur une donnée qui manque.
 */
@Composable
private fun FoodRow(food: Food, onPick: (FoodId) -> Unit, onToggleFavorite: (Food) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(food.id) }
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
        IconButton(onClick = { onToggleFavorite(food) }) {
            Icon(
                imageVector = if (food.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(
                    if (food.favorite) R.string.search_unpin else R.string.search_pin,
                    food.name,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoResult(query: String, onCreate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.search_no_result, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(
            text = stringResource(R.string.search_create, query),
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
            style = NeonButtonStyle.FILLED,
        )
    }
}

@Composable
private fun ReadFailed(onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.search_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.search_error_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(text = stringResource(R.string.search_close), onClick = onClose)
    }
}

@Composable
private fun Empty(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}
