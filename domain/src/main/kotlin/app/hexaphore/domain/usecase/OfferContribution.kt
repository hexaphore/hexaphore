package app.hexaphore.domain.usecase

import app.hexaphore.domain.food.ContributionOutcome
import app.hexaphore.domain.food.ContributionSettings
import app.hexaphore.domain.food.FoodContribution
import app.hexaphore.domain.food.FoodContributionTarget
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodLookup
import kotlinx.coroutines.flow.first

/**
 * Ce qu'on peut proposer de reverser à Open Food Facts, une fois la fiche écrite.
 *
 * **Le moment est le sujet.** La proposition n'arrive pas dans une liste ni derrière
 * un menu, mais **juste après avoir créé une fiche pour un produit que le scan n'a pas
 * trouvé** — c'est-à-dire à l'instant précis où le travail vient d'être fait et où il
 * a le plus de valeur pour quelqu'un d'autre. C'est le parcours que [D70][decisions]
 * décrit : hors d'Europe le produit absent est le cas courant, et chaque saisie reste
 * sinon sur un seul téléphone.
 *
 * **La fiche est relue plutôt que reconstruite depuis le formulaire.** Ce qui part est
 * ce qui a été enregistré, pas ce qu'on croyait avoir saisi — et c'est aussi ce qui
 * garantit que la source vaut bien `CUSTOM`, condition que seule l'écriture connaît.
 *
 * [decisions]: docs/11-decisions.md
 */
class OfferContribution(private val foods: FoodLookup, private val settings: ContributionSettings) {
    /**
     * @return la contribution à proposer, ou `null` s'il n'y a rien à proposer.
     *
     * **Trois raisons de ne rien proposer, et aucune n'est une erreur** : la fiche
     * n'est pas contribuable — pas de code-barres, une valeur estimée, un autre
     * catalogue —, aucun compte n'est configuré, ou la fiche n'a pas pu être relue.
     * Dans les trois cas l'écran se referme comme avant, sans rien dire : un message
     * qui expliquerait pourquoi on ne propose pas encombrerait le moment où l'on vient
     * de finir une saisie.
     */
    suspend operator fun invoke(id: FoodId): FoodContribution? {
        if (!settings.observe().first().open) return null
        return foods.byId(id)?.let(FoodContribution::of)
    }
}

/**
 * L'envoi lui-même, une fois que l'utilisateur a dit oui.
 *
 * **Séparé de la proposition**, parce que ce sont deux moments et deux décisions :
 * l'un regarde s'il y a quelque chose à offrir, l'autre agit sur le monde extérieur.
 * Les fondre ferait passer une écriture sortante pour une lecture.
 *
 * Le compte est relu ici et non porté depuis la proposition : entre les deux, il a pu
 * être effacé dans les réglages, et un envoi avec un compte périmé se ferait refuser
 * sans qu'on sache pourquoi.
 */
class SendContribution(private val target: FoodContributionTarget, private val settings: ContributionSettings) {
    suspend operator fun invoke(contribution: FoodContribution): ContributionOutcome {
        val account = settings.observe().first().account
        return when {
            account == null || !account.usable -> ContributionOutcome.Rejected
            else -> target.contribute(contribution, account)
        }
    }
}
