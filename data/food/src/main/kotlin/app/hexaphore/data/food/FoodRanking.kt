package app.hexaphore.data.food

import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.food.SearchText

/**
 * L'ordre dans lequel les résultats se présentent.
 *
 * Sans lui, « pomme » renvoie « Pomme de terre à chair farineuse, crue » avant
 * « Pomme, chair et peau, crue » — ce que [docs/04][sources] signale comme le défaut
 * à éviter, parce qu'il rend la recherche inutilisable sur les mots les plus courants.
 *
 * BM25 n'est pas disponible : c'est une fonction de FTS5, que `minSdk 26` interdit
 * ([D49][decisions]). Son absence coûte peu ici. Sur 3 484 libellés courts, la
 * pondération par fréquence de terme départage mal — « pomme » apparaît une fois
 * dans chacun des deux candidats ci-dessus — alors que la **longueur du nom** et
 * l'**usage réel** les départagent nettement. C'était déjà le critère décisif quand
 * BM25 était prévu, et il devait de toute façon se calculer ici.
 *
 * Fonction pure, testée pour elle-même : c'est la seule partie de la recherche dont
 * la justesse ne se voit pas à l'œil sur un écran.
 *
 * [sources]: docs/04-sources-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
internal object FoodRanking {
    fun sort(foods: List<Food>, query: String): List<Food> {
        val normalised = SearchText.normalise(query)
        return foods.sortedWith(
            compareByDescending<Food> { score(it, normalised) }.thenBy { it.name },
        )
    }

    /** Visible pour être testée : c'est elle qui porte la règle, pas le tri. */
    fun score(food: Food, normalisedQuery: String): Double {
        val name = SearchText.normalise(food.name)
        return proximity(name, normalisedQuery) * brevity(name) * food.source.weight * familiarity(food)
    }

    /**
     * À quel point le nom *est* la requête, plutôt que de la contenir.
     *
     * Quatre paliers et non un score continu : « pomme » doit passer devant « pomme
     * de terre », et un écart continu laisserait la longueur du nom rattraper la
     * différence sur un libellé CIQUAL à rallonge.
     */
    private fun proximity(name: String, query: String): Double = when {
        name == query -> EXACT
        name.startsWith(query) -> PREFIX
        name.split(' ').any { it == query } -> WHOLE_WORD
        else -> SOMEWHERE
    }

    /**
     * Le nom court gagne.
     *
     * Un libellé CIQUAL décrit sa préparation — « Pomme de terre, chair et peau,
     * bouillie/cuite à l'eau, sans sel ajouté » — et l'aliment générique porte
     * toujours le nom le plus court de sa famille.
     */
    private fun brevity(name: String): Double = REFERENCE_LENGTH / (REFERENCE_LENGTH + name.length)

    /**
     * Ce que l'utilisateur a déjà mangé remonte.
     *
     * Pas proportionnel au nombre d'usages : un aliment consommé cent fois ne doit
     * pas enterrer un homonyme plus pertinent. Un seul palier suffit à faire remonter
     * le riz qu'on achète au-dessus des quatorze riz de la table.
     */
    private fun familiarity(food: Food): Double = if (food.useCount > 0) CONSUMED else 1.0

    /**
     * Le poids d'une provenance, tel que [docs/04][sources] le fixe.
     *
     * Le produit de marque est délibérément dévalorisé : « riz » doit tomber sur le
     * riz de la table, pas sur un paquet scanné il y a trois mois.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    private val FoodSource.weight: Double
        get() = when (this) {
            FoodSource.CUSTOM -> 1.3
            FoodSource.CIQUAL -> 1.0
            FoodSource.OFF -> 0.8
        }

    private const val EXACT = 4.0
    private const val PREFIX = 2.0
    private const val WHOLE_WORD = 1.5
    private const val SOMEWHERE = 1.0

    private const val CONSUMED = 1.5

    /** Longueur au-delà de laquelle un nom cesse d'être court. En caractères. */
    private const val REFERENCE_LENGTH = 30.0
}
