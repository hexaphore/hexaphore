package app.hexaphore.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.hexaphore.core.designsystem.component.MacroSegmentRing
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.usecase.CalendarDay
import java.time.LocalDate

/**
 * Une journée, réduite à ce qui tient sous le pouce.
 *
 * **Trois états, et ils ne se confondent pas.**
 *
 * - Une journée **notée** porte son anneau segmenté, rempli selon l'objectif du jour.
 * - Une journée **sans saisie** n'a pas d'anneau du tout — ni vide, ni gris :
 *   **absent**. C'est la seule façon de ne pas confondre « je n'ai rien noté » avec
 *   « je n'ai rien mangé », et c'est un critère de fin de la tranche 7.
 * - Une journée **à venir** s'affiche en retrait et ne s'ouvre pas : [docs/02][parcours]
 *   interdit la saisie dans le futur, et un écran Journée d'un jour à venir ne pourrait
 *   rien proposer d'utile tout en laissant croire le contraire.
 *
 * **Elle ne calcule pas sa taille.** Elle prend la place que `CalendarPane` lui donne
 * et y loge un anneau. C'est le désaccord entre deux comptes de marges — un ici, un
 * dans le calcul — qui rendait les pastilles ovales ; il ne reste qu'un compte.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun DayCell(
    date: LocalDate,
    state: CalendarUiState,
    /** La place que la cellule occupe ; l'anneau tient dedans. */
    footprint: Dp,
    selected: LocalDate?,
    onOpenDay: (LocalDate) -> Unit,
) {
    val future = state.isFuture(date)
    val day = state.days[date]
    // Le jour affiche par l'ecran. `null` veut dire aujourd'hui : c'est la meme
    // convention que partout ailleurs, et elle evite qu'un ecran laisse ouvert
    // pendant la nuit garde son cerne sur la veille.
    val shown = date == (selected ?: state.today)

    Box(
        modifier = Modifier
            // La cellule prend sa place, sans en retirer ni en ajouter : c'est ce
            // qui garantit que sept tiennent dans la largeur. Elle ne compte rien
            // elle-meme, elle consomme ce que `CalendarPane` a calcule.
            .size(footprint)
            // Un jour a venir n'est pas cliquable : pas de ride au toucher, pas de
            // navigation, et le lecteur d'ecran ne l'annonce pas comme un bouton.
            .let { base -> if (future) base else base.clickable { onOpenDay(date) } },
        contentAlignment = Alignment.Center,
    ) {
        MacroSegmentRing(
            progress = day.progress(),
            diameter = ringDiameter(footprint),
            contentDescription = stringResource(date.labelOf(day, future), date.dayOfMonth),
            center = {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        future -> MaterialTheme.colorScheme.outline
                        // Le jour affiche, et non plus seulement aujourd'hui : c'est
                        // lui que les six compteurs decrivent, et sur lui que le
                        // bouton d'ajout ecrit.
                        shown -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (shown) FontWeight.Bold else null,
                )
            },
        )
    }
}

/** Ce que le lecteur d'écran annonce : un anneau absent ne s'entend pas. */
private fun LocalDate.labelOf(day: CalendarDay?, future: Boolean): Int = when {
    future -> R.string.calendar_day_future_a11y
    day == null -> R.string.calendar_day_empty_a11y
    else -> R.string.calendar_day_a11y
}

/**
 * Ce que l'anneau doit remplir, macro par macro.
 *
 * Vide quand la journée n'existe pas **ou** qu'aucun objectif ne la couvrait : dans les
 * deux cas il n'y a rien à comparer, et un anneau plein dirait le contraire. Une
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
