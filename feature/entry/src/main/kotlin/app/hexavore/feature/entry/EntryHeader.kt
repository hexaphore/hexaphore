package app.hexavore.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.SourceBadge
import app.hexavore.core.designsystem.theme.Spacing
import java.time.format.DateTimeFormatter

/**
 * Le haut d'une validation : ce qu'on est en train de faire, et sur quoi.
 *
 * Sorti de `EntryScreen` quand le seuil de fonctions par fichier a mordu, et le
 * decoupage suit ce que les choses sont : l'en-tete dit **de quoi il s'agit** -- un
 * plat neuf, un plat modifie, un favori reecrit -- et **ou cela ira**. Le reste du
 * fichier d'origine dit comment on le saisit.
 */

@Composable
internal fun DraftHeader(state: EntryUiState.Content, actions: EntryActions, dateFormatter: DateTimeFormatter) {
    var naming by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    when {
                        state.editingFavorite -> R.string.entry_title_favorite
                        state.form.dishId == null -> R.string.entry_title_new
                        else -> R.string.entry_title_edit
                    },
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

        val context = LocalContext.current
        LaunchedEffect(naming) {
            if (naming) actions.onNaming { number -> context.getString(R.string.entry_favorite_proposal, number) }
        }

        if (naming) {
            FavoriteNameDialog(
                proposal = stringResource(R.string.entry_favorite_proposal, state.favoriteNumber),
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
        SourceAndDay(state, dateFormatter)
    }
}

/**
 * D'ou vient ce plat, et quel jour il ira.
 *
 * **La meme ligne, deux tons.** Aujourd'hui, la date est un rappel discret ; un autre
 * jour, elle dit ce que l'appui va faire et se lit comme le reste du texte.
 *
 * Depuis qu'on peut rattraper un repas oublie, c'est le dernier endroit avant
 * l'ecriture. L'accueil annonce bien le jour en titre, mais entre les deux il y a eu
 * la recherche, un scan, ou une modale d'IA -- et une date en petit gris a cote d'un
 * badge est exactement ce qu'on ne lit pas.
 */
@Composable
private fun SourceAndDay(state: EntryUiState.Content, dateFormatter: DateTimeFormatter) {
    val jour = state.otherDay

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceBadge(source = state.form.source)
        Text(
            text = when (jour) {
                null -> dateFormatter.format(state.form.date)
                else -> stringResource(R.string.entry_other_day, dateFormatter.format(jour))
            },
            style = when (jour) {
                null -> MaterialTheme.typography.labelSmall
                else -> MaterialTheme.typography.bodyMedium
            },
            color = when (jour) {
                null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
