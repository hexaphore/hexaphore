package app.hexaphore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.BarcodeGlyph
import app.hexaphore.core.designsystem.component.CameraGlyph
import app.hexaphore.core.designsystem.component.MacroBar
import app.hexaphore.core.designsystem.component.MacroHexagon
import app.hexaphore.core.designsystem.component.MacroQuarter
import app.hexaphore.core.designsystem.component.MacroUnit
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.core.designsystem.theme.Timing
import app.hexaphore.domain.diary.DaySummary
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.nutrition.Macro
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** L'accueil, branché sur le graphe d'injection. */
@Composable
fun HomeRoute(routes: HomeRoutes, onOpenDay: (java.time.LocalDate) -> Unit = {}, onOpenMonth: () -> Unit = {}) {
    val viewModel: HomeViewModel = hiltViewModel()
    val calendarViewModel: CalendarViewModel = hiltViewModel()
    val calendar by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val nameTaken by viewModel.favoriteNameTaken.collectAsStateWithLifecycle()
    val aiConfigured by viewModel.aiConfigured.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        pendingUndo = pendingUndo,
        aiConfigured = aiConfigured,
        favoriteNameTaken = nameTaken,
        onDismissFavoriteError = viewModel::onDismissFavoriteError,
        calendar = { CalendarStrip(state = calendar, onOpenDay = onOpenDay, onOpenMonth = onOpenMonth) },
        actions = remember(viewModel, routes) {
            HomeActions(
                onAddDish = routes.onAddDish,
                onScan = routes.onScan,
                onDescribe = routes.onDescribe,
                onPhotograph = routes.onPhotograph,
                onEditDish = routes.onEditDish,
                onDeleteDish = viewModel::onDeleteDish,
                onDeleteEntry = viewModel::onDeleteEntry,
                onUndo = viewModel::onUndo,
                onUndoExpired = viewModel::onUndoExpired,
                onRetry = viewModel::retry,
                onSetUpGoal = routes.onSetUpGoal,
                onOpenSettings = routes.onOpenSettings,
                onToggleFavorite = viewModel::onToggleFavorite,
                onOpenFavorites = routes.onOpenFavorites,
            )
        },
    )
}

/**
 * L'accueil, sans état.
 *
 * Tout tient en un défilement vertical : le bloc de la journée, puis les plats.
 * Le bandeau calendrier arrive avec la tranche qui lui donne une destination — un
 * bandeau qui n'ouvre rien n'est pas une avance.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    pendingUndo: Dish?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    aiConfigured: Boolean = false,
    favoriteNameTaken: Boolean = false,
    onDismissFavoriteError: () -> Unit = {},
    /**
     * Le bandeau des sept derniers jours, ou rien.
     *
     * Un emplacement et non un composant : l'accueil n'a pas a connaitre le calendrier
     * ni son `ViewModel`, et les apercus se composent sans lui.
     */
    calendar: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.home_entry_deleted)
    val undo = stringResource(R.string.home_entry_undo)

    // La barre reste affichee cinq secondes, ni quatre ni dix : SnackbarDuration
    // n'offre que ces deux-la, donc la fenetre est tenue par le delai et la barre
    // par un affichage indefini. Voir Timing dans :core:designsystem.
    LaunchedEffect(pendingUndo) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = withTimeoutOrNull(Timing.UNDO_WINDOW_MILLIS) {
            snackbarHostState.showSnackbar(
                message = deleted,
                actionLabel = undo,
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (result == SnackbarResult.ActionPerformed) actions.onUndo() else actions.onUndoExpired()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { DayActions(actions, aiConfigured) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            DayHeader(actions.onOpenSettings)
            calendar()

            when (state) {
                HomeUiState.Loading -> Unit
                is HomeUiState.Content -> DayContent(
                    summary = state.summary,
                    actions = actions,
                    favoriteNameTaken = favoriteNameTaken,
                    onDismissFavoriteError = onDismissFavoriteError,
                )

                HomeUiState.Error -> UnreadableDay(actions.onRetry)
            }
        }
    }
}

