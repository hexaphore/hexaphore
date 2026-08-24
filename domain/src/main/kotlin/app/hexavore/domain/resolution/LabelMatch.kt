package app.hexavore.domain.resolution

import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodRanking

/**
 * Ce qu'un libellé du modèle est devenu, une fois les candidats pesés.
 *
 * Un enregistrement plat et non une hiérarchie scellée, alors que les trois issues
 * appellent bien trois gestes différents : `MatchVerdict` **est déjà** cette
 * énumération ([D74][decisions]). Un `sealed interface` à côté en ferait une seconde,
 * et deux hiérarchies pour le même fait finissent par ne plus être d'accord — c'est
 * le raisonnement du score unique, appliqué au verdict.
 *
 * [decisions]: docs/11-decisions.md
 */
data class LabelMatch(
    val verdict: MatchVerdict,
    /**
     * Le candidat retenu. **`null` exactement quand [verdict] vaut
     * [MatchVerdict.NONE]** : en dessous de 0,40, [docs/04][sources] ne retient
     * personne et renvoie la ligne au repli IA.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    val food: Food?,
    /**
     * La confiance du meilleur candidat, **y compris quand il est écarté**.
     *
     * Zéro quand la recherche n'a rien rendu du tout. Les deux cas mènent au même
     * verdict et n'ont pas à être distingués par l'appelant ; le chiffre, lui, dit
     * s'il s'en est fallu de peu, ce qu'un jour de calibrage voudra lire.
     */
    val confidence: Double,
    /**
     * Les alternatives, **en zone de relecture et nulle part ailleurs**.
     *
     * [docs/04][sources] les attache à la zone 0,40 – 0,75 : une correspondance
     * automatique n'a pas à proposer de solution de rechange, et une ligne non
     * résolue n'en a aucune à offrir. L'écran de validation reste le lieu où l'on
     * cherche autre chose à la main, dans les deux cas.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    val alternatives: List<Food>,
)

/**
 * Le meilleur candidat, son verdict, et ce que ce verdict autorise.
 *
 * **Le classement est refait ici, sur la confiance**, alors que la recherche en rend
 * déjà un. Ce n'est pas une défiance envers le port : c'est que son contrat ne promet
 * pas *cet* ordre-là, et que ses deux implémentations ne l'ont effectivement pas — la
 * vraie trie par `FoodRanking`, le faux par usage puis longueur du nom. S'appuyer sur
 * l'ordre reçu ferait donc du résolveur une règle qui change avec l'implémentation,
 * la forme exacte de défaut que [D53][decisions] proscrit. Le tri est stable, donc
 * l'ordre du port départage encore les ex æquo.
 *
 * **Les alternatives sont filtrées par le même seuil que la décision**, et non par un
 * second écrit à côté : une alternative est un candidat qu'on accepterait comme
 * correspondance, donc un candidat dont le verdict n'est pas [MatchVerdict.NONE].
 * C'est ce qui garde 0,40 dans un seul fichier, et ce qui fait qu'un jour de
 * calibrage n'aura qu'un chiffre à bouger. La contrepartie est assumée : la liste est
 * parfois vide, là où trois lignes à 0,15 auraient rempli l'écran de bruit.
 *
 * @param normalisedQuery **la requête qui a servi à trouver [candidates]**, et non le
 *   libellé d'origine. Peser un candidat contre autre chose que ce qui l'a ramené
 *   donnerait une confiance qui ne se rapporte à rien.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md § Résolution, étapes 2 et 3
 */
fun matchFor(candidates: List<Food>, normalisedQuery: String): LabelMatch {
    val ranked = candidates.sortedByDescending { FoodRanking.confidence(it, normalisedQuery) }
    val best = ranked.firstOrNull() ?: return NOTHING_FOUND

    val confidence = FoodRanking.confidence(best, normalisedQuery)
    val verdict = verdictFor(confidence)
    return LabelMatch(
        verdict = verdict,
        food = best.takeIf { verdict != MatchVerdict.NONE },
        confidence = confidence,
        alternatives = if (verdict == MatchVerdict.REVIEW) ranked.alternativesTo(normalisedQuery) else emptyList(),
    )
}

/** Les suivants du classement que l'on accepterait aussi, dans la limite de trois. */
private fun List<Food>.alternativesTo(normalisedQuery: String): List<Food> = drop(1)
    .filter { verdictFor(FoodRanking.confidence(it, normalisedQuery)) != MatchVerdict.NONE }
    .take(MAX_ALTERNATIVES)

/** Une recherche sans résultat, dont la confiance est nulle faute de quoi que ce soit à peser. */
private val NOTHING_FOUND = LabelMatch(MatchVerdict.NONE, food = null, confidence = 0.0, alternatives = emptyList())

/** « jusqu'à 3 alternatives », dit docs/04. */
private const val MAX_ALTERNATIVES = 3
