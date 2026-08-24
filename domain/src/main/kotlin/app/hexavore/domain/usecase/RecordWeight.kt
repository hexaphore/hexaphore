package app.hexavore.domain.usecase

import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.time.Clock

/**
 * Noter une pesée.
 *
 * **Deux refus, et deux seulement.** Une date à venir — [docs/02][parcours] interdit la
 * saisie dans le futur, ici comme dans le journal alimentaire — et un poids nul ou
 * négatif, qui n'est pas une mesure. Aucune borne haute ni basse : la fourchette du
 * corps humain va du nourrisson au record du monde, et un seuil inventé refuserait un
 * jour la pesée de quelqu'un de réel au motif qu'elle sort de ce qu'on avait imaginé.
 *
 * Un refus n'est pas une erreur à afficher : l'écran désactive son bouton bien avant.
 * C'est le filet, pour que la règle vive dans le domaine et non dans un composant.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
class RecordWeight(private val weights: WeightLog, private val clock: Clock) {
    /** @return `false` si la pesée est refusée, et rien n'est alors écrit. */
    suspend operator fun invoke(entry: WeightEntry): Boolean {
        if (entry.date.isAfter(clock.today()) || entry.weightKg <= 0.0) return false
        weights.record(entry)
        return true
    }
}
