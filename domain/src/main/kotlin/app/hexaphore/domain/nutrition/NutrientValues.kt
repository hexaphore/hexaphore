package app.hexaphore.domain.nutrition

/**
 * Six teneurs dont **aucune** n'est obligatoire, énergie comprise.
 *
 * C'est ce qui la distingue de [Macros], où l'énergie est acquise parce qu'une ligne
 * de journal ne peut pas exister sans elle. Ici on décrit ce qu'on *sait* d'un
 * aliment ou d'une ligne en cours de saisie, et une fiche peut n'avoir aucune
 * énergie déterminée : 143 aliments de CIQUAL sont dans ce cas, et ce ne sont pas
 * des rebuts — la feta, les câpres, la canneberge, le pruneau cuit.
 *
 * Sans ce type, choisir la feta dans la recherche donnerait une ligne **vide** :
 * `Macros` n'accepte pas une énergie absente, donc les protéines et les lipides
 * connus partiraient avec elle. Ils sont conservés, et c'est l'énergie seule qui
 * reste à compléter.
 *
 * `null` signifie inconnu, jamais zéro. Toutes les opérations d'ici le respectent :
 * une valeur absente reste absente, elle ne devient pas zéro parce qu'on a
 * multiplié.
 *
 * @see docs/04-sources-de-donnees.md
 */
data class NutrientValues(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val sugars: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
) {
    /** Accès par macro, pour parcourir les six sans les énumérer à la main. */
    operator fun get(macro: Macro): Double? = when (macro) {
        Macro.CALORIES -> kcal
        Macro.PROTEIN -> protein
        Macro.CARBS -> carbs
        Macro.SUGARS -> sugars
        Macro.FAT -> fat
        Macro.FIBER -> fiber
    }

    /** Remplace une valeur, les cinq autres inchangées. */
    fun with(macro: Macro, value: Double?): NutrientValues = when (macro) {
        Macro.CALORIES -> copy(kcal = value)
        Macro.PROTEIN -> copy(protein = value)
        Macro.CARBS -> copy(carbs = value)
        Macro.SUGARS -> copy(sugars = value)
        Macro.FAT -> copy(fat = value)
        Macro.FIBER -> copy(fiber = value)
    }

    /** `true` quand rien n'est renseigné — le cas d'une ligne qu'on vient d'ajouter. */
    val empty: Boolean get() = Macro.entries.all { this[it] == null }

    /**
     * Ce que [grams] grammes apportent, si ces valeurs sont données pour 100 g.
     *
     * **Une valeur inconnue le reste.** C'est le point le plus facile à trahir de
     * toute la chaîne : `(fiber ?: 0.0) * facteur` compilerait, produirait des
     * nombres plausibles, et transformerait 70 aliments sans mesure de fibres en
     * 70 aliments sans fibres. Il n'y a pas de multiplication d'un inconnu.
     */
    fun per(grams: Double): NutrientValues {
        val factor = grams / REFERENCE_GRAMS
        return NutrientValues(
            kcal = kcal?.times(factor),
            protein = protein?.times(factor),
            carbs = carbs?.times(factor),
            sugars = sugars?.times(factor),
            fat = fat?.times(factor),
            fiber = fiber?.times(factor),
        )
    }

    /**
     * Ces valeurs sous la forme qu'une ligne de journal exige, ou `null`.
     *
     * `null` quand l'énergie manque : une ligne de journal sans énergie n'est pas
     * enregistrable, et lui en inventer une serait écrire un chiffre que personne
     * n'a donné.
     */
    fun toMacros(): Macros? = kcal?.let {
        Macros(kcal = it, protein = protein, carbs = carbs, sugars = sugars, fat = fat, fiber = fiber)
    }

    companion object {
        /** Les teneurs d'une fiche sont données pour cette masse. */
        const val REFERENCE_GRAMS = 100.0

        /** Le chemin inverse : ce qu'une ligne déjà enregistrée porte. */
        fun of(macros: Macros): NutrientValues = NutrientValues(
            kcal = macros.kcal,
            protein = macros.protein,
            carbs = macros.carbs,
            sugars = macros.sugars,
            fat = macros.fat,
            fiber = macros.fiber,
        )
    }
}
