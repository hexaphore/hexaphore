package app.hexaphore.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.DraftTextField
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.diary.FavoriteDish
import app.hexaphore.domain.diary.FavoriteDishId

/** La liste des plats favoris, branchée sur le graphe d'injection. */
@Composable
internal fun FavoritesRoute(
    onPick: (FavoriteDishId) -> Unit,
    onClose: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    FavoritesScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onPick = onPick,
        onClose = onClose,
    )
}

/**
 * Choisir un plat déjà composé, pour le rejouer.
 *
 * **Aucune suppression ici** ([D62][decisions]). Cette liste ne sert qu'à choisir ;
 * retirer un favori se fait par l'étoile de l'écran de validation, qui est aussi
 * l'endroit où on l'a mis. Deux endroits pour la même décision auraient été deux
 * endroits à tenir d'accord.
 *
 * Choisir ouvre l'écran de validation **prérempli et modifiable** : un favori est un
 * modèle, pas un raccourci d'écriture. Corriger la quantité avant d'enregistrer est le
 * cas courant, pas l'exception.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun FavoritesScreen(
    state: FavoritesUiState,
    onQueryChange: (String) -> Unit,
    onPick: (FavoriteDishId) -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.favorites_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.lg),
            )

            when (state) {
                FavoritesUiState.Loading -> Unit
                FavoritesUiState.Error -> Body(stringResource(R.string.favorites_error))
                is FavoritesUiState.Content -> {
                    DraftTextField(
                        initial = state.query,
                        onValueChange = onQueryChange,
                        label = stringResource(R.string.favorites_search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FavoriteList(state, onPick)
                }
            }

            NeonButton(
                text = stringResource(R.string.favorites_close),
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
            )
        }
    }
}

@Composable
private fun ColumnScope.FavoriteList(state: FavoritesUiState.Content, onPick: (FavoriteDishId) -> Unit) {
    if (state.favorites.isEmpty()) {
        // Une liste vide dit quoi faire, pas seulement qu'elle est vide -- et elle
        // distingue « rien ne correspond » de « vous n'en avez pas encore ».
        Body(stringResource(if (state.filteredOut) R.string.favorites_none_matching else R.string.favorites_empty))
        return
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(items = state.favorites, key = { it.id.value }) { favorite ->
            FavoriteRow(favorite = favorite, onPick = { onPick(favorite.id) })
        }
    }
}

/**
 * Une ligne : le nom, puis ce qu'il contient.
 *
 * Les aliments sous le nom plutôt qu'un total de calories : le total serait celui du
 * jour de l'enregistrement, et un favori qui cite des fiches vivantes ne vaut pas
 * forcément la même chose aujourd'hui. Annoncer un chiffre qui peut avoir bougé est
 * pire que ne pas en annoncer.
 */
@Composable
private fun FavoriteRow(favorite: FavoriteDish, onPick: () -> Unit) {
    val contents = favorite.components.joinToString(separator = ", ") { it.name }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.favorites_pick), onClick = onPick)
            .semantics(mergeDescendants = true) { contentDescription = "${favorite.name}. $contents" }
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = favorite.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = contents,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
