package app.hexaphore.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.MacroRingDefaults
import app.hexaphore.core.designsystem.component.MacroSegmentRing
import app.hexaphore.core.designsystem.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun CalendarMonthRoute(onOpenDay: (LocalDate) -> Unit, onClose: () -> Unit) {
    val viewModel: CalendarViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CalendarMonthScreen(
        state = state,
        onMonthChange = viewModel::onMonthChange,
        onOpenDay = onOpenDay,
        onClose = onClose,
    )
}

/**
 * Le mois entier, pour remonter plus loin que sept jours.
 *
 * Même règle que le bandeau, à une échelle de plus : **une case sans saisie est
 * vide**, pas remplie de zéros. À 28 dp, l'anneau segmenté n'est plus qu'une nuance
 * de couleur — c'est assez pour repérer un trou dans un mois, et c'est tout ce qu'on
 * lui demande. Le détail se lit en touchant la case.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun CalendarMonthScreen(
    state: CalendarUiState,
    onMonthChange: (Long) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
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
            MonthHeader(state.month, onMonthChange, onClose)
            WeekdayHeader()
            MonthGrid(state, onOpenDay)
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onMonthChange: (Long) -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.calendar_close))
        }
        Text(
            text = month.monthLabel(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onMonthChange(-1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.calendar_previous))
        }
        IconButton(onClick = { onMonthChange(1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.calendar_next))
        }
    }
}

/**
 * Les sept initiales, dans l'ordre de la locale.
 *
 * `WeekFields` et non « lundi d'abord » en dur : la semaine commence le dimanche dans
 * une partie du monde, et une grille décalée d'un jour est pire qu'inutile.
 */
@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdays().forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * La grille, semaine par semaine.
 *
 * Les cases avant le premier jour et après le dernier sont **vides et non
 * cliquables** : elles appartiennent à un autre mois, et les rendre actives ferait
 * sauter la vue d'un mois à l'autre sans qu'on l'ait demandé.
 */
@Composable
private fun MonthGrid(state: CalendarUiState, onOpenDay: (LocalDate) -> Unit) {
    val first = state.month.atDay(1)
    val blanks = weekdays().indexOf(first.dayOfWeek)
    val cells = List(blanks) { null } + (1..state.month.lengthOfMonth()).map(state.month::atDay)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date -> MonthCell(date, state, onOpenDay, Modifier.weight(1f)) }
                repeat(DAYS_PER_WEEK - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthCell(
    date: LocalDate?,
    state: CalendarUiState,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Box(modifier)
        return
    }
    Box(
        modifier = modifier.clickable { onOpenDay(date) }.padding(Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        MacroSegmentRing(
            progress = state.days[date].progress(),
            diameter = MacroRingDefaults.MonthDiameter,
            contentDescription = stringResource(
                if (state.days[date] == null) R.string.calendar_day_empty_a11y else R.string.calendar_day_a11y,
                date.dayOfMonth,
            ),
            center = {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (date == state.today) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
        )
    }
}

/** Les sept jours, à partir du premier de la semaine selon la locale. */
private fun weekdays(): List<DayOfWeek> {
    val first = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0 until DAYS_PER_WEEK).map { first.plus(it.toLong()) }
}

/** Le mois et l'année, dans la langue de l'appareil. */
internal fun YearMonth.monthLabel(): String =
    "${month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())} $year"

private const val DAYS_PER_WEEK = 7
