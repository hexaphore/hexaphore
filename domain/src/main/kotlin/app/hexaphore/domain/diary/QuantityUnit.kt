package app.hexaphore.domain.diary

/**
 * Les unités dans lesquelles une quantité se saisit.
 *
 * Deux seulement, et c'est ce que la saisie à la main permet d'honnête : les
 * portions nommées — « 1 tranche », « 1 verre » — appartiennent à une fiche
 * d'aliment, et il n'y en a aucune avant la tranche 3. Les proposer maintenant
 * demanderait à l'utilisateur de définir lui-même ce que pèse une tranche, ce qui
 * est exactement le travail qu'on veut lui épargner.
 *
 * @see docs/04-sources-de-donnees.md
 */
enum class QuantityUnit(
    /** Ce qui est écrit dans `food_entry.unit`, et affiché tel quel. */
    val code: String,
    /**
     * Combien de grammes pèse une unité.
     *
     * Un millilitre vaut un gramme, faute de mieux : c'est la densité par défaut
     * de [docs/04][sources], et la seule dont on dispose tant qu'aucune fiche
     * d'aliment ne porte la sienne. L'écart est réel — un litre de lait pèse
     * 1,03 kg — mais il ne fausse aucun calcul à ce stade, puisque les valeurs
     * nutritionnelles sont saisies pour la quantité et non déduites d'un poids.
     *
     * Isolé ici pour qu'il n'y ait **qu'un** endroit à corriger le jour où la
     * densité arrive avec la table `food`.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    val gramsPerUnit: Double,
) {
    GRAM("g", 1.0),
    MILLILITRE("ml", DEFAULT_DENSITY),

    ;

    companion object {
        /**
         * L'unité correspondant à un code stocké.
         *
         * Une valeur inconnue retombe sur [GRAM] plutôt que de faire échouer la
         * lecture : une base écrite par une version plus récente — qui connaîtra
         * les portions nommées — ne doit pas rendre le journal illisible.
         */
        fun fromCode(code: String): QuantityUnit = entries.firstOrNull { it.code == code } ?: GRAM
    }
}

/** Densité par défaut, en g/ml. */
private const val DEFAULT_DENSITY = 1.0
