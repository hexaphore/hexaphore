package app.hexaphore.tooling.ciqual

/** Ce qu'on demande à raccourcir : un code, et le libellé publié par l'ANSES. */
internal data class LongLabel(val code: String, val name: String)

/**
 * Qui sait raccourcir un libellé. Une interface pour que la règle se teste sans réseau.
 *
 * Le contrat est **strict sur le rattachement et lâche sur le reste** : les titres
 * rendus sont indexés par code, et un code qu'on n'a pas demandé est ignoré plutôt
 * que de faire échouer un lot de cinquante. C'est la règle de [D83][decisions]
 * appliquée ici — un modèle qui reformule ou qui invente une clé produit un résultat
 * qu'on ne peut pas rattacher, et on l'écarte au lieu de le deviner.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun interface ShortNamer {
    /** @return les titres rendus, par code. Les codes absents seront redemandés. */
    fun shorten(labels: List<LongLabel>): Map<String, String>
}

/**
 * La génération, telle qu'elle se déroule — sans rien savoir du réseau.
 *
 * **Reprenable par construction.** Ce qui est déjà dans le fichier n'est jamais
 * redemandé, donc une passe coupée au trentième lot reprend au trentième. C'est la
 * seule propriété qui rende soixante-dix requêtes supportables : sans elle, une
 * coupure de réseau à la fin fait tout repayer.
 *
 * **Ce qui sort du contrat est écarté, pas corrigé.** Un titre vide, trop long, ou
 * plus long que le libellé qu'il remplace n'entre pas dans le fichier : il sera
 * redemandé à la passe suivante. Les réparer ici les ferait entrer sans que personne
 * ne les ait vus, et [ShortNamesCsv] les refuserait de toute façon à l'import — mais
 * plus tard, et sur une ligne que le modèle n'aurait plus le moyen de reprendre.
 */
internal class ShortNames(private val namer: ShortNamer, private val batchSize: Int = BATCH_SIZE) {
    /**
     * @param labels tous les libellés de la table.
     * @param known ce que le fichier porte déjà.
     * @param onBatch reçoit **tout** ce qui est acquis après chaque lot, pour que
     *   l'appelant réécrive le fichier entier. Lui passer le seul lot l'obligerait à
     *   tenir un cumul de son côté, et deux comptes de la même chose divergent.
     */
    fun generate(
        labels: List<LongLabel>,
        known: List<CiqualShortName>,
        onBatch: (List<CiqualShortName>) -> Unit = {},
    ): List<CiqualShortName> {
        val settled = known.associateBy { it.code }.toMutableMap()

        pending(labels, known).chunked(batchSize).forEach { batch ->
            namer
                .shorten(batch)
                .mapNotNull { (code, title) -> accept(batch, code, title) }
                .forEach { settled[it.code] = it }
            onBatch(settled.values.toList())
        }
        return settled.values.toList()
    }

    /** Combien de libellés restent à demander, avant de dépenser quoi que ce soit. */
    fun pending(labels: List<LongLabel>, known: List<CiqualShortName>): List<LongLabel> {
        val settled = known.mapTo(mutableSetOf()) { it.code }
        return labels.filter { it.code !in settled && worthShortening(it) }
    }

    /**
     * Le titre, s'il tient le contrat. `null` sinon, et il sera redemandé.
     *
     * Le rattachement se fait par **code** et non par libellé : c'est ce qui permet
     * de demander cinquante titres d'un coup sans qu'une reformulation en place un
     * sur la mauvaise fiche.
     */
    private fun accept(batch: List<LongLabel>, code: String, title: String): CiqualShortName? {
        val asked = batch.firstOrNull { it.code == code } ?: return null
        val trimmed = title.trim()
        val valid = trimmed.isNotBlank() &&
            trimmed.length <= ShortNamesCsv.MAX_LENGTH &&
            trimmed.length < asked.name.length
        return CiqualShortName(code, trimmed).takeIf { valid }
    }

    private fun worthShortening(label: LongLabel) = label.name.length > ShortNamesCsv.WORTH_SHORTENING

    internal companion object {
        /**
         * Cinquante libellés par requête.
         *
         * Un compromis, et les deux bords coûtent. Un lot par fiche paierait la
         * consigne 3 484 fois ; un lot de mille rendrait une réponse si longue
         * qu'une coupure en perdrait beaucoup — et le modèle dérive sur la fin d'une
         * liste interminable.
         */
        const val BATCH_SIZE = 50
    }
}
