package app.hexaphore.data.profile

import app.hexaphore.core.testing.InMemoryGoals
import app.hexaphore.core.testing.InMemoryProfiles
import app.hexaphore.core.testing.InMemoryWeightLog

/**
 * Le même contrat, joué sur les faux.
 *
 * Il vit dans ce module et non dans `:core:testing` pour la raison qui fait tout
 * l'intérêt du dispositif : les deux implémentations sont **compilées et exécutées
 * côte à côte**, sous la même commande et dans le même rapport. Une divergence n'est
 * plus une chose qu'on découvre sur l'appareil, c'est une ligne rouge à côté d'une
 * verte.
 */
class InMemoryProfileStoreTest : ProfileStoreContract() {
    override fun store() = ProfileStoreView(
        profiles = InMemoryProfiles(),
        weights = InMemoryWeightLog(),
        goals = InMemoryGoals(),
    )
}
