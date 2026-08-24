package app.hexavore.feature.home

import androidx.compose.ui.geometry.Offset

/**
 * Ce qu'un défilement doit céder au repli du calendrier.
 *
 * **Un doigt qui part vers le haut replie d'abord, puis la page suit.** Le delta qui
 * déclenche le repli est consommé : sans cela la page se déplacerait pendant que la
 * hauteur du calendrier s'anime, et le contenu ferait un bond.
 *
 * Trois cas ne replient rien, et chacun pour sa raison :
 *
 * - **le calendrier est déjà replié** — il n'y a rien à fermer ;
 * - **le doigt descend** — on remonte dans la page, et refermer ce qu'on vient
 *   d'ouvrir à ce moment-là serait tout le contraire du geste ;
 * - **le geste est horizontal** — c'est le bandeau qui change de semaine.
 *
 * **Cette fonction ne dit pas *où* le geste a eu lieu**, et c'est le second défaut
 * rapporté à l'usage : la connexion était posée sur toute la page, calendrier compris,
 * et `onPreScroll` va du parent vers l'enfant. Défiler *dans* le mois déplié le
 * refermait donc au lieu de le faire défiler. La réponse n'est pas ici mais dans la
 * disposition — la connexion n'est plus un ancêtre du calendrier, seulement du contenu
 * qui le suit ([D103][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun collapsingDelta(expanded: Boolean, available: Offset): Offset =
    if (expanded && available.y < 0f) available else Offset.Zero
