package app.hexaphore.domain.resolution

/**
 * Ce que le résolveur fait d'un candidat, une fois sa confiance connue.
 *
 * Trois issues et non deux, parce que [docs/04][sources] en veut trois : remplir
 * sans rien demander, remplir **en le signalant** avec des alternatives, ou ne rien
 * remplir du tout. Les confondre coûterait dans les deux sens — fusionner les deux
 * premières ferait passer une correspondance douteuse pour une certitude, fusionner
 * les deux dernières ferait perdre un candidat souvent correct.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
enum class MatchVerdict {
    /** Confiance ≥ 0,75. La ligne se remplit seule. */
    AUTOMATIC,

    /**
     * Entre 0,40 et 0,75. Le meilleur candidat est retenu, **et la ligne est
     * signalée** : c'est là que les trois alternatives se proposent.
     */
    REVIEW,

    /** En dessous de 0,40. Aucune correspondance ; c'est le repli IA qui prend. */
    NONE,
}

/**
 * Le verdict que porte une confiance.
 *
 * Les deux seuils viennent de [docs/04][sources] et **n'ont été calibrés contre
 * rien** : ils datent de la conception, avant qu'un score existe. Ce qui les rend
 * exploitables aujourd'hui est l'échelle sur laquelle ils s'appliquent — voir
 * `FoodRanking.confidence`, qui la règle pour qu'un nom exact passe et qu'un simple
 * préfixe ne passe pas seul. De vraies reconnaissances diront s'il faut les bouger.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
fun verdictFor(confidence: Double): MatchVerdict = when {
    confidence >= AUTOMATIC_THRESHOLD -> MatchVerdict.AUTOMATIC
    confidence >= REVIEW_THRESHOLD -> MatchVerdict.REVIEW
    else -> MatchVerdict.NONE
}

private const val AUTOMATIC_THRESHOLD = 0.75
private const val REVIEW_THRESHOLD = 0.40
