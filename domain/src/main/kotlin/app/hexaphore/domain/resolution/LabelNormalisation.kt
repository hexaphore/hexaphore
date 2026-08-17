package app.hexaphore.domain.resolution

import app.hexaphore.domain.food.SearchText

/**
 * Le libellé du modèle, mis dans la forme sous laquelle l'index se laisse interroger.
 *
 * Minuscules, accents et ponctuation sont le travail de [SearchText.normalise], et
 * il n'est pas refait ici : c'est **la même** normalisation que celle des deux index,
 * et une seconde règle divergerait de la première le jour où l'une apprend les
 * ligatures et l'autre non ([D49][decisions], [D73][decisions]).
 *
 * **Ce que cette fonction ajoute est le retrait des articles de tête**, et c'est une
 * nécessité mécanique et non un embellissement. Les deux recherches sont
 * conjonctives : le catalogue local compare une sous-chaîne entière — `name_search
 * LIKE '%du pain%'` — et la table de l'ANSES exige que **tous** les termes du `MATCH`
 * soient présents. « du pain » ne rend donc rien nulle part, alors que « pain » rend
 * les deux cents lignes attendues. Un article gardé n'est pas du bruit dans le
 * classement : c'est une réponse vide.
 *
 * **Les pluriels, eux, ne sont pas traités ici**, et c'est la différence qui compte.
 * Voir [depluralise] : ils sont l'objet d'un second essai, pas de la normalisation.
 *
 * Un libellé qui ne serait fait que d'articles rend la chaîne vide, ce que les deux
 * implémentations de la recherche traitent déjà comme « rien à chercher ».
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md § Résolution, étape 1
 */
fun normaliseLabel(raw: String): String = SearchText
    .normalise(raw)
    .split(' ')
    .filter { it.isNotEmpty() }
    .dropWhile { it in LEADING_ARTICLES }
    .joinToString(" ")

/**
 * Le même libellé au singulier naïf, pour le **second** essai et lui seul.
 *
 * L'ordre est la règle, et il est mesuré : l'index de l'ANSES **garde ses pluriels**
 * — 32 % de ses 3 484 libellés en portent un, 6 % commencent par un — et le `LIKE`
 * du catalogue local compare une sous-chaîne entière. Dépluraliser systématiquement
 * ferait donc **perdre** « haricots verts », que la requête brute trouvait. On
 * interroge avec le libellé tel qu'il vient, et on ne retente au singulier que si la
 * première recherche n'a rien rendu ([D74][decisions]).
 *
 * **C'est aussi ce qui rend la naïveté de la règle sans conséquence**, et c'est
 * l'argument le plus solide des deux. « Jus » ne devient pas « ju », « pois » devient
 * « poi » et « eaux » devient « eal » — mais aucun de ces trois libellés n'atteint
 * jamais cette fonction, parce que tous les trois rendent des résultats à la première
 * requête. Une règle approximative placée derrière une garde qui ne s'ouvre qu'en cas
 * d'échec ne peut dégrader que ce qui était déjà vide.
 *
 * Un mot de trois lettres ou moins n'est pas touché : ce qu'il en resterait serait un
 * préfixe si court qu'il ramènerait n'importe quoi.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md § Résolution, étape 1
 */
fun depluralise(normalisedLabel: String): String = normalisedLabel.split(' ').joinToString(" ") { it.singular() }

/**
 * Les trois règles de [docs/04][sources], appliquées dans l'ordre où elles se
 * recouvrent : `-aux` avant `-x`, sans quoi « chevaux » deviendrait « chevau ».
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
private fun String.singular(): String = when {
    length <= SHORTEST_STEM -> this
    endsWith(PLURAL_AUX) -> dropLast(PLURAL_AUX.length) + SINGULAR_AL
    last() in PLURAL_ENDINGS -> dropLast(1)
    else -> this
}

/**
 * Ce qu'on retire en tête, sous la forme normalisée — où l'apostrophe est devenue une
 * coupure de mot, « d'orange » un « d » suivi d'« orange ».
 *
 * Seulement en tête : « pain de mie » garde son « de », qui y désigne quelque chose.
 */
private val LEADING_ARTICLES = setOf("de", "du", "des", "d", "le", "la", "les", "l", "un", "une")

private val PLURAL_ENDINGS = setOf('s', 'x')

private const val PLURAL_AUX = "aux"
private const val SINGULAR_AL = "al"

/** En deçà, la troncature laisse un préfixe qui ne désigne plus rien. En caractères. */
private const val SHORTEST_STEM = 3
