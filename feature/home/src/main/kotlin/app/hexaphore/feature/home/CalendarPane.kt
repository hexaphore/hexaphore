package app.hexaphore.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexaphore.core.designsystem.theme.Spacing
import com.kizitonwose.calendar.compose.VerticalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Le calendrier de l'accueil : une semaine, qui grandit en mois.
 *
 * **Le même calendrier qui change de hauteur**, et non deux écrans à tenir d'accord.
 * Replié, il montre la semaine en cours ; une poignée tirée vers le bas le déplie en
 * mois défilant, et la même poignée tirée vers le haut le replie.
 *
 * **La poignée plutôt que le calendrier lui-même** : déplié, le mois défile
 * verticalement, et un geste vertical sur lui appartient à son défilement. Poser le
 * geste d'ouverture au même endroit aurait fait se disputer les deux, et le perdant
 * aurait changé selon la vitesse du doigt.
 *
 * **Tout est paresseux.** `WeekCalendar` et `VerticalCalendar` sont bâtis sur
 * `LazyRow` et `LazyColumn` : seules les cellules visibles existent. La lecture du
 * journal suit le mois affiché plutôt que de tout charger d'avance ([D93][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun CalendarPane(
    state: CalendarUiState,
    onOpenDay: (LocalDate) -> Unit,
    onVisibleMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    /** Le jour affiché par l'écran, marqué d'un cerne. `null` : aujourd'hui. */
    selected: LocalDate? = null,
    /**
     * Déplié ou non — **porté par l'écran et non par ce panneau**.
     *
     * C'est l'accueil qui reçoit le défilement de la page, donc lui seul peut replier
     * le calendrier quand le doigt part vers le haut. L'état vit là où le geste arrive.
     */
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        WeekdayHeader()

        AnimatedVisibility(visible = !expanded, enter = expandVertically(), exit = shrinkVertically()) {
            WeekStrip(state, selected, onOpenDay)
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            MonthPager(state, selected, onOpenDay, onVisibleMonth)
        }

        ExpandHandle(expanded = expanded, onToggle = { onExpandedChange(!expanded) })
    }
}

/**
 * La semaine en cours, sur un `LazyRow` qui ne charge que ce qui se voit.
 *
 * Le calendrier de la bibliothèque défile horizontalement de semaine en semaine ; on
 * le borne à quelques semaines autour d'aujourd'hui, parce qu'un bandeau n'est pas
 * fait pour remonter loin — c'est le rôle du mois déplié.
 */
@Composable
private fun WeekStrip(state: CalendarUiState, selected: LocalDate?, onOpenDay: (LocalDate) -> Unit) {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weeks = rememberWeekCalendarState(
        startDate = state.today.minusWeeks(WEEKS_BACK),
        endDate = state.today.plusWeeks(1),
        firstVisibleWeekDate = state.today,
        firstDayOfWeek = firstDayOfWeek,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val place = cellFootprint(maxWidth)
        WeekCalendar(
            state = weeks,
            dayContent = { day -> DayCell(day.date, state, place, selected, onOpenDay) },
        )
    }
}

/**
 * Les mois, sur un `LazyColumn` borné à la période où l'application a servi.
 *
 * **Le mois visible remonte au modèle**, qui relit le journal autour de lui : c'est
 * ce qui évite de charger une année pour en montrer trente jours. `snapshotFlow`
 * n'émet qu'au changement réel, et `distinctUntilChanged` absorbe le reste.
 */
