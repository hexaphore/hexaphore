package app.hexaphore.domain.usecase

import app.hexaphore.domain.ai.Recognition
import app.hexaphore.domain.ai.RecognizedItem
import app.hexaphore.domain.diary.DraftLine
import app.hexaphore.domain.diary.EntryDraft
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.diary.Suggestion
import app.hexaphore.domain.resolution.convertToGrams

/**
 * Ce qu'une reconnaissance devient : un brouillon, ligne par ligne.
 *
 * **C'est la jonction que quatre livraisons attendaient.** Le contrat de
 * reconnaissance, la conversion des quantités, le score de décision et la recherche de
 * candidats existaient chacun avec ses tests et **aucun appelant** ; c'est ici qu'ils
 * se chaînent, et l'écran de validation reçoit un `EntryDraft` comme il en reçoit
 * depuis la tranche 2.
 *
 * L'ordre des étapes est celui de [docs/04][sources] : identifier l'aliment
 * **d'abord**, convertir la quantité **ensuite**. Il n'est pas interchangeable — la
 * portion nommée de la fiche l'emporte sur le forfait ([D73][decisions]), donc
 * convertir avant de savoir quelle fiche on vise reviendrait à appliquer le forfait à
 * tous les coups, et à se tromper d'un facteur six sur un bol de céréales.
 *
 * **Rien n'est écrit nulle part.** Résoudre est une lecture ; c'est l'enregistrement du
 * brouillon qui verse les fiches au catalogue, exactement comme pour la recherche.
 *
 * [sources]: docs/04-sources-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
class ResolveRecognition(private val resolve: ResolveFoodLabel, private val create: CreateDraft) {
    suspend operator fun invoke(recognition: Recognition, source: EntrySource): EntryDraft =
        create(source, recognition.items.map { resolveLine(it) })

    /**
     * Une ligne, telle que la résolution la rend.
     *
     * **Un libellé non résolu donne quand même une ligne**, avec son nom et sa
     * quantité mais sans valeurs. C'est la forme la plus utile de l'échec : l'écran de
     * validation dit déjà qu'une ligne sans énergie n'est pas enregistrable, et
     * « Ajouter un aliment » y est à un tap. L'écarter silencieusement ferait
     * disparaître un aliment que l'utilisateur a bel et bien mangé — et il ne saurait
     * pas lequel.
     *
     * Le repli IA groupé de [docs/04][sources] § étape 4 viendra remplir ces
     * lignes-là ; il a besoin d'un appel supplémentaire au fournisseur, donc d'une
     * décision sur ce qu'on accepte de payer, et c'est la livraison qui l'apporte qui
     * la prendra.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    private suspend fun resolveLine(item: RecognizedItem): DraftLine {
        val match = resolve(item.label)
        val converted = convertToGrams(item.quantity, item.unit, match.food)
        val line = match.food?.let(create::line) ?: create.line().copy(name = item.label)

        return line
            .measured(converted.grams, QuantityUnit.Gram)
            .copy(
                suggestion = Suggestion(
                    confidence = item.confidence,
                    verdict = match.verdict,
                    alternatives = match.alternatives,
                    estimated = converted.guessed,
                ),
            )
    }
}
