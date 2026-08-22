package app.hexaphore.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.hexaphore.core.designsystem.component.MacroSegmentRing
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.usecase.CalendarDay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Les sept derniers jours, fixes en tête de l'accueil.
 *
 * Chaque pastille porte le jour de la semaine, son numéro, et l'anneau segmenté des
 * six macros ([docs/02][parcours]).
 *
 * **Une journée sans saisie est neutre, et c'est structurel** : elle n'a pas de ligne
 * dans le calendrier, donc il n'y a rien à dessiner. Un `?: MacroTotals.Empty` aurait
 * peint un anneau vide indiscernable d'un jour de jeûne — c'est le critère de fin de
 * cette tranche, et il se tient ici parce qu'il se tient déjà dans le domaine.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun CalendarStrip(
    state: CalendarUiState,
    onOpenDay: (LocalDate) -> Unit,
    onOpenMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = state.month.monthLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onOpenMonth).padding(vertical = Spacing.xs),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            state.strip.forEach { date ->
                DayPill(
                    date = date,
                    day = state.days[date],
                    today = date == state.today,
                    onClick = { onOpenDay(date) },
                )
            }
        }
    }
}

/**
 * Une journée, réduite à ce qui tient sous le pouce.
 *
 * [day] vaut `null` quand rien n'a été noté ce jour-là. L'anneau n'est alors pas
 * dessiné du tout — ni vide, ni gris : **absent**. C'est la seule façon de ne pas
 * confondre « je n'ai rien noté » avec « je n'ai rien mangé ».
 */
@Composable
private fun DayPill(date: LocalDate, day: CalendarDay?, today: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.clickable(onClick = onClick).padding(Spacing.xs),
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        MacroSegmentRing(
            progress = day.progress(),
            contentDescription = stringResource(
                if (day == null) R.string.calendar_day_empty_a11y else R.string.calendar_day_a11y,
                date.dayOfMonth,
            ),
            center = {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (today) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
        )
    }
}

/**
 * Ce que l'anneau doit remplir, macro par macro.
 *
 * Vide quand la journée n'existe pas **ou** qu'aucun objectif ne la couvrait : dans
 * les deux cas il n'y a rien à comparer, et un anneau plein dirait le contraire. Une
 * journée notée avant qu'un objectif existe garde donc ses chiffres — ils se lisent
 * dans l'écran Journée — sans que la pastille prétende les juger.
 */
internal fun CalendarDay?.progress(): Map<Macro, Float> {
    val goal = this?.goal ?: return emptyMap()
    return Macro.entries.associateWith { macro ->
        val target = goal[macro]
        if (target <= 0.0) 0f else (totals[macro].value / target).toFloat()
    }
}
