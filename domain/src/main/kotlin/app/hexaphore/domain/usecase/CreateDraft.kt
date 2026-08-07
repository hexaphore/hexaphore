package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.DraftLine
import app.hexaphore.domain.diary.DraftLineId
import app.hexaphore.domain.diary.EntryDraft
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.time.Clock

/**
 * Fabrique un brouillon vierge et ses lignes.
 *
 * Deux dépendances non déterministes — l'horloge et le générateur d'identifiants —
 * qui ne servent qu'à ça. Les rassembler ici évite qu'un ViewModel les porte pour
 * en faire deux appels, et donne à la journée par défaut un seul endroit où être
 * décidée : celle de l'horloge, jamais `LocalDate.now()`.
 *
 * [source] est un paramètre et non une constante : c'est ce qui permettra à la
 * recherche, au scan et à l'IA de produire le même genre de brouillon sans que rien
 * ne change ici.
 */
class CreateDraft(private val clock: Clock, private val ids: IdGenerator) {
    /** Un brouillon d'une seule ligne vide, daté d'aujourd'hui. */
    operator fun invoke(source: EntrySource): EntryDraft = EntryDraft(
        date = clock.today(),
        source = source,
        lines = listOf(line()),
    )

    /** Une ligne vierge de plus, telle que la produit « Ajouter une ligne ». */
    fun line(): DraftLine = DraftLine.blank(DraftLineId(ids.next()))
}
