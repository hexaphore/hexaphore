package app.hexaphore.data.diary

import app.hexaphore.core.database.dao.DishWithEntries
import app.hexaphore.core.database.entity.DishEntity
import app.hexaphore.core.database.entity.FoodEntryEntity
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.EntryId
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.FavoriteDishId
import app.hexaphore.domain.diary.FoodEntry
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.nutrition.Macros
import java.time.Instant
import java.time.LocalDate

/**
 * La correspondance entre les tables et le domaine.
 *
 * Elle existe pour qu'aucun type Room ne remonte jusqu'à un cas d'usage ni à un
 * écran. C'est le prix à payer pour que le jour où la source de données change, le
 * domaine ne bouge pas — et c'est exactement ce que la tranche 1 vient de
 * démontrer en remplaçant une implémentation en mémoire par celle-ci.
 *
 * @see docs/06-architecture.md
 */
fun DishWithEntries.toDomain(): Dish {
    val id = DishId(dish.id)
    return Dish(
        id = id,
        date = LocalDate.parse(dish.date),
        source = dish.source.toEntrySource(),
        loggedAt = Instant.ofEpochMilli(dish.loggedAt),
        entries = entries.map { it.toDomain(id) },
        favoriteId = dish.favoriteId?.let(::FavoriteDishId),
    )
}

private fun FoodEntryEntity.toDomain(dishId: DishId) = FoodEntry(
    id = EntryId(id),
    dishId = dishId,
    foodId = foodId?.let(::FoodId),
    displayName = displayName,
    quantity = quantity,
    unit = unit,
    grams = grams,
    // Les cinq valeurs nullables traversent telles quelles : un `?: 0.0` ici
    // ferait disparaitre la distinction entre inconnu et zero, silencieusement et
    // pour de bon.
    macros = Macros(
        kcal = kcal,
        protein = proteinG,
        carbs = carbG,
        sugars = sugarG,
        fat = fatG,
        fiber = fiberG,
    ),
)

/**
 * La source d'un plat, telle qu'elle est stockée.
 *
 * Une valeur inconnue retombe sur [EntrySource.MANUAL] plutôt que de faire planter
 * la lecture : une base écrite par une version plus récente ne doit pas rendre le
 * journal illisible. C'est la lecture la plus prudente — attribuer une saisie à la
 * main n'invente aucune provenance automatique.
 */
private fun String.toEntrySource(): EntrySource =
    EntrySource.entries.firstOrNull { it.name == this } ?: EntrySource.MANUAL

/**
 * Le chemin inverse : du domaine vers les tables.
 *
 * [now] sert de date de création **et** de modification. Le DAO écrase la première
 * par celle qui existe déjà, le cas échéant : c'est lui qui sait si le plat est
 * nouveau, et la correspondance n'a aucune raison de poser la question.
 */
fun Dish.toEntity(now: Long) = DishEntity(
    id = id.value,
    date = date.toString(),
    source = source.name,
    loggedAt = loggedAt.toEpochMilli(),
    favoriteId = favoriteId?.value,
    createdAt = now,
    updatedAt = now,
)

fun FoodEntry.toEntity(now: Long) = FoodEntryEntity(
    id = id.value,
    dishId = dishId.value,
    foodId = foodId?.value,
    displayName = displayName,
    quantity = quantity,
    unit = unit,
    grams = grams,
    kcal = macros.kcal,
    // Les cinq valeurs nullables descendent telles quelles. Un `?: 0.0` ici serait
    // la derniere occasion de perdre la distinction entre inconnu et zero, et la
    // seule ou plus rien ensuite ne pourrait la retrouver.
    proteinG = macros.protein,
    carbG = macros.carbs,
    sugarG = macros.sugars,
    fatG = macros.fat,
    fiberG = macros.fiber,
    createdAt = now,
    updatedAt = now,
)