@Composable
private fun MonthPager(
    state: CalendarUiState,
    selected: LocalDate?,
    onOpenDay: (LocalDate) -> Unit,
    onVisibleMonth: (YearMonth) -> Unit,
) {
    val current = YearMonth.from(state.today)
    val months = rememberCalendarState(
        startMonth = current.minusMonths(MONTHS_BACK),
        endMonth = current,
        firstVisibleMonth = current,
        firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    )

    LaunchedEffect(months) {
        snapshotFlow { months.firstVisibleMonth.yearMonth }
            .filterNotNull()
            .distinctUntilChanged()
            .collect(onVisibleMonth)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val place = cellFootprint(maxWidth)
        VerticalCalendar(
            state = months,
            modifier = Modifier.heightIn(max = MonthHeight),
            monthHeader = { month ->
                Text(
                    text = month.yearMonth.monthLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
            },
            dayContent = { day -> DayCell(day.date, state, place, selected, onOpenDay) },
        )
    }
}

/**
 * La place qu'occupe une cellule : **sept d'entre elles font exactement la largeur**.
 *
 * Une simple division, et c'est tout le propos. Le calcul précédent réservait les
 * marges ici pendant que [DayCell] les dépensait de son côté — huit intervalles contre
 * quatorze. La largeur demandée dépassait la place, le parent la rognait sans toucher à
 * la hauteur, et les pastilles sortaient ovales. **Une pastille plus haute que large
 * est le symptôme d'une largeur refusée, jamais d'un dessin ovale** : l'anneau, lui, a
 * toujours été rond.
 *
 * La cellule prend cette place, l'anneau tient dedans ([ringDiameter]), et il n'y a
 * plus deux comptes de marges à tenir d'accord — il n'y en a qu'un.
 */
internal fun cellFootprint(available: Dp): Dp = available / DAYS_PER_WEEK

/**
 * Le diamètre de l'anneau dans la place d'une cellule, ses marges retirées.
 *
 * Borné des deux côtés : assez grand pour que le chiffre du jour reste lisible, assez
 * petit pour qu'une tablette ne montre pas sept médaillons. **Et jamais plus grand que
 * sa place** — sur un écran très étroit la borne basse la dépasserait, et mieux vaut
 * une pastille qui touche ses voisines qu'une pastille écrasée.
 */
internal fun ringDiameter(footprint: Dp): Dp =
    (footprint - CellPadding * 2).coerceIn(MinCellDiameter, MaxCellDiameter).coerceAtMost(footprint)

/**
 * L'air autour d'une pastille, **de chaque côté**.
 *
 * Un seul endroit la dépense désormais : [ringDiameter] la retire de la place offerte.
 * C'est le désaccord entre deux comptes de marges qui a produit les pastilles ovales,
 * et la façon sûre de ne pas les laisser se contredire est qu'il n'y en ait qu'un.
 */
internal val CellPadding: Dp = Spacing.xs

/** Ce qui sépare le geste d'ouverture du défilement du mois. */
@Composable
private fun ExpandHandle(expanded: Boolean, onToggle: () -> Unit) {
    val label = stringResource(if (expanded) R.string.calendar_collapse else R.string.calendar_expand)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = label
                onClick(label = label) {
                    onToggle()
                    true
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    // Un seul basculement par geste : le sens du doigt suffit, et un
                    // suivi continu ferait clignoter le calendrier pendant le glissement.
                    if (delta > DRAG_THRESHOLD && !expanded) onToggle()
                    if (delta < -DRAG_THRESHOLD && expanded) onToggle()
                },
            )
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = HandleWidth, height = HandleHeight)
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(HandleHeight / 2)),
        )
    }
}

/** Les sept initiales, dans l'ordre de la locale. */
@Composable
private fun WeekdayHeader() {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    Row(modifier = Modifier.fillMaxWidth()) {
        for (offset in 0 until DAYS_PER_WEEK) {
            Text(
                text = first.plus(offset.toLong())
                    .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val MonthHeight: Dp = 320.dp
internal val MinCellDiameter: Dp = 28.dp
internal val MaxCellDiameter: Dp = 48.dp
private val HandleWidth: Dp = 32.dp
private val HandleHeight: Dp = 4.dp

/** En deçà, c'est un tremblement du doigt et non une intention. */
private const val DRAG_THRESHOLD = 8f

private const val DAYS_PER_WEEK = 7
private const val WEEKS_BACK = 4L
private const val MONTHS_BACK = 24L

/** Le mois et l'annee, dans la langue de l'appareil. */
internal fun YearMonth.monthLabel(): String =
    "${month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.getDefault())} $year"
