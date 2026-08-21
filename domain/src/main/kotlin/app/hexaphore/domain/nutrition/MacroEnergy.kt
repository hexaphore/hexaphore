package app.hexaphore.domain.nutrition

import kotlin.math.abs

/**
 * Les facteurs d'Atwater, utilisés partout dans le projet.
 *
 * Les fibres à 2 kcal/g suivent le règlement UE 1169/2011, cohérent avec CIQUAL et
 * Open Food Facts. L'alcool (7 kcal/g) n'est pas modélisé en v1 : les boissons
 * alcoolisées sont saisies via leur fiche, dont les calories sont déjà justes.
 *
 * Ils vivent dans `nutrition` et non dans `goal`, où ils sont nés : ce sont des
 * facteurs de nutrition, et le calcul d'objectif n'est qu'un de leurs deux usages.
 * L'autre est ici.
 */
const val KCAL_PER_GRAM_PROTEIN = 4.0

const val KCAL_PER_GRAM_CARB = 4.0

const val KCAL_PER_GRAM_FAT = 9.0

const val KCAL_PER_GRAM_FIBER = 2.0

/**
 * Ce que les macros d'une ligne représentent en énergie, quand ça vaut d'être dit.
 *
 * Une **proposition** et jamais une valeur posée d'office : c'est l'utilisateur qui
 * décide de remplacer son énergie par ce calcul, et [docs/12][plan] insiste sur le
 * mot. Un écran qui écrirait tout seul dans un champ que quelqu'un vient de remplir
 * ferait exactement ce que `edited` existe pour empêcher.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
data class EnergyProposal(
    /** L'énergie calculée, en kcal, pour la quantité de la ligne. */
    val kcal: Double,
    /**
     * `true` quand les fibres n'étaient pas renseignées et n'ont donc rien apporté.
     *
     * La valeur est alors **minorée** d'au plus 2 kcal par gramme de fibres ignoré,
     * et l'écran doit le dire. Une valeur minorée qui s'annonce vaut mieux qu'une
     * ligne bloquée ; une valeur minorée qui se tait serait un chiffre inventé de
     * plus ([D83][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val withoutFiber: Boolean,
)

/**
 * L'énergie que ces macros représentent, ou `null` si l'une des trois exigées manque.
 *
 * **Les sucres n'y figurent pas** : ils sont inclus dans les glucides, et les compter
 * en plus doublerait une partie du total. C'est la même famille d'erreur que les
 * fibres distribuées deux fois ([D24][decisions]) — laquelle vient de l'autre côté :
 * les glucides de CIQUAL comme d'Open Food Facts sont déclarés **hors fibres**, si
 * bien qu'ici les additionner tels quels ne compte rien deux fois.
 *
 * Les fibres absentes valent zéro dans ce calcul **et seulement dans ce calcul** :
 * c'est ce que [energyProposal] entoure de son garde-fou, parce qu'un zéro de commodité
 * qui s'échapperait vaudrait affirmation.
 *
 * [decisions]: docs/11-decisions.md
 */
val NutrientValues.macroEnergy: Double?
    get() {
        val protein = protein
        val carbs = carbs
        val fat = fat
        return when {
            protein == null || carbs == null || fat == null -> null
            else ->
                KCAL_PER_GRAM_PROTEIN * protein +
                    KCAL_PER_GRAM_CARB * carbs +
                    KCAL_PER_GRAM_FAT * fat +
                    KCAL_PER_GRAM_FIBER * (fiber ?: 0.0)
        }
    }

/**
 * Le calcul à proposer pour ces valeurs, ou `null` s'il n'y a rien à proposer.
 *
 * Deux situations le justifient, et une troisième l'interdit :
 *
 * - **L'énergie manque.** La ligne n'est pas enregistrable, et c'est le cas que
 *   [docs/12][plan] décrit en premier.
 * - **L'énergie contredit les macros.** Corriger les protéines d'une ligne laisse
 *   l'énergie d'avant en place : présente, donc silencieuse, et fausse. C'est le
 *   défaut qui a motivé la demande, et ne proposer que sur une énergie absente
 *   l'aurait laissé passer entier.
 * - **Les fibres manquent et l'énergie est là.** Rien n'est proposé : l'écart observé
 *   peut n'être que les fibres qu'on ignore, et remplacer une valeur mesurée par un
 *   calcul minoré serait une régression. Quinze fiches de l'ANSES sont exactement dans
 *   ce cas.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
val NutrientValues.energyProposal: EnergyProposal?
    get() {
        val computed = macroEnergy ?: return null
        return EnergyProposal(kcal = computed, withoutFiber = fiber == null)
            .takeIf { proposable(computed) }
    }

/** Les trois situations, dans l'ordre où elles se posent. */
private fun NutrientValues.proposable(computed: Double): Boolean {
    val current = kcal
    return when {
        current == null -> true
        fiber == null -> false
        else -> significant(computed, current)
    }
}

/**
 * `true` quand l'écart mérite qu'on en parle.
 *
 * **Deux seuils, et il en faut deux.** Un seuil relatif seul ferait clignoter la
 * proposition sur les petites lignes — 3 kcal d'écart sur une tisane en font 30 % —
 * et un seuil absolu seul se tairait sur un plat de 2 000 kcal faux de 50. Chacun
 * couvre l'angle mort de l'autre, donc les deux doivent tomber.
 *
 * Un écart sous ces seuils n'est pas une erreur à corriger : c'est l'arrondi à
 * l'entier des six champs ([D52][decisions]), qui ne se rattrape pas et n'a pas à
 * l'être.
 *
 * [decisions]: docs/11-decisions.md
 */
private fun significant(computed: Double, current: Double): Boolean {
    val gap = abs(computed - current)
    return gap >= MIN_ENERGY_GAP_KCAL && gap > RELATIVE_ENERGY_GAP * current
}

/** En deçà, l'écart ne vaut pas un geste. Une énergie absente vaut zéro, donc passe. */
private const val MIN_ENERGY_GAP_KCAL = 10.0

/** Et il doit peser cette part de ce qui est affiché. */
private const val RELATIVE_ENERGY_GAP = 0.10
