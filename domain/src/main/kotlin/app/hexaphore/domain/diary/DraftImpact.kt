package app.hexaphore.domain.diary

import app.hexaphore.domain.nutrition.Macro

/**
 * Ce que la saisie en cours coûtera à la journée.
 *
 * Seulement des calories, et c'est voulu : au moment de valider, la question est
 * « est-ce que ça rentre ». Les six compteurs sont deux écrans plus loin, sur
 * l'accueil, et les répéter ici ferait de la validation un second récapitulatif.
 *
 * Aucun drapeau de fiabilité non plus, contrairement à [MacroTotal][totals] : les
 * calories ne sont jamais inconnues — une fiche sans énergie n'est pas exploitable,
 * et le parcours de saisie oblige à en fournir une. Un drapeau qui vaudrait
 * toujours `true` serait une branche morte que chaque écran devrait quand même
 * traiter.
 *
 * [totals]: app.hexaphore.domain.nutrition.MacroTotal
 *
 * @property remainingKcal ce qui restera une fois le brouillon enregistré. Négatif
 *   en cas de dépassement — c'est une donnée, pas un jugement.
 */
data class DraftImpact(val draftKcal: Double, val remainingKcal: Double)

/**
 * L'impact d'un brouillon sur cette journée.
 *
 * Le plat en cours de modification est **retiré du total avant d'ajouter le
 * brouillon**. Sans cela, corriger un plat de 600 kcal en 700 afficherait un
 * restant amputé de 1 300 : l'écran compterait deux fois un plat qui n'existe
 * qu'une, et le chiffre le plus visible de la validation serait faux précisément
 * quand on relit pour corriger.
 */
fun DaySummary.impactOf(draft: EntryDraft): DraftImpact {
    val consumed = totals[Macro.CALORIES].value
    val replaced = dishes.firstOrNull { it.dish.id == draft.dishId }?.totals?.get(Macro.CALORIES)?.value ?: 0.0

    return DraftImpact(
        draftKcal = draft.kcal,
        remainingKcal = goal.kcal - (consumed - replaced + draft.kcal),
    )
}
