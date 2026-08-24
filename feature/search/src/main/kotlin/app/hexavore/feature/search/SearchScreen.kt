package app.hexavore.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodFilter
import app.hexavore.domain.food.FoodId

/** L'écran de recherche, branché sur le graphe d'injection. */
@Composable
internal fun SearchRoute(
    onPick: (FoodId) -> Unit,
    onManualEntry: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val picked by viewModel.picked.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    val remote by viewModel.remote.collectAsStateWithLifecycle()

    // La fiche est versee au catalogue avant de partir : un resultat de la table de
    // l'ANSES n'a qu'un identifiant provisoire tant qu'il n'y est pas ecrit, et
    // l'ecran de validation ne le retrouverait pas -- il s'ouvrirait vide.
    LaunchedEffect(picked) {
        val id = picked ?: return@LaunchedEffect
        viewModel.onPickHandled()
        onPick(id)
    }

    SearchScreen(
        state = state,
        query = query,
        filter = filter,
        remote = remote,
        actions = remember(viewModel, onManualEntry, onClose) {
            SearchActions(
                onQueryChange = viewModel::onQueryChange,
                onPick = viewModel::onPick,
                onToggleFavorite = viewModel::onToggleFavorite,
                onDelete = viewModel::onDeleteRequested,
                onToggleCategory = viewModel::onToggleCategory,
                onToggleTrait = viewModel::onToggleTrait,
                onManualEntry = onManualEntry,
                onSearchRemotely = viewModel::onSearchRemotely,
                onClose = onClose,
            )
        },
    )

    deletion?.let {
        DeleteConfirmation(
            deletion = it,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteCancelled,
        )
    }
}

/**
 * La recherche, sans état.
 *
 * **C'est le seul point d'entrée d'une saisie**, et la saisie manuelle y est une
 * branche permanente plutôt qu'une porte à côté. Un aliment tapé à la main devient
 * une fiche : il se retrouve ensuite dans cette liste, se reprend en un tap, et sa
 * quantité recalcule ses valeurs comme celle de n'importe quel autre.
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
    query: String,
    filter: FoodFilter,
    remote: RemoteSearch,
    actions: SearchActions,
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
            QueryField(actions.onQueryChange)

            // Le bandeau est hors du `Box` qui change d'etat : il doit rester la dans
            // les cinq, sans quoi un filtre qui ne rend rien deviendrait impossible a
            // defaire.
            FilterBand(filter, actions)

            Box(modifier = Modifier.weight(1f)) {
                when (state) {
                    is SearchUiState.Shortcuts -> Shortcuts(state, actions)
                    SearchUiState.Searching -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    is SearchUiState.Results -> FoodList(state.foods, remote, actions)
                    is SearchUiState.Empty -> NoResult(state.query, remote, actions)
                    SearchUiState.Error -> ReadFailed(actions.onClose)
                }
            }

            if (state != SearchUiState.Error) ManualEntry(state, query, actions.onManualEntry)
        }
    }
}

/**
 * La saisie manuelle, toujours à l'image.
 *
 * Permanente et non réservée au cas où la recherche ne rend rien : un reste de la
 * veille n'a pas de nom qu'on aurait envie de chercher, et obliger à taper deux
 * lettres pour découvrir le bouton serait un détour payé à chaque fois. Elle passe
 * en bouton plein quand la recherche n'a rien trouvé — c'est alors la seule chose à
 * faire.
 */
@Composable
private fun ManualEntry(state: SearchUiState, query: String, onManualEntry: (String) -> Unit) {
    // Nommee seulement s'il y a un nom : en mode parcours, la recherche peut ne rien
    // rendre sans qu'un seul caractere ait ete tape, et « Saisir "" a la main »
    // proposerait de creer un aliment sans nom.
    val named = state is SearchUiState.Empty && query.isNotBlank()
    NeonButton(
        text = if (named) {
            stringResource(R.string.search_manual_entry_named, query.trim())
        } else {
            stringResource(R.string.search_manual_entry)
        },
        onClick = { onManualEntry(query.trim()) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md),
        style = if (named) NeonButtonStyle.FILLED else NeonButtonStyle.OUTLINED,
    )
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
private fun Shortcuts(state: SearchUiState.Shortcuts, actions: SearchActions) {
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
            items(state.favorites, key = { "fav-" + it.id.value }) { FoodRow(it, actions) }
        }
        if (state.recent.isNotEmpty()) {
            item(key = "recents") { SectionTitle(stringResource(R.string.search_recent)) }
            items(state.recent, key = { "rec-" + it.id.value }) { FoodRow(it, actions) }
        }
    }
}

@Composable
private fun FoodList(foods: List<Food>, remote: RemoteSearch, actions: SearchActions) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items(foods, key = { it.id.value }) { FoodRow(it, actions) }
        remoteSection(remote, actions)
    }
}

@Composable
private fun NoResult(query: String, remote: RemoteSearch, actions: SearchActions) {
    // Une liste et non un simple texte : la suggestion Open Food Facts se pose dessous,
    // et c'est justement ici qu'elle sert le plus -- rien de local ne correspond.
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        item {
            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.search_no_result_filtered)
                } else {
                    stringResource(R.string.search_no_result, query)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        remoteSection(remote, actions)
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
