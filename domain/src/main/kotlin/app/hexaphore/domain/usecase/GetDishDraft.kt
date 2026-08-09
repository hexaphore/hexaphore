package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.DraftLine
import app.hexaphore.domain.diary.DraftLineId
import app.hexaphore.domain.diary.EntryDraft
import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.nutrition.NutrientValues

/**
 * Rouvre un plat enregistré sous la forme que l'écran de validation manipule.
 *
 * **Les macros rendues sont celles qui ont été figées à l'enregistrement**, jamais
 * recalculées depuis une fiche d'aliment. Un fabricant qui reformule son produit ne
 * doit pas réécrire un journal vieux de six mois ([D05][decisions]) : rouvrir une
 * ligne pour corriger une quantité ne doit pas non plus être l'occasion de la
 * réécrire en silence.
 *
 * Chaque ligne conserve son [EntryId][app.hexaphore.domain.diary.EntryId] et reçoit
 * un identifiant de brouillon neuf. Les deux ne se confondent pas : le premier dit
 * quelle ligne du journal sera réécrite, le second sert à la désigner à l'écran.
 *
 * [decisions]: docs/11-decisions.md
 */
class GetDishDraft(private val diary: DiaryRepository, private val ids: IdGenerator) {
    /** @return `null` si le plat n'existe plus. */
    suspend operator fun invoke(dishId: DishId): EntryDraft? {
        val dish = diary.dish(dishId) ?: return null
        return EntryDraft(
            dishId = dish.id,
            date = dish.date,
            source = dish.source,
            lines = dish.entries.map { entry ->
                DraftLine(
                    id = DraftLineId(ids.next()),
                    entryId = entry.id,
                    foodId = entry.foodId,
                    name = entry.displayName,
                    quantity = entry.quantity,
                    // La portion nommee se reconstruit depuis ce qui a ete ecrit,
                    // sans relire la fiche : elle a pu etre supprimee depuis, et un
                    // plat de l'an dernier doit rester relisible tel qu'il a ete
                    // enregistre.
                    unit = QuantityUnit.of(entry.unit, entry.grams, entry.quantity),
                    values = NutrientValues.of(entry.macros),
                )
            },
        )
    }
}
