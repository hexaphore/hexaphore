package app.hexaphore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexaphore.core.designsystem.component.TrendGlyph
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
 */
@Composable
internal fun DayHeader(actions: HomeActions, day: LocalDate?) {
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
