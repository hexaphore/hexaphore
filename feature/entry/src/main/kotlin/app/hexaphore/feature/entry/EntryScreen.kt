package app.hexaphore.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.component.NeonButtonAvailability
import app.hexaphore.core.designsystem.component.NeonButtonStyle
import app.hexaphore.core.designsystem.component.SourceBadge
import app.hexaphore.core.designsystem.component.SwipeToDelete
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.diary.DraftImpact
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.nutrition.Macro
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/** L'écran de validation, branché sur le graphe d'injection. */
@Composable
internal fun EntryRoute(
    pickedFood: FoodId?,
    onPickedFoodHandled: () -> Unit,
    onAddFood: () -> Unit,
    onClose: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Enregistre : l'ecran se referme. Un effet plutot qu'un rappel depuis onSave,
    // pour que la fermeture suive l'etat reellement atteint et non l'intention.
    LaunchedEffect(state) {
        if (state is EntryUiState.Saved) onClose()
    }

    // La fiche choisie dans la recherche s ajoute au plat en cours. La cle est videe
    // avant l ajout : revenir sur cet ecran ne doit pas rajouter la meme ligne.
    LaunchedEffect(pickedFood) {
        val id = pickedFood ?: return@LaunchedEffect
        onPickedFoodHandled()
        viewModel.onFoodPicked(id)
    }

    EntryScreen(
        state = state,
        actions = remember(viewModel, onAddFood, onClose) {
            EntryActions(
                onLineEdit = viewModel::onLineEdit,
                onAddFood = onAddFood,
                onRemoveLine = viewModel::onRemoveLine,
                onSave = viewModel::onSave,
                onFavorite = viewModel::onFavorite,
                onUnfavorite = viewModel::onUnfavorite,
                onDismissFavoriteError = viewModel::onDismissFavoriteError,
                onRetry = viewModel::onRetry,
                onClose = onClose,
            )
        },
    )
}

/**
 * L'écran de validation, sans état.
 *
 * **Il ne sait rien de la provenance de ce qu'il montre.** Aucune branche, aucun
 * paramètre, aucune chaîne ne distingue une saisie à la main d'une proposition de
 * modèle : la seule chose qui change d'un mode à l'autre est le contenu du
 * brouillon et la pastille en tête. C'est cette propriété qui évite de réécrire
 * l'écran à chaque nouveau mode de saisie, et c'est le piège central que
 * [docs/12][plan] signale depuis la conception.
 *
 * [plan]: docs/12-plan-de-developpement.md
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun EntryScreen(state: EntryUiState, actions: EntryActions, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        when (state) {
            EntryUiState.Loading, EntryUiState.Saved -> Unit
            EntryUiState.Unavailable -> UnavailableDish(actions.onClose)
            is EntryUiState.Error -> WriteFailed(actions.onRetry, actions.onClose)
            is EntryUiState.Content -> DraftEditor(state, actions)
        }
    }
}

/**
 * Le brouillon : son en-tête, ses lignes, et la barre d'actions qui ne défile pas.
 *
 * **Enregistrer et Annuler flottent au-dessus de la liste.** Placés en pied de
 * défilement, ils s'éloignaient à mesure que le plat grossissait : à cinq lignes
 * dépliées, enregistrer demandait de faire défiler un écran entier, et rien à
 * l'image ne disait que le bouton existait encore ([D48][decisions]).
 *
 * La réserve laissée sous la liste est **mesurée**, pas déclarée : la barre grandit
 * avec la taille de police, et une hauteur écrite en dur ferait passer le dernier
 * champ sous les boutons à 200 %.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun DraftEditor(state: EntryUiState.Content, actions: EntryActions) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val density = LocalDensity.current
    var actionsHeightPx by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val incomplete = stringResource(R.string.entry_incomplete_hint)
    val onIncomplete: () -> Unit = {
        scope.launch {
            // Une seule barre a la fois : trois appuis empilaient trois fois le
            // meme message, et le troisieme s'affichait dix secondes plus tard.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(incomplete)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            contentPadding = PaddingValues(bottom = with(density) { actionsHeightPx.toDp() }),
        ) {
            item(key = "en-tete") {
                DraftHeader(state, actions, dateFormatter)
            }

            items(items = state.form.lines, key = { it.id.value }) { line ->
                SwipeToDelete(
                    label = stringResource(R.string.entry_remove_line),
                    onDelete = { actions.onRemoveLine(line.id) },
                ) {
                    LineEditor(line = line, actions = actions)
                }
            }

            item(key = "pied") {
                DraftFooter(state, actions)
            }
        }

        DraftActions(
            state = state,
            actions = actions,
            onIncomplete = onIncomplete,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { actionsHeightPx = it.height },
        )

        // Juste au-dessus de la barre d'actions, et non collee au bas de l'ecran :
        // posee dessous, elle passerait sous les boutons flottants de D48.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { actionsHeightPx.toDp() }),
        )
    }
}

@Composable
private fun DraftHeader(state: EntryUiState.Content, actions: EntryActions, dateFormatter: DateTimeFormatter) {
    var naming by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (state.form.dishId == null) R.string.entry_title_new else R.string.entry_title_edit,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // L'etoile n'apparait que sur un brouillon complet : un favori sans
            // ligne enregistrable ne rejouerait rien, et il n'y a rien a expliquer
            // sur un plat qu'on est en train de remplir.
            if (state.favoritable) {
                FavoriteStar(
                    favorite = state.favorite,
                    onToggle = { if (state.favorite) actions.onUnfavorite() else naming = true },
                )
            }
        }

        if (naming) {
            FavoriteNameDialog(
                proposal = state.form.proposedFavoriteName(),
                nameTaken = state.favoriteNameTaken,
                onConfirm = actions.onFavorite,
                onDismiss = {
                    naming = false
                    actions.onDismissFavoriteError()
                },
            )
        }
        // La boite se referme d'elle-meme des que le favori existe : c'est le seul
        // signal fiable que l'ecriture a abouti.
        LaunchedEffect(state.favorite) {
            if (state.favorite) naming = false
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceBadge(source = state.form.source)
            Text(
                text = dateFormatter.format(state.form.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ce qui reste dans le défilement : ajouter une ligne, et le poids de la saisie. */
