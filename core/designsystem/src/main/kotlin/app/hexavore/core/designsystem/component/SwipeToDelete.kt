package app.hexavore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.nutrition.Macro

/**
 * Un contenu qu'un balayage vers la gauche supprime.
 *
 * Le fond qui se révèle est en teinte protéines, la seule couleur chaude et vive de
 * la palette après l'ambre des lipides. Ce n'est pas une septième teinte : la
 * palette n'en compte toujours que six, et celle-ci ne prétend rien dire des
 * protéines dans ce contexte — elle sert de surface d'alerte, avec un libellé qui
 * porte le sens. La règle du projet interdit qu'une couleur renseigne seule ; ici
 * elle ne renseigne pas du tout.
 *
 * **Un seul sens de balayage.** Vers la droite, le geste servira un jour à
 * dupliquer ou à basculer une ligne ; l'ouvrir aujourd'hui à la suppression rendrait
 * ce futur impossible sans réapprentissage.
 *
 * L'appelant reste responsable de l'annulation : la suppression part immédiatement
 * et un `Snackbar` la reprend. Voir docs/02-parcours-et-ecrans.md.
 */
@Composable
fun SwipeToDelete(
    label: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        // Un demi-ecran, plutot que le quart par defaut : les lignes de journal
        // sont hautes et etroites, et un seuil trop bas les fait disparaitre sur
        // un defilement un peu oblique.
        positionalThreshold = { distance -> distance * DISMISS_THRESHOLD },
    )
    val background = NeonTheme.macros[Macro.PROTEIN]

    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.EndToStart) return@LaunchedEffect
        onDelete()
        // Le contenu revient en place si l'appelant ne l'a pas retire. Quand il le
        // retire, cette ligne ne s'execute jamais -- l'effet part avec lui. Sans
        // elle, un echec de suppression laisserait une ligne invisible mais
        // toujours presente, ce qui est la pire des deux facons de se tromper.
        state.reset()
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // L'opacite suit l'avancement du geste : le fond se revele,
                    // il n'apparait pas d'un coup. C'est ce qui rend le geste
                    // annulable a l'oeil avant de l'etre a la main.
                    .background(background.base.copy(alpha = revealAlpha(state.progress)))
                    .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.background,
                )
            }
        },
        content = { Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) { content() } },
    )
}

/** Épaisseur minimale du fond révélé, pour que le geste se voie dès son début. */
private const val MIN_REVEAL = 0.25f
private const val MAX_REVEAL = 0.9f
private const val DISMISS_THRESHOLD = 0.5f

private fun revealAlpha(progress: Float): Float = (MIN_REVEAL + progress * (MAX_REVEAL - MIN_REVEAL))
    .coerceIn(MIN_REVEAL, MAX_REVEAL)
