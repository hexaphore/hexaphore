package app.hexaphore.data.food

import app.hexaphore.core.database.ciqual.CiqualFoodRow
import app.hexaphore.core.database.ciqual.CiqualServingRow
import app.hexaphore.core.database.entity.FoodEntity
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodCategory
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodServing
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.food.SearchText
import app.hexaphore.domain.nutrition.NutrientValues
import java.time.Instant

/**
 * La correspondance entre les deux bases et le domaine.
 *
 * Deux origines, un seul type de sortie : un aliment de la table de l'ANSES et un
 * aliment personnel se présentent pareil à l'écran de recherche, et c'est ce qui lui
 * permet de n'avoir aucune branche sur la provenance.
 *
 * **Les huit valeurs traversent telles quelles.** Un `?: 0.0` sur l'une d'elles
 * serait la dernière occasion de perdre la distinction entre inconnu et zéro.
 */
internal fun FoodEntity.toDomain(servings: List<FoodServing> = emptyList(), category: FoodCategory? = null) = Food(
    id = FoodId(id),
    source = source.toFoodSource(),
    sourceRef = sourceRef,
    name = name,
    brand = brand,
    category = category,
    per100g = NutrientValues(
        kcal = kcal100,
        protein = protein100,
        carbs = carb100,
        sugars = sugar100,
        fat = fat100,
        fiber = fiber100,
    ),
    servings = servings,
    defaultServingG = defaultServingG,
    lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli),
    useCount = useCount,
    favorite = isFavorite,
)

/**
 * Une provenance inconnue retombe sur [FoodSource.CUSTOM].
 *
 * C'est la lecture la plus prudente : une base écrite par une version plus récente
 * ne doit pas rendre le catalogue illisible, et prendre une fiche inconnue pour un
 * aliment personnel ne lui attribue aucune autorité qu'elle n'a pas.
 */
private fun String.toFoodSource(): FoodSource = FoodSource.entries.firstOrNull { it.name == this } ?: FoodSource.CUSTOM

/**
 * Un rayon inconnu vaut **aucun rayon**.
 *
 * Même lecture prudente que ci-dessus, et le repli est ici sans conséquence : une
 * base écrite par une version plus récente peut nommer un rayon que celle-ci ne
 * connaît pas encore. Le lui inventer une correspondance ferait sortir l'aliment
 * sous une pastille au hasard ; ne rien lui donner le laisse trouvable par son nom.
 */
internal fun String?.toFoodCategory(): FoodCategory? = FoodCategory.entries.firstOrNull { it.name == this }

internal fun Food.toEntity(now: Long) = FoodEntity(
    id = id.value,
    source = source.name,
    sourceRef = sourceRef,
    name = name,
    // Calculee ici et stockee : la recherche compare une saisie normalisee a cette
    // colonne, et la recalculer a chaque frappe serait le seul endroit du parcours a
    // depenser le budget de 150 ms sans raison.
    nameSearch = SearchText.normalise(name),
    brand = brand,
    kcal100 = per100g.kcal,
    protein100 = per100g.protein,
    carb100 = per100g.carbs,
    sugar100 = per100g.sugars,
    fat100 = per100g.fat,
    fiber100 = per100g.fiber,
    // CIQUAL les publie, Open Food Facts aussi. La v1 ne les affiche pas ; les
    // perdre a la copie obligerait a tout reimporter le jour ou on les montre.
    saturatedFat100 = null,
    salt100 = null,
    defaultServingG = defaultServingG,
    lastUsedAt = lastUsedAt?.toEpochMilli(),
    useCount = useCount,
    isFavorite = favorite,
    createdAt = now,
    updatedAt = now,
)

/**
 * Un aliment de la table de l'ANSES, tel qu'il se présente **avant** d'être copié
 * dans le catalogue.
 *
 * Son identifiant est provisoire : il n'entre dans `food` que le jour où il est
 * réellement consommé. Copier les 3 484 lignes à l'installation gonflerait la base,
 * les sauvegardes et la recherche avec 99 % de contenu jamais utilisé.
 */
internal fun CiqualFoodRow.toDomain(id: FoodId, servings: List<CiqualServingRow>) = Food(
    id = id,
    source = FoodSource.CIQUAL,
    sourceRef = code,
    name = name,
    brand = null,
    category = category.toFoodCategory(),
    per100g = NutrientValues(
        kcal = kcal100,
        protein = protein100,
        carbs = carb100,
        sugars = sugar100,
        fat = fat100,
        fiber = fiber100,
    ),
    servings = servings.map { FoodServing(label = it.label, grams = it.grams, isDefault = it.isDefault) },
)

internal fun CiqualServingRow.toDomain() = FoodServing(label = label, grams = grams, isDefault = isDefault)
