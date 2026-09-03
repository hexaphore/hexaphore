package app.hexavore.domain.diary

import app.hexavore.domain.food.FoodServing
import app.hexavore.domain.profile.UnitSystem

/**
 * L'unité dans laquelle une quantité se saisit.
 *
 * Un type fermé et non une énumération, parce que les portions nommées ne sont pas
 * des cas connus à la compilation : « 1 pomme moyenne » pèse 150 g **d'après une
 * fiche**, et la liste des portions dépend de l'aliment choisi. Une énumération
 * aurait obligé à porter le poids ailleurs, dans un champ parallèle que rien
 * n'obligerait à remplir.
 *
 * C'est ce que [D42][decisions] avait reporté à cette tranche, et pour la bonne
 * raison : sans fiche, il aurait fallu demander à l'utilisateur ce que pèse une
 * tranche, c'est-à-dire exactement le travail qu'on veut lui épargner.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md
 */
sealed interface QuantityUnit {
    /** Ce qui est écrit dans `food_entry.unit`, et affiché tel quel. */
    val code: String

    /** Combien de grammes pèse une unité. */
    val gramsPerUnit: Double

    /** Des grammes. */
    data object Gram : QuantityUnit {
        override val code: String = "g"
        override val gramsPerUnit: Double = 1.0
    }

    /**
     * Des millilitres.
     *
     * Un millilitre vaut un gramme, faute de mieux : c'est la densité par défaut de
     * [docs/04][sources], et la seule dont on dispose — CIQUAL ne publie pas de
     * densité, et rien ne la calcule avant le résolveur de la tranche 6. L'écart est
     * réel — un litre de lait pèse 1,03 kg — et il est isolé ici pour qu'il n'y ait
     * qu'un endroit à corriger.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    data object Millilitre : QuantityUnit {
        override val code: String = "ml"
        override val gramsPerUnit: Double = DEFAULT_DENSITY
    }

    /**
     * Une once, celle des étiquettes américaines.
     *
     * **Une unité de saisie de plus, et non une conversion d'affichage.** La ligne
     * garde ce qui a été tapé et le poids d'une unité voyage avec elle, exactement
     * comme « 1 tranche = 33 g » : ce que l'utilisateur a écrit est ce qui est
     * enregistré, et relire un plat ne fait dériver aucun chiffre.
     */
    data object Ounce : QuantityUnit {
        override val code: String = "oz"
        override val gramsPerUnit: Double = GRAMS_PER_OUNCE
    }

    /**
     * Une once liquide, ramenée en grammes par la même densité que le millilitre.
     *
     * L'écart est le même et il est isolé au même endroit : un litre de lait pèse
     * 1,03 kg, et le jour où une source publie des densités, il n'y a qu'une ligne à
     * corriger de chaque côté.
     */
    data object FluidOunce : QuantityUnit {
        override val code: String = "fl oz"
        override val gramsPerUnit: Double = MILLILITRES_PER_FLUID_OUNCE * DEFAULT_DENSITY
    }

    /**
     * Une portion nommée, avec le poids que sa fiche lui donne.
     *
     * Le poids voyage **avec** l'unité plutôt que d'être relu dans la fiche au
     * moment du calcul. Sans cela, rouvrir un plat de l'an dernier demanderait de
     * retrouver une fiche peut-être supprimée pour savoir ce que pesait « 1 tranche »
     * ce jour-là — et un journal est un registre d'événements.
     */
    data class Serving(override val code: String, override val gramsPerUnit: Double) : QuantityUnit

    companion object {
        /**
         * Les deux unités qu'un aliment propose toujours, dans le système choisi.
         *
         * **Une paire et non les quatre.** Proposer grammes, millilitres, onces et
         * onces liquides ensemble ferait une liste où l'on choisit son système à
         * chaque ligne, alors que c'est un réglage. Ce qui reste saisissable malgré
         * tout est ce que la ligne porte déjà — voir `DraftLine.units` : un plat noté
         * en grammes se relit en grammes, même après avoir basculé.
         */
        fun universal(system: UnitSystem): List<QuantityUnit> = when (system) {
            UnitSystem.METRIC -> listOf(Gram, Millilitre)
            UnitSystem.IMPERIAL -> listOf(Ounce, FluidOunce)
        }

        /**
         * L'unité correspondant à ce qui a été stocké.
         *
         * Un code inconnu n'est pas une erreur : c'est une portion nommée, et son
         * poids se retrouve en divisant les grammes enregistrés par la quantité.
         * C'est exact parce que c'est ainsi qu'il a été écrit, et c'est ce qui rend
         * une ligne relisible sans sa fiche.
         *
         * Une quantité nulle ou négative ne permet pas cette division ; la ligne
         * retombe alors sur des grammes, qui est la lecture la plus prudente — elle
         * n'invente aucun poids de portion.
         */
        fun of(code: String, grams: Double, quantity: Double): QuantityUnit = when {
            code == Gram.code -> Gram
            code == Millilitre.code -> Millilitre
            code == Ounce.code -> Ounce
            code == FluidOunce.code -> FluidOunce
            quantity > 0.0 -> Serving(code, grams / quantity)
            else -> Gram
        }

        /** L'unité que propose une portion de fiche. */
        fun of(serving: FoodServing): QuantityUnit = Serving(serving.label, serving.grams)
    }
}

/** Densité par défaut, en g/ml. */
private const val DEFAULT_DENSITY = 1.0

/** L'once avoirdupois, définition légale et non arrondie. */
private const val GRAMS_PER_OUNCE = 28.349523125

/** L'once liquide **américaine** : celle du Royaume-Uni vaut 28,4 ml, et ce n'est pas la même. */
private const val MILLILITRES_PER_FLUID_OUNCE = 29.5735295625