@Composable
private fun DraftFooter(state: EntryUiState.Content, actions: EntryActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        NeonButton(
            text = stringResource(R.string.entry_add_line),
            onClick = actions.onAddFood,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        state.impact?.let { Totals(it) }
    }
}

/**
 * Les deux issues de l'écran, côte à côte et toujours à l'image.
 *
 * L'explication de ce qui manque est la **réponse** du bouton indisponible à un appui,
 * ce que [D28][decisions] demandait déjà. Elle passe par une barre plutôt que par un
 * texte glissé au-dessus des boutons : cette version-là existait, et elle était
 * invisible — un `labelSmall` gris apparaissant sous le pouce au moment exact où l'œil
 * est sur le bouton ([D56][decisions]).
 *
 * Annuler ne demande pas confirmation : il fait exactement ce que fait le retour
 * arrière, qui est là de toute façon. Un garde-fou sur l'un des deux chemins et pas
 * sur l'autre ne protège rien.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun DraftActions(
    state: EntryUiState.Content,
    actions: EntryActions,
    onIncomplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                NeonButton(
                    text = stringResource(R.string.entry_cancel),
                    onClick = actions.onClose,
                    modifier = Modifier.weight(1f),
                    availability = if (state.saving) {
                        NeonButtonAvailability.DISABLED
                    } else {
                        NeonButtonAvailability.AVAILABLE
                    },
                )
                NeonButton(
                    // Le bouton dit ce qu'il va faire. Vide de ses lignes, un plat
                    // relu se supprime a l'enregistrement : le libelle l'annonce
                    // plutot que de laisser le plat disparaitre sous « Enregistrer ».
                    text = stringResource(
                        when {
                            state.saving -> R.string.entry_saving
                            state.emptying -> R.string.entry_delete_dish
                            else -> R.string.entry_save
                        },
                    ),
                    onClick = { if (state.saveable) actions.onSave() else onIncomplete() },
                    modifier = Modifier.weight(1f),
                    style = NeonButtonStyle.FILLED,
                    availability = when {
                        // Pendant l'ecriture, il n'y a rien a expliquer : le bouton
                        // est inerte et TalkBack l'annonce desactive.
                        state.saving -> NeonButtonAvailability.DISABLED
                        // Incomplet : grise, mais il repond, et son appui dit ce
                        // qui manque.
                        !state.saveable -> NeonButtonAvailability.UNAVAILABLE
                        else -> NeonButtonAvailability.AVAILABLE
                    },
                )
            }
        }
    }
}

/**
 * Ce que la saisie pèse, et ce qu'il restera.
 *
 * Le restant plutôt que le consommé, comme sur l'accueil : c'est l'information dont
 * on a besoin au moment de décider si ça rentre. Un dépassement s'affiche en
 * négatif, sans rouge d'alerte et sans message.
 */
@Composable
private fun Totals(impact: DraftImpact) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.entry_total_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.entry_kcal, impact.draftKcal.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                color = NeonTheme.macros[Macro.CALORIES].base,
            )
        }
        // Rien tant qu'aucun objectif ne court : il n'y a alors pas de « restant »,
        // et afficher le cout du brouillon a sa place inventerait une reference.
        impact.remainingKcal?.let { remaining ->
            Text(
                text = stringResource(
                    if (remaining < 0) R.string.entry_over else R.string.entry_remaining,
                    abs(remaining).roundToInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnavailableDish(onClose: () -> Unit) {
    Message(
        title = stringResource(R.string.entry_unavailable_title),
        body = stringResource(R.string.entry_unavailable_body),
        action = stringResource(R.string.entry_close),
        onAction = onClose,
    )
}

@Composable
private fun WriteFailed(onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.entry_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.entry_error_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(
            text = stringResource(R.string.entry_error_retry),
            onClick = onRetry,
            style = NeonButtonStyle.FILLED,
        )
        NeonButton(text = stringResource(R.string.entry_close), onClick = onClose)
    }
}

@Composable
private fun Message(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(text = action, onClick = onAction)
    }
}
