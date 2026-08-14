package app.hexaphore.domain.resolution

/**
 * Ce que pèse une quantité estimée, et si l'application a dû deviner pour le dire.
 *
 * [guessed] n'est pas un détail d'implémentation : [docs/04][sources] exige que
 * *« toute conversion appuyée sur un défaut plutôt que sur une donnée réelle soit
 * signalée dans l'écran de validation »*. Sans ce drapeau, « 1 bol » et « 250 g »
 * s'afficheraient avec la même autorité, alors que l'un est mesuré et l'autre
 * inventé.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
data class ConvertedQuantity(val grams: Double, val guessed: Boolean)
