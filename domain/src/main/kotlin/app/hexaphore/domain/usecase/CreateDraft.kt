package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.DraftLine
import app.hexaphore.domain.diary.DraftLineId
import app.hexaphore.domain.diary.EntryDraft
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.food.Food
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

    /**
     * Un brouillon d'une ligne, préremplie depuis une fiche d'aliment.
     *
     * Le pendant du précédent, et la démonstration que la promesse tenait : la
     * recherche produit le même genre de brouillon que la saisie manuelle, et rien
     * en aval ne les distingue. Seule la source diffère, et elle n'est qu'une
     * pastille.
     */
    operator fun invoke(source: EntrySource, food: Food): EntryDraft = EntryDraft(
        date = clock.today(),
        source = source,
        lines = listOf(DraftLine.of(DraftLineId(ids.next()), food)),
    )

    /**
     * Un brouillon de plusieurs lignes, ce que produit une reconnaissance.
     *
     * **Une liste vide donne quand même une ligne**, et c'est ici que l'invariant se
     * tient : un brouillon sans aucune ligne n'aurait rien à afficher ni à supprimer,
     * et l'écran de validation n'a pas d'état pour ça. Le cas ne devrait pas se
     * présenter — une analyse sans ligne exploitable est une erreur, pas une
     * réussite — mais le tenir ici coûte une expression et évite d'y compter.
     */
    operator fun invoke(source: EntrySource, lines: List<DraftLine>): EntryDraft = EntryDraft(
        date = clock.today(),
        source = source,
        lines = lines.ifEmpty { listOf(line()) },
    )

    /** Une ligne vierge de plus. Sert de repli quand aucune fiche n'est disponible. */
    fun line(): DraftLine = DraftLine.blank(DraftLineId(ids.next()))

    /** Une ligne de plus, préremplie depuis une fiche — ce que produit « Ajouter un aliment ». */
    fun line(food: Food): DraftLine = DraftLine.of(DraftLineId(ids.next()), food)
}
