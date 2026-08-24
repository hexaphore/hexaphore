package app.hexavore.data.diary

import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.domain.diary.FavoriteDishes

/**
 * Le même contrat, joué sur le faux.
 *
 * Il vit dans ce module plutôt que dans `:core:testing` pour que les deux
 * implémentations soient **compilées et exécutées côte à côte**, sous la même
 * commande et dans le même rapport.
 */
class InMemoryFavoriteDishesTest : FavoriteDishContract() {
    override fun favorites(): FavoriteDishes = InMemoryFavoriteDishes()
}
