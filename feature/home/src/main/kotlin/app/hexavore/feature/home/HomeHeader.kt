package app.hexavore.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.TrendGlyph
import app.hexavore.core.designsystem.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * La barre du jour : ce qu'on regarde, et les deux portes de l'accueil.
 *
 * Sortie de `HomeScreen` quand le seuil de fonctions par fichier a mordu, et le
 * decoupage suit ce que les choses sont : cette barre dit **quel jour** l'ecran
 * montre -- depuis que l'accueil en porte un -- la ou le reste du fichier dit ce que
 * ce jour contient.
 */

/**
 * Le titre du jour, et les deux portes de la barre : le poids et le profil.
 *
 * **Le titre est la date quand ce n'est pas aujourd'hui.** C'est le seul endroit de
 * l'écran qui dise quel jour on regarde, et c'est ce que le bouton d'ajout va écrire.
 *
 * En dessous, et seulement alors, le chemin du retour ([TodayChip]).
 */
@Composable
internal fun DayHeader(actions: HomeActions, day: LocalDate?, onBackToToday: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DayTitle(actions, day)
        // Rien du tout quand on est aujourd'hui : un bouton grise qui ne fait rien
        // occuperait la place et poserait la question de ce qu'il fait la.
        AnimatedVisibility(visible = day != null) { TodayChip(onBackToToday) }
    }
}

/**
 * Le chemin visible du retour à aujourd'hui.
 *
 * Le bouton retour du système y ramène aussi, mais **un geste sans représentation
 * visible est introuvable pour qui ne le connaît pas** — c'est la règle que
 * [docs/02][parcours] applique déjà au balayage qui supprime une ligne. Il reste le
 * raccourci de celui qui le connaît, jamais le seul chemin.
 *
 * La pastille d'aujourd'hui est un troisième chemin, mais elle sort du calendrier dès
 * qu'on remonte de plus d'une semaine.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun TodayChip(onBackToToday: () -> Unit) {
    AssistChip(
        onClick = onBackToToday,
        label = { Text(stringResource(R.string.home_back_to_today)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

/** La date et les deux icônes, sur une ligne. */
@Composable
private fun DayTitle(actions: HomeActions, day: LocalDate?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day?.longLabel() ?: stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Des icones seules, sans libelle : ce sont les portes les moins frequentees
        // de l'ecran, et le titre du jour doit rester ce qu'on lit en premier.
        Row {
            IconButton(onClick = actions.onOpenWeight) {
                TrendGlyph(contentDescription = stringResource(R.string.home_open_weight))
            }
            IconButton(onClick = actions.onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = stringResource(R.string.home_open_profile),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * « lundi 10 août 2026 », dans la langue de l'appareil.
 *
 * Format long et non abrégé : le titre est ce qui dit sur quel jour le bouton d'ajout
 * va écrire, et « 10/08 » se confond avec le mois d'à côté d'un coup d'œil trop rapide.
 */
private fun LocalDate.longLabel(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