/** Le titre du jour, et la porte vers le profil. */
@Composable
private fun DayHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Une icone seule, sans libelle : c'est la porte la moins frequentee de
        // l'ecran, et le titre du jour doit rester ce qu'on lit en premier.
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = stringResource(R.string.home_open_profile),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Les boutons flottants, empilés.
 *
 * L'étoile ouvre les plats déjà composés, « Ajouter » la recherche — qui porte aussi
 * la saisie manuelle, puisqu'un aliment tapé à la main devient une fiche. « Ajouter »
 * reste le geste principal : c'est le seul qui porte un libellé.
 *
 * **Les quatre modes de saisie y sont enfin**, et c'est [docs/02][parcours] au complet
 * à une forme près : une colonne plutôt qu'un arc déployé par un bouton unique. L'arc
 * viendra quand il aura quelque chose à replier — quatre boutons empilés se visent
 * aussi bien, et se codent sans animation.
 *
 * Les deux modes d'IA sont **grisés ensemble** : c'est la même clé qui leur manque.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun DayActions(actions: HomeActions, aiConfigured: Boolean) {
    var explaining by rememberSaveable { mutableStateOf(false) }

    if (explaining) {
        AiUnavailableDialog(
            onConfigure = {
                explaining = false
                actions.onOpenSettings()
            },
            onDismiss = { explaining = false },
        )
    }

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AiButton(
            label = stringResource(R.string.home_photograph),
            configured = aiConfigured,
            onClick = actions.onPhotograph,
            onExplain = { explaining = true },
        ) { label -> CameraGlyph(contentDescription = label) }
        AiButton(
            label = stringResource(R.string.home_describe),
            configured = aiConfigured,
            onClick = actions.onDescribe,
            onExplain = { explaining = true },
        ) { label -> Icon(imageVector = Icons.Filled.Edit, contentDescription = label) }
        SmallFloatingActionButton(onClick = actions.onScan) {
            BarcodeGlyph(contentDescription = stringResource(R.string.home_scan))
        }
        SmallFloatingActionButton(onClick = actions.onOpenFavorites) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.home_open_favorites),
            )
        }
        ExtendedFloatingActionButton(onClick = actions.onAddDish) {
            Text(text = stringResource(R.string.home_add_dish))
        }
    }
}

/**
 * Visible et grisé plutôt que caché ([D73][decisions]), et **tapable dans les deux
 * cas**.
 *
 * Un bouton absent tant qu'aucune clé n'est saisie ne s'apprend jamais : personne ne
 * cherche dans les réglages une fonctionnalité dont rien n'indique l'existence. Un
 * bouton inerte n'apprend rien non plus — c'est pourquoi l'appui ouvre l'explication
 * et le chemin vers les réglages, au lieu de ne rien faire.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun AiButton(
    label: String,
    configured: Boolean,
    onClick: () -> Unit,
    onExplain: () -> Unit,
    icon: @Composable (String) -> Unit,
) {
    val unavailable = stringResource(R.string.home_describe_unavailable)

    SmallFloatingActionButton(
        onClick = if (configured) onClick else onExplain,
        containerColor = if (configured) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        // Le grisé ne se voit pas au lecteur d'ecran : sans cette phrase, le bouton
        // s'annonce comme n'importe quel autre et l'appui semble sans effet.
        modifier = Modifier.semantics { if (!configured) stateDescription = unavailable },
    ) {
        // Les deux glyphes suivent la couleur du contenu : le grise se decide ici,
        // une fois, plutot que dans chaque appelant.
        CompositionLocalProvider(
            LocalContentColor provides
                if (configured) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            icon(label)
        }
    }
}

/**
 * Ce qu'on dit quand il n'y a pas de clé, et **où aller**.
 *
 * Une explication sans chemin obligerait à chercher soi-même la bonne section des
 * réglages ; le bouton l'ouvre. [docs/02][parcours] veut cette explication courte :
 * ce qui manque, ce que ça coûte, et rien de plus.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun AiUnavailableDialog(onConfigure: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_ai_title)) },
        text = { Text(stringResource(R.string.home_ai_explanation)) },
        confirmButton = { TextButton(onClick = onConfigure) { Text(stringResource(R.string.home_ai_configure)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_ai_later)) } },
    )
}

/**
 * La journée, avec ou **sans** objectif.
 *
 * Une journée sans objectif n'est pas une journée à zéro : c'est une journée qu'on ne
 * peut comparer à rien, parce qu'aucun objectif ne courait ce jour-là — avant
 * l'onboarding, ou pour une journée notée avant qu'un objectif soit posé
 * ([D04][decisions]). Les six totaux s'affichent alors sans jauge, et l'écran invite à
 * répondre aux questions plutôt que d'inventer une cible.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun DayContent(
    summary: DaySummary,
    actions: HomeActions,
    favoriteNameTaken: Boolean,
    onDismissFavoriteError: () -> Unit,
) {
    val goal = summary.goal
    if (goal != null) {
        RemainingBlock(summary, goal)
        MacroBars(summary, goal)
    } else {
        NoGoal(actions.onSetUpGoal)
        MacroTotalsOnly(summary)
    }
    if (summary.logged) {
        DishList(
            dishes = summary.dishes,
            zone = summary.zone,
            actions = actions,
            favoriteNameTaken = favoriteNameTaken,
            onDismissFavoriteError = onDismissFavoriteError,
        )
    } else {
        EmptyDay()
    }
}

/**
 * L'hexagone des macros, puis le grand chiffre.
 *
 * Le chiffre est sous la figure et non en son centre : les six quartiers prennent
 * naissance au centre, un texte y serait recouvert dès la première bouchée.
 *
 * C'est le **restant** qui s'affiche, pas le consommé — l'information dont on a
 * besoin au moment de décider quoi manger. Un dépassement l'affiche en négatif,
 * sans rouge d'alerte ni message : c'est une donnée, pas un jugement.
 */
