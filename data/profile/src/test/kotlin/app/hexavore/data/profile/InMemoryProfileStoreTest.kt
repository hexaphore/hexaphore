package app.hexavore.data.profile

import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemoryWeightLog

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
