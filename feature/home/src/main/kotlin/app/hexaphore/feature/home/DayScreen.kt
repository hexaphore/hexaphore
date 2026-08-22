package app.hexaphore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.theme.Spacing
import java.time.LocalDate

@Composable
internal fun DayRoute(routes: HomeRoutes, onClose: () -> Unit) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DayScreen(
        state = state,
        actions = remember(viewModel, routes) { routes.toActions(viewModel) },
        onClose = onClose,
    )
}

/**
 * Une journée passée, relue.
 *
 * **Structurellement identique à l'accueil**, et c'est voulu : c'est le même
 * récapitulatif, seule la date change ([docs/02][parcours]). Réécrire les six barres
 * et la liste des plats pour un jour passé aurait donné deux écrans qui divergent au
 * premier changement — celui que la tranche 2 avait déjà évité pour les modes de
 * saisie.
 *
 * Trois différences seulement : la date remplace le titre, une croix remplace la porte
 * des réglages, et il n'y a pas de bouton d'ajout. On vient relire, pas noter.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun DayScreen(state: HomeUiState, actions: HomeActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            DayTitle(state, onClose)

            when (state) {
                HomeUiState.Loading -> Unit
                is HomeUiState.Content -> DayContent(
                    summary = state.summary,
                    actions = actions,
                    favoriteNameTaken = false,
                    onDismissFavoriteError = {},
                )

                HomeUiState.Error -> UnreadableDay(actions.onRetry)
            }
        }
    }
}

/**
 * La date du jour relu, et la sortie.
 *
 * Elle vient de l'état et non de la route : c'est le résumé qui sait de quelle
 * journée il parle, et deux sources de date finiraient par en afficher une pendant
 * qu'on lit l'autre.
 */
@Composable
private fun DayTitle(state: HomeUiState, onClose: () -> Unit) {
    val date = (state as? HomeUiState.Content)?.summary?.date

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.calendar_close))
        }
        androidx.compose.material3.Text(
            text = date?.longLabel() ?: "",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** « lundi 10 août 2026 », dans la langue de l'appareil. */
private fun LocalDate.longLabel(): String = format(
    java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL),
)
