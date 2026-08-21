package app.hexaphore.domain.diary

import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.NutrientValues
import java.time.LocalDate

/** Le 15 mars 2026, la journée de référence de tous les tests du journal. */
internal val JOUR: LocalDate = LocalDate.of(2026, 3, 15)

/**
 * Une ligne complète, donc enregistrable.
 *
 * `fiber = null` par défaut sur aucune ligne : les tests qui veulent éprouver un
 * trou le demandent explicitement, pour qu'on voie dans le test lui-même ce qui est
 * censé manquer.
 */
internal fun ligne(
    id: String,
    nom: String = "Riz",
    quantite: Double? = 150.0,
    unite: QuantityUnit = QuantityUnit.Gram,
    entryId: EntryId? = null,
    foodId: FoodId? = null,
    kcal: Double? = 195.0,
    fibres: Double? = 1.2,
    fiche: Food? = null,
    corrigees: Set<Macro> = emptySet(),
) = DraftLine(
    id = DraftLineId(id),
    entryId = entryId,
    foodId = foodId ?: fiche?.id,
    food = fiche,
    name = nom,
    quantity = quantite,
    unit = unite,
    values = NutrientValues(kcal = kcal, protein = 4.0, carbs = 42.0, sugars = 0.2, fat = 0.5, fiber = fibres),
    edited = corrigees,
)

internal fun brouillon(
    vararg lignes: DraftLine,
    dishId: DishId? = null,
    source: EntrySource = EntrySource.MANUAL,
    date: LocalDate = JOUR,
    favoriteId: FavoriteDishId? = null,
) = EntryDraft(
    dishId = dishId,
    date = date,
    source = source,
    lines = lignes.toList(),
    favoriteId = favoriteId,
)
