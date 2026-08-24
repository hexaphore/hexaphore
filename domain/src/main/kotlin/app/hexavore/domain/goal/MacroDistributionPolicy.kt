package app.hexavore.domain.goal

import app.hexavore.domain.nutrition.KCAL_PER_GRAM_CARB
import app.hexavore.domain.nutrition.KCAL_PER_GRAM_FAT
import app.hexavore.domain.nutrition.KCAL_PER_GRAM_FIBER
import app.hexavore.domain.nutrition.KCAL_PER_GRAM_PROTEIN
import kotlin.math.roundToInt

/** Ce que l'utilisateur cherche à faire. Trois cartes à l'onboarding ([docs/02][parcours]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
enum class GoalStrategy(
    /** Protéines en g par kg de poids **cible** ([docs/03][calculs]).
     *
     * [calculs]: docs/03-nutrition-calculs.md
     */
    val proteinPerKg: Double,
    /** Lipides en fraction des calories. */
    val fatRatio: Double,
) {
    /** Préserve la masse maigre quand l'apport énergétique est bas. */
    LOSE(proteinPerKg = 1.8, fatRatio = 0.25),

    /** Couvre largement les besoins d'un adulte actif. */
    MAINTAIN(proteinPerKg = 1.6, fatRatio = 0.30),

    /** Optimum pour la synthèse protéique musculaire. */
    GAIN(proteinPerKg = 2.0, fatRatio = 0.30),
}

/**
 * Les six chiffres, et ce que le calcul a dû concéder.
 *
 * [carbsBelowMinimum] n'est pas une erreur : c'est un objectif calorique trop bas pour
 * dégager 100 g de glucides même après rééquilibrage, et l'écran doit le dire. Le
 * taire laisserait l'utilisateur avec une répartition qu'il ne pourrait pas tenir sans
 * comprendre pourquoi.
 */
data class MacroDistribution(val goal: DailyGoal, val carbsBelowMinimum: Boolean)

/**
 * La répartition des six compteurs, à partir d'un budget calorique.
 *
 * **L'ordre n'est pas indifférent** : protéines d'abord — le besoin le plus contraint —
 * puis lipides, puis **fibres**, et les glucides en solde. Les fibres passent avant les
 * glucides parce qu'elles consomment de l'énergie : 2 kcal/g au sens du règlement
 * UE 1169/2011, et elles sont comptées séparément des glucides dans CIQUAL comme dans
 * Open Food Facts. Les calculer après le solde distribuerait deux fois les mêmes
 * calories — sur l'exemple de référence, 70 kcal ([D24][decisions]).
 *
 * **Les grammes sont entiers, et le solde se calcule sur eux.** Personne ne compte les
 * demi-grammes de lipides, et une décimale affichée est une précision que la formule ne
 * tient pas. Calculer les glucides sur des valeurs non arrondies puis arrondir donnerait
 * un contrôle de cohérence qui ne retombe pas sur ce qui est **affiché** ([D52][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/03-nutrition-calculs.md
 */
object MacroDistributionPolicy {
    /**
     * @param currentWeightKg pour le plancher de lipides, qui est physiologique.
     * @param targetWeightKg pour les protéines — sinon une personne en surpoids
     *   important se voit attribuer un objectif protéique irréaliste.
     */
    fun distribute(
        kcal: Double,
        strategy: GoalStrategy,
        currentWeightKg: Double,
        targetWeightKg: Double,
    ): MacroDistribution {
        val fiber = fiber(kcal)
        var protein = protein(strategy, targetWeightKg)
        var fat = fat(kcal, strategy, currentWeightKg)
        var carbs = solde(kcal, protein, fat, fiber)

        if (carbs < MINIMUM_CARBS) {
            // Les lipides cedent les premiers, jusqu'a leur plancher : c'est la
            // marge la moins couteuse. Les proteines ensuite, jusqu'a 1,4 g/kg.
            // Les fibres, jamais -- leur plancher est un besoin, pas une variable
            // d'ajustement.
            fat = fatFloor(currentWeightKg)
            carbs = solde(kcal, protein, fat, fiber)
        }
        if (carbs < MINIMUM_CARBS) {
            protein = (REBALANCED_PROTEIN_PER_KG * targetWeightKg).roundToInt().toDouble()
            carbs = solde(kcal, protein, fat, fiber)
        }

        return MacroDistribution(
            goal = DailyGoal(
                kcal = kcal,
                protein = protein,
                carbs = carbs,
                sugars = sugars(kcal),
                fat = fat,
                fiber = fiber,
            ),
            carbsBelowMinimum = carbs < MINIMUM_CARBS,
        )
    }

    /** Bornée à 0,8 g/kg — l'apport de sécurité — et à 2,5, au-delà desquels rien n'est démontré. */
    private fun protein(strategy: GoalStrategy, targetWeightKg: Double): Double =
        (strategy.proteinPerKg.coerceIn(MIN_PROTEIN_PER_KG, MAX_PROTEIN_PER_KG) * targetWeightKg)
            .roundToInt()
            .toDouble()

    /**
     * Le pourcentage des calories, **sauf** si le plancher physiologique est plus haut.
     *
     * En dessous de 0,6 g/kg, l'absorption des vitamines liposolubles et la production
     * hormonale sont compromises. Ce plancher prime sur le pourcentage, et c'est le
     * seul endroit du calcul où une règle de santé l'emporte sur une répartition.
     */
    private fun fat(kcal: Double, strategy: GoalStrategy, currentWeightKg: Double): Double =
        maxOf(kcal * strategy.fatRatio / KCAL_PER_GRAM_FAT, fatFloor(currentWeightKg)).roundToInt().toDouble()

    private fun fatFloor(currentWeightKg: Double): Double = MIN_FAT_PER_KG * currentWeightKg

    /** 14 g pour 1 000 kcal, repère de l'Institute of Medicine, entre 25 et 50 g. */
    private fun fiber(kcal: Double): Double = (FIBER_PER_1000_KCAL * kcal / KCAL_PER_MILLE)
        .coerceIn(MIN_FIBER_G, MAX_FIBER_G)
        .roundToInt()
        .toDouble()

    /**
     * Plafond et non objectif : ≤ 10 % des calories, repère de l'OMS pour les sucres
     * **libres**.
     *
     * Faute de donnée fiable sur les sucres libres, le seuil est appliqué aux sucres
     * **totaux**, ce qui est plus strict. L'infobulle du compteur le dit, parce qu'un
     * plafond plus sévère que la référence qu'il cite doit s'annoncer.
     */
    private fun sugars(kcal: Double): Double = (kcal * MAX_SUGAR_RATIO / KCAL_PER_GRAM_CARB).roundToInt().toDouble()

    private fun solde(kcal: Double, protein: Double, fat: Double, fiber: Double): Double = (
        (kcal - KCAL_PER_GRAM_PROTEIN * protein - KCAL_PER_GRAM_FAT * fat - KCAL_PER_GRAM_FIBER * fiber) /
            KCAL_PER_GRAM_CARB
        ).roundToInt().toDouble()

    private const val MIN_PROTEIN_PER_KG = 0.8
    private const val MAX_PROTEIN_PER_KG = 2.5
    private const val REBALANCED_PROTEIN_PER_KG = 1.4

    private const val MIN_FAT_PER_KG = 0.6

    private const val FIBER_PER_1000_KCAL = 14.0
    private const val KCAL_PER_MILLE = 1_000.0
    private const val MIN_FIBER_G = 25.0
    private const val MAX_FIBER_G = 50.0

    private const val MAX_SUGAR_RATIO = 0.10
    private const val MINIMUM_CARBS = 100.0
}
