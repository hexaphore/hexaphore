package app.hexavore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le point qui dit « il y a quelque chose à faire ici ».
 *
 * **Il n'a pas de chiffre.** Un compteur inviterait à vider une file, alors qu'une
 * pastille du projet désigne une **situation** et non des messages empilés : « aucune
 * clé » n'arrive pas trois fois. Le chiffre aurait en outre demandé de décider ce qu'on
 * compte, et il n'y a rien à compter.
 *
 * **La couleur ne suffit pas à le porter, et c'est pour cela qu'il a une place.** Un
 * point posé au coin supérieur droit d'une icône est reconnaissable par sa position
 * autant que par sa teinte ; qui ne distingue pas cette couleur voit quand même qu'un
 * élément s'est ajouté là où il n'y avait rien. La description d'accessibilité fait le
 * reste — sans elle, le point n'existe pas pour un lecteur d'écran.
 *
 * @param label ce que le lecteur d'écran annonce. Il dit **ce qu'il y a à faire**,
 *   jamais « notification » : « aucune intelligence artificielle configurée » se
 *   comprend, « 1 notification » demande d'aller voir.
 */
@Composable
fun NoticeDot(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(DotSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .semantics { contentDescription = label },
    )
}

/**
 * Une icône, et son point s'il y a lieu.
 *
 * Le point **déborde** volontairement du coin : posé à l'intérieur, il rognerait le
 * glyphe et se confondrait avec lui sur les icônes chargées.
 */
@Composable
fun WithNoticeDot(visible: Boolean, label: String, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.TopEnd) {
        content()
        if (visible) {
            NoticeDot(label = label, modifier = Modifier.offset(x = DotOverhang, y = -DotOverhang))
        }
    }
}

/** Assez gros pour se voir à côté d'une icône, assez petit pour ne pas la cacher. */
private val DotSize: Dp = 8.dp

/** Ce dont le point sort du coin de l'icône, pour ne pas mordre dessus. */
private val DotOverhang: Dp = 2.dp
