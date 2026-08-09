package app.hexaphore.domain.diary

import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.nutrition.NutrientValues
import java.time.LocalDate

/**
 * Identifiant d'une ligne de brouillon.
 *
 * Distinct de [EntryId] à dessein : une ligne existe à l'écran avant d'exister dans
 * le journal, et pendant ce temps la liste doit quand même pouvoir la désigner —
 * pour la modifier, pour la supprimer, pour ne pas la recomposer entièrement à
 * chaque frappe. Réutiliser [EntryId] obligerait à en inventer un pour du contenu
 * qui n'est pas enregistré, donc à ne plus pouvoir distinguer les deux états.
 */
@JvmInline
value class DraftLineId(val value: String)

/**
 * Une ligne de l'écran de validation.
 *
 * Ses champs sont **facultatifs**, et ce n'est pas un relâchement : une ligne en
 * cours de saisie est incomplète par nature, et représenter « pas encore renseigné »
 * par un zéro serait la même erreur que confondre `null` avec zéro sur une valeur
 * nutritionnelle. [complete] dit quand la ligne est enregistrable.
 *
 * [values] porte les six valeurs **indépendamment les unes des autres**, énergie
 * comprise. Une seule d'entre elles peut manquer sans emporter les autres, et c'est
 * ce qui permet à un aliment CIQUAL sans énergie déterminée — la feta, les câpres —
 * d'arriver ici avec ses protéines et ses lipides, l'énergie seule restant à
 * compléter.
 *
 * Aucune source ici. Elle appartient au plat, et c'est ce qui permet à cet écran
 * d'accepter indifféremment une ligne tapée à la main, un résultat de recherche, un
 * produit scanné ou une proposition de modèle.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
data class DraftLine(
    val id: DraftLineId,
    /**
     * L'entrée de journal dont cette ligne provient, si elle en provient d'une.
     *
     * `null` pour une ligne ajoutée à l'écran. La conserver permet à une
     * modification de réécrire la même ligne plutôt que d'en créer une seconde,
     * et donc de préserver sa date de création.
     */
    val entryId: EntryId? = null,
    /** La fiche d'où vient cette ligne. Provenance seulement — voir [FoodEntry.foodId]. */
    val foodId: FoodId? = null,
    /**
     * La fiche elle-même, quand elle vient d'être choisie et n'est peut-être pas
     * encore au catalogue.
     *
     * `null` sur une ligne tapée à la main, et `null` aussi sur une ligne rouverte :
     * dans ce second cas [foodId] suffit, puisque la fiche est forcément déjà au
     * catalogue — elle y est entrée quand le plat a été enregistré. La porter ici
     * évite d'aller la relire pour l'écrire.
     */
    val food: Food? = null,
    val name: String = "",
    val quantity: Double? = null,
    val unit: QuantityUnit = QuantityUnit.Gram,
    val values: NutrientValues = NutrientValues(),
    /**
     * Les portions que la fiche d'origine propose, en plus des grammes et des
     * millilitres.
     *
     * Vide pour une ligne tapée à la main : il n'y a pas de fiche derrière, donc
     * rien qui puisse dire ce que pèse une tranche.
     */
    val servings: List<QuantityUnit.Serving> = emptyList(),
) {
    /** La quantité convertie en grammes, base de tout calcul. */
    val grams: Double? get() = quantity?.times(unit.gramsPerUnit)

    /**
     * `true` quand la ligne peut être enregistrée.
     *
     * Un nom, une quantité strictement positive, une énergie. Les cinq autres
     * valeurs restent facultatives : un produit mal renseigné doit pouvoir entrer
     * dans le journal avec ses trous visibles, plutôt que de ne pas y entrer.
     */
    val complete: Boolean
        get() = name.isNotBlank() && (quantity ?: 0.0) > 0.0 && values.kcal != null

    /** `true` quand rien n'a été saisi — le cas d'une ligne qu'on vient d'ajouter. */
    val blank: Boolean
        get() = name.isBlank() && quantity == null && values.empty

    /** Les unités proposées pour cette ligne : les deux universelles, puis ses portions. */
    val units: List<QuantityUnit> get() = QuantityUnit.universal + servings

    companion object {
        /** Une ligne vierge, telle que la produit « Ajouter une ligne ». */
        fun blank(id: DraftLineId): DraftLine = DraftLine(id = id)

        /**
         * Une ligne préremplie depuis une fiche d'aliment.
         *
         * **C'est ici que le recalcul à la quantité a enfin une référence**, ce que
         * [D42][decisions] attendait : les valeurs sont celles de la fiche pour
         * 100 g, ramenées à la quantité choisie. Une valeur inconnue le reste — il
         * n'y a pas de multiplication d'un inconnu.
         *
         * La quantité proposée est celle de la fiche : sa portion par défaut si elle
         * en a une, sinon son poids de service, sinon 100 g. Personne ne pèse une
         * pomme.
         *
         * [decisions]: docs/11-decisions.md
         */
        fun of(id: DraftLineId, food: Food): DraftLine {
            val servings = food.servings.map { QuantityUnit.Serving(it.label, it.grams) }
            val unit = food.defaultServing?.let { QuantityUnit.Serving(it.label, it.grams) }
            val quantity = if (unit != null) 1.0 else food.defaultServingG ?: NutrientValues.REFERENCE_GRAMS

            return DraftLine(
                id = id,
                foodId = food.id,
                food = food,
                name = food.name,
                quantity = quantity,
                unit = unit ?: QuantityUnit.Gram,
                values = food.per100g.per(quantity * (unit?.gramsPerUnit ?: 1.0)),
                servings = servings,
            )
        }
    }
}

