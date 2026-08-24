package app.hexavore.data.backup

import app.hexavore.domain.diary.FavoriteComponent
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.NutrientValues
import java.time.Instant

/** Le catalogue local et les plats favoris. */
internal fun Food.toDto() = FoodDto(
    id = id.value,
    source = source.name,
    sourceRef = sourceRef,
    name = name,
    brand = brand,
    kcal = per100g.kcal,
    protein = per100g.protein,
    carbs = per100g.carbs,
    sugars = per100g.sugars,
    fat = per100g.fat,
    fiber = per100g.fiber,
    defaultServingG = defaultServingG,
    isLiquid = isLiquid,
    fetchedAt = fetchedAt?.toString(),
    lastUsedAt = lastUsedAt?.toString(),
    useCount = useCount,
    favorite = favorite,
)

internal fun FoodDto.toDomain() = Food(
    id = FoodId(id),
    source = FoodSource.entries.firstOrNull { it.name == source } ?: FoodSource.CUSTOM,
    sourceRef = sourceRef,
    name = name,
    brand = brand,
    per100g = NutrientValues(
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        sugars = sugars,
        fat = fat,
        fiber = fiber,
    ),
    defaultServingG = defaultServingG,
    isLiquid = isLiquid,
    fetchedAt = fetchedAt?.let(Instant::parse),
    lastUsedAt = lastUsedAt?.let(Instant::parse),
    useCount = useCount,
    favorite = favorite,
)

internal fun FavoriteDish.toDto() = FavoriteDto(
    id = id.value,
    name = name,
    useCount = useCount,
    components = components.map { it.toDto() },
)

internal fun FavoriteDto.toDomain() = FavoriteDish(
    id = FavoriteDishId(id),
    name = name,
    useCount = useCount,
    components = components.map { it.toDomain() },
)

internal fun FavoriteComponent.toDto() = ComponentDto(
    foodId = foodId?.value,
    name = name,
    quantity = quantity,
    unit = unit.code,
    grams = grams,
    kcal = values.kcal,
    protein = values.protein,
    carbs = values.carbs,
    sugars = values.sugars,
    fat = values.fat,
    fiber = values.fiber,
)

internal fun ComponentDto.toDomain() = FavoriteComponent(
    foodId = foodId?.let(::FoodId),
    name = name,
    quantity = quantity,
    // La portion nommee se reconstruit depuis ce qui a ete ecrit : la fiche a pu
    // disparaitre entre l'export et l'import, et le favori doit rester rejouable.
    unit = QuantityUnit.of(unit, grams, quantity),
    grams = grams,
    values = NutrientValues(
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        sugars = sugars,
        fat = fat,
        fiber = fiber,
    ),
)
