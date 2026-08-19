package app.hexaphore.domain.diary

import app.hexaphore.domain.food.Food
import app.hexaphore.domain.resolution.MatchVerdict

/**
 * Ce qu'un modèle a proposé pour une ligne, et ce que la résolution en a fait.
 *
 * **Deux incertitudes et non une**, parce qu'elles se trompent séparément : le modèle
 * peut être sûr d'avoir vu du riz complet ([confidence] à 0,95) alors que le catalogue
 * n'a que du riz blanc ([verdict] à [MatchVerdict.REVIEW]), et l'inverse arrive tout
 * autant. Les moyenner rendrait un chiffre qui ne se rapporte à rien et ferait
 * disparaître le seul cas qui compte : celui où l'une des deux doute.
 *
 * [docs/02][parcours] les demande à l'écran de validation — la confiance ligne par
 * ligne, jusqu'à trois alternatives sur une correspondance faible, et un marqueur sur
 * les chiffres qui sont une estimation. C'est le prix du gain de temps : l'IA fait
 * gagner du temps sans faire autorité.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
data class Suggestion(
    /** La certitude du modèle sur son identification et sa quantité, dans `[0, 1]`. */
    val confidence: Float,
    /** Ce que la confrontation au catalogue a donné. */
    val verdict: MatchVerdict,
    /**
     * Les autres fiches qu'on aurait acceptées, jusqu'à trois.
     *
     * Vide hors de la zone de relecture, et c'est le résolveur qui le décide : une
     * correspondance automatique n'a pas de solution de rechange à offrir, et une
     * ligne non résolue n'en a aucune.
     */
    val alternatives: List<Food>,
    /**
     * `true` quand la quantité en grammes vient d'un forfait plutôt que d'une donnée.
     *
     * [docs/04][sources] l'exige : *« toute conversion appuyée sur un défaut plutôt
     * que sur une donnée réelle doit être signalée dans l'écran de validation »*. Sans
     * cette marque, « 1 bol » converti au forfait s'afficherait avec la même autorité
     * qu'une portion mesurée par la fiche.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    val estimated: Boolean,
)
