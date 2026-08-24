package app.hexavore.data.diary

import app.hexavore.core.testing.InMemoryDiaryRepository

/**
 * Le même contrat, joué sur le faux.
 *
 * Il vit dans ce module plutôt que dans `:core:testing` pour que les deux
 * implémentations soient **compilées et exécutées côte à côte**, sous la même
 * commande et dans le même rapport.
 *
 * `diary` et `citations` sont **le même objet**, et c'est ce qui rend le faux
 * honnête : le compte se dérive des plats que le journal contient, il n'a pas de
 * réserve à lui qu'un test pourrait poser à la main ([D71][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class InMemoryDiaryRepositoryTest : DiaryContract() {
    override fun open(): OpenJournal {
        val journal = InMemoryDiaryRepository()
        // Rien a faire : le faux n'a pas de catalogue, donc pas de cle etrangere a
        // satisfaire. L'asymetrie est assumee et ecrite en D71.
        return OpenJournal(diary = journal, citations = journal, givenFood = {})
    }
}
