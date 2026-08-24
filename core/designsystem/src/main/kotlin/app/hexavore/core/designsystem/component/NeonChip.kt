package app.hexavore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.preview.NeonPreviews
import app.hexavore.core.designsystem.preview.PreviewSurface
import app.hexavore.core.designsystem.theme.Spacing

/**
 * Une pastille de filtre, sélectionnable.
 *
 * **La forme porte la famille, pas seulement la couleur.** Le bandeau de la recherche
 * mêle deux sortes de pastilles qui ne se combinent pas de la même façon — les rayons
 * entre eux en OU, les qualités en ET par-dessus — et rien dans un texte ne le dit.
 * Une pastille [leading] est donc **ronde et porte une icône** ; une pastille de rayon
 * est un rectangle arrondi de texte seul. Deux canaux, comme partout ailleurs dans ce
 * projet : la teinte de sélection ne travaille jamais seule ([08][design]).
 *
 * Sélectionnée, elle prend la teinte primaire en fond translucide et un contour plein ;
 * au repos, un contour discret sur le fond de l'écran. Pas de lueur : un bandeau de
 * dix pastilles qui brillent toutes ne hiérarchise plus rien, et le néon est réservé
 * à ce qui porte un chiffre.
 *
 * [design]: docs/08-design-system.md
 */
@Composable
fun NeonChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    contentDescription: String = label,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(if (leading == null) ChipRadius else RoundChipRadius)

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = TouchTarget)
            .background(if (selected) accent.copy(alpha = SELECTED_FILL_ALPHA) else Color.Transparent, shape)
            .border(if (selected) SelectedBorder else RestingBorder, ink.copy(alpha = borderAlpha(selected)), shape)
            .selectable(
                selected = selected,
                role = Role.Checkbox,
                onClick = onClick,
            )
            // La famille d'une pastille se lit a la forme, a la position et au trait
            // qui separe -- trois canaux visuels, et aucun pour TalkBack. La phrase
            // annoncee est donc le quatrieme, et le seul qui ne se voie pas.
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            Icon(
                imageVector = it,
                // Le libelle dit deja de quoi il s'agit : l'icone est un second
                // canal pour l'oeil, pas une information de plus a annoncer.
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(IconSize),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
        )
    }
}

/**
 * La coupure entre les deux familles de pastilles.
 *
 * Un trait vertical et non un espace plus large : un espace se lit comme du hasard de
 * mise en page, un trait comme une frontière. C'est le seul endroit de l'écran qui
 * dise qu'il y a deux familles.
 */
@Composable
fun NeonChipDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = Spacing.xs)
            .size(width = DividerWidth, height = DividerHeight)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DIVIDER_ALPHA)),
    )
}

private val ChipRadius: Dp = 8.dp
private val RoundChipRadius: Dp = 20.dp
private val IconSize: Dp = 16.dp
private val TouchTarget: Dp = 40.dp
private val SelectedBorder: Dp = 1.5.dp
private val RestingBorder: Dp = 1.dp
private val DividerWidth: Dp = 1.dp
private val DividerHeight: Dp = 24.dp

private const val SELECTED_FILL_ALPHA = 0.16f
private const val RESTING_BORDER_ALPHA = 0.35f
private const val DIVIDER_ALPHA = 0.3f

private fun borderAlpha(selected: Boolean) = if (selected) 1f else RESTING_BORDER_ALPHA

// --- Aperçus -----------------------------------------------------------------

@NeonPreviews
@Composable
private fun NeonChipPreview() {
    PreviewSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonChip(label = "Favori", selected = true, onClick = {}, leading = Icons.Filled.Favorite)
            NeonChip(label = "Mon aliment", selected = false, onClick = {}, leading = Icons.Filled.Person)
            NeonChipDivider()
            NeonChip(label = "Fruits", selected = true, onClick = {})
            NeonChip(label = "Legumes", selected = false, onClick = {})
        }
    }
}
