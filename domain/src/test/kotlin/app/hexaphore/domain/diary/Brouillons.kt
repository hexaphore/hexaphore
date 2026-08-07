package app.hexaphore.domain.diary

import app.hexaphore.domain.nutrition.Macros
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
    unite: QuantityUnit = QuantityUnit.GRAM,
    entryId: EntryId? = null,
    kcal: Double? = 195.0,
    fibres: Double? = 1.2,
) = DraftLine(
    id = DraftLineId(id),
    entryId = entryId,
    name = nom,
    quantity = quantite,
    unit = unite,
    macros = kcal?.let {
        Macros(kcal = it, protein = 4.0, carbs = 42.0, sugars = 0.2, fat = 0.5, fiber = fibres)
    },
)

internal fun brouillon(
    vararg lignes: DraftLine,
    dishId: DishId? = null,
    source: EntrySource = EntrySource.MANUAL,
    date: LocalDate = JOUR,
) = EntryDraft(dishId = dishId, date = date, source = source, lines = lignes.toList())