@Composable
private fun RemainingBlock(summary: DaySummary, dailyGoal: DailyGoal) {
    val consumed = summary.totals[Macro.CALORIES].value
    val goal = dailyGoal.kcal
    val remaining = goal - consumed

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Un intervalle de plus sous la figure : les six lettres touchent le bas de
        // sa zone, et le « G » des glucides venait buter contre le grand chiffre.
        MacroHexagon(quarters = summary.quarters(dailyGoal), modifier = Modifier.padding(bottom = Spacing.md))
        Text(
            text = abs(remaining).roundToInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(if (remaining < 0) R.string.home_over_label else R.string.home_remaining_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.home_consumed_of_goal, consumed.roundToInt(), goal.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Les six quartiers, dérivés des totaux et de l'objectif du jour.
 *
 * Un objectif nul rendrait un ratio infini : le quartier est alors vide, ce qui est
 * la seule lecture honnête d'une cible qui n'existe pas.
 */
private fun DaySummary.quarters(goal: DailyGoal): Map<Macro, MacroQuarter> = Macro.entries.associateWith { macro ->
    val total = totals[macro]
    val target = goal[macro]
    MacroQuarter(
        ratio = if (target > 0.0) (total.value / target).toFloat() else 0f,
        complete = total.complete,
    )
}

@Composable
private fun MacroBars(summary: DaySummary, goal: DailyGoal) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BAR_MACROS.forEach { macro ->
            MacroBar(
                macro = macro,
                label = stringResource(macro.labelRes),
                consumed = summary.totals[macro].value.toFloat(),
                goal = goal[macro].toFloat(),
                unit = MacroUnit.GRAM,
            )
        }

        // D29 : un total ampute d'une valeur inconnue ne doit pas se lire comme
        // exact. Le dire une fois sous les barres, plutot que de bruiter chacune.
        if (BAR_MACROS.any { !summary.totals[it].complete }) {
            Text(
                text = stringResource(R.string.home_incomplete_totals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Les cinq barres, dans l'**ordre angulaire des quartiers**.
 *
 * Les calories n'en ont pas : elles ont le quartier du haut et le grand chiffre.
 * L'ordre suit celui de l'hexagone pour que l'œil passe de l'un à l'autre sans
 * traduction — deux ordres différents rendraient la couleur seule porteuse du lien.
 */
private val BAR_MACROS =
    listOf(Macro.PROTEIN, Macro.FIBER, Macro.CARBS, Macro.SUGARS, Macro.FAT)
