package app.hexaphore.domain.food

/**
 * Ce qu'une fiche doit être, en plus d'appartenir à un rayon.
 *
 * Deux qualités et non deux catégories de plus : elles ne se combinent pas de la
 * même façon, et les ranger dans le même ensemble aurait fait de « Favori + Fruits »
 * la réunion des favoris **et** des fruits, alors que c'est l'intersection qu'on
 * demande.
 */
enum class FoodTrait {
    /** Créé par l'utilisateur. Lui seul se modifie et se supprime. */
    PERSONAL,

    /** Épinglé. */
    FAVORITE,
    ;

    internal fun holds(food: Food): Boolean = when (this) {
        PERSONAL -> food.source == FoodSource.CUSTOM
        FAVORITE -> food.favorite
    }
}

/**
 * Ce que le bandeau de pastilles retient, et la règle qui le rend lisible.
 *
 * **Deux familles, deux combinaisons.** Les catégories entre elles se cumulent en
 * **OU** — « Fruits + Légumes » montre les deux, parce que sélectionner deux rayons
 * pour n'obtenir que leur intersection ne rendrait jamais rien : aucun aliment n'est
 * à la fois un fruit et un légume. Les qualités, elles, se posent en **ET**
 * par-dessus : « Favori + Fruits » montre les fruits épinglés, et « Favori + Mon
 * aliment » les fiches personnelles épinglées.
 *
 * Rien à l'écran ne dit qu'il y a deux familles ; c'est le bandeau qui doit le
 * montrer, en les séparant visuellement ([D54][decisions]). Cette classe se contente
 * d'être la règle, et de l'être **une seule fois** : la recherche, les récents et les
 * favoris passent tous par [matches], donc aucun des trois ne peut obéir à une
 * variante.
 *
 * [decisions]: docs/11-decisions.md
 */
data class FoodFilter(val categories: Set<FoodCategory> = emptySet(), val traits: Set<FoodTrait> = emptySet()) {
    /** Aucune pastille : tout passe, et l'écran retrouve son comportement d'avant. */
    val isEmpty: Boolean get() = categories.isEmpty() && traits.isEmpty()

    /**
     * Une fiche sans catégorie est retenue **tant qu'aucun rayon n'est demandé**.
     *
     * C'est ce qui fait qu'un aliment personnel, une huile ou une soupe restent
     * trouvables par leur nom et par « Mon aliment », sans jamais apparaître sous un
     * rayon auquel ils n'appartiennent pas.
     */
    fun matches(food: Food): Boolean =
        (categories.isEmpty() || food.category in categories) && traits.all { it.holds(food) }

    fun toggle(category: FoodCategory): FoodFilter = copy(categories = categories.flip(category))

    fun toggle(trait: FoodTrait): FoodFilter = copy(traits = traits.flip(trait))

    companion object {
        val NONE = FoodFilter()
    }
}

private fun <T> Set<T>.flip(value: T): Set<T> = if (value in this) this - value else this + value