/**
 * Ce que l'écran de validation manipule : *n* lignes et le plat qu'elles formeront.
 *
 * **C'est le point de convergence des quatre modes de saisie**, et la raison d'être
 * de ce type. Une recherche produit un brouillon d'une ligne, un scan aussi, une
 * photo en produit cinq ; l'écran ne fait pas la différence, parce qu'il n'a jamais
 * accès à autre chose que cet objet. Écrire cet écran « pour la saisie manuelle »
 * obligerait à le généraliser trois fois — c'est le piège central du projet, et il
 * est signalé dans [docs/12][plan] depuis la conception.
 *
 * [source] est portée par le brouillon et non par ses lignes : on ne photographie
 * pas une assiette aliment par aliment. Elle est **ignorée** lors d'une
 * modification, où l'origine du plat existant fait foi ([D32][decisions]).
 *
 * [plan]: docs/12-plan-de-developpement.md
 * [decisions]: docs/11-decisions.md
 */
data class EntryDraft(
    /** `null` pour une nouvelle saisie ; renseigné quand on modifie un plat existant. */
    val dishId: DishId? = null,
    val date: LocalDate,
    val source: EntrySource,
    val lines: List<DraftLine>,
) {
    /** `true` quand ce brouillon modifie un plat déjà enregistré. */
    val editing: Boolean get() = dishId != null

    /**
     * `true` quand l'enregistrement est possible.
     *
     * Toutes les lignes, et pas seulement une : une ligne à moitié remplie qu'on
     * enregistrerait silencieusement serait une donnée inventée.
     */
    val saveable: Boolean get() = lines.isNotEmpty() && lines.all { it.complete }

    /** L'énergie de la saisie, sur les seules lignes qui en portent une. */
    val kcal: Double get() = lines.sumOf { it.values.kcal ?: 0.0 }

    /**
     * Les fiches à verser au catalogue à l'enregistrement.
     *
     * Vide pour un plat tapé à la main, et vide aussi pour un plat rouvert : ses
     * fiches y sont déjà entrées le jour où il a été enregistré.
     */
    val foods: List<Food> get() = lines.mapNotNull { it.food }.distinctBy { it.id }
}

/**
 * Les lignes du brouillon, telles qu'elles entreront dans le journal.
 *
 * Les identifiants manquants sont tirés de [ids] plutôt que du hasard ambiant :
 * c'est ce qui rend `LogDish` vérifiable autrement qu'en relisant la base.
 *
 * @throws IllegalStateException si une ligne est incomplète. L'appelant vérifie
 *   [EntryDraft.saveable] avant : une ligne à moitié saisie n'a pas de conversion
 *   raisonnable, et en inventer une écrirait un chiffre que personne n'a donné.
 */
fun EntryDraft.toEntries(dishId: DishId, ids: IdGenerator): List<FoodEntry> = lines.map { line ->
    FoodEntry(
        id = line.entryId ?: EntryId(ids.next()),
        dishId = dishId,
        foodId = line.foodId,
        displayName = line.name.trim(),
        quantity = checkNotNull(line.quantity) { "Ligne sans quantite : ${line.name}" },
        unit = line.unit.code,
        grams = checkNotNull(line.grams) { "Ligne sans quantite : ${line.name}" },
        // Les cinq valeurs facultatives traversent telles quelles ; seule l'energie
        // est exigee, et son absence est une erreur de l'appelant, pas un zero.
        macros = checkNotNull(line.values.toMacros()) { "Ligne sans energie : ${line.name}" },
    )
}
