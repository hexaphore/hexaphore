package app.hexaphore.feature.entry

import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.DraftLine
import app.hexaphore.domain.diary.DraftLineId
import app.hexaphore.domain.diary.EntryDraft
import app.hexaphore.domain.diary.EntryId
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.Macros
import java.time.LocalDate

/**
 * Le brouillon, tel que les champs de l'écran le portent.
 *
 * **Du texte et non des nombres**, et c'est la seule raison d'exister de ce type.
 * Un champ qui rendrait un `Double` reformaterait « 12, » en « 12 » à la frappe
 * suivante, et il deviendrait impossible de saisir « 12,5 » : le point décimal
 * n'existerait jamais assez longtemps pour être suivi d'un chiffre.
 *
 * La conversion vers [EntryDraft] a donc lieu à chaque recomposition plutôt qu'à la
 * saisie. Elle est bon marché et rend un seul modèle de vérité — celui du domaine —
 * responsable de dire si l'enregistrement est possible.
 */
internal data class EntryForm(
    val dishId: DishId?,
    val date: LocalDate,
    val source: EntrySource,
    val lines: List<EntryFormLine>,
) {
    fun toDraft(): EntryDraft = EntryDraft(
        dishId = dishId,
        date = date,
        source = source,
        lines = lines.map { it.toDraftLine() },
    )

    fun update(id: DraftLineId, transform: (EntryFormLine) -> EntryFormLine): EntryForm =
        copy(lines = lines.map { if (it.id == id) transform(it) else it })

    companion object {
        fun of(draft: EntryDraft): EntryForm = EntryForm(
            dishId = draft.dishId,
            date = draft.date,
            source = draft.source,
            lines = draft.lines.map(EntryFormLine::of),
        )
    }
}

/**
 * Une ligne saisissable.
 *
 * [macros] indexe les six champs par [Macro] plutôt que de les nommer un par un :
 * l'écran les parcourt dans l'ordre angulaire commun à toute l'application, et
 * ajouter un septième compteur — s'il en arrivait un — ne demanderait pas de
 * réécrire ce type.
 *
 * **Un champ vide vaut inconnu, jamais zéro.** C'est la même règle que partout
 * ailleurs, appliquée à l'endroit où elle est le plus facile à trahir : il serait
 * tentant de lire un champ vide comme un « 0 » et d'éviter ainsi tout traitement du
 * cas nul. Le journal porterait alors des zéros que personne n'a saisis.
 */
internal data class EntryFormLine(
    val id: DraftLineId,
    val entryId: EntryId? = null,
    val name: String = "",
    val quantity: String = "",
    val unit: QuantityUnit = QuantityUnit.GRAM,
    val macros: Map<Macro, String> = emptyMap(),
    /** Les cinq macros hors calories sont repliées par défaut : elles sont facultatives. */
    val expanded: Boolean = false,
) {
    fun toDraftLine(): DraftLine = DraftLine(
        id = id,
        entryId = entryId,
        name = name,
        quantity = number(quantity),
        unit = unit,
        macros = number(macros[Macro.CALORIES].orEmpty())?.let { kcal ->
            Macros(
                kcal = kcal,
                protein = macroValue(Macro.PROTEIN),
                carbs = macroValue(Macro.CARBS),
                sugars = macroValue(Macro.SUGARS),
                fat = macroValue(Macro.FAT),
                fiber = macroValue(Macro.FIBER),
            )
        },
    )

    private fun macroValue(macro: Macro): Double? = number(macros[macro].orEmpty())

    companion object {
        fun of(line: DraftLine): EntryFormLine = EntryFormLine(
            id = line.id,
            entryId = line.entryId,
            name = line.name,
            quantity = line.quantity.asField(),
            unit = line.unit,
            macros = Macro.entries.associateWith { line.macros?.get(it).asField() },
            // Une ligne relue montre ses valeurs : les replier obligerait a deplier
            // chaque ligne pour verifier qu'on modifie la bonne.
            expanded = line.macros != null,
        )
    }
}

/**
 * Un nombre tel qu'un clavier français le produit.
 *
 * La virgule décimale est acceptée au même titre que le point. Le clavier numérique
 * d'Android affiche l'un ou l'autre selon la locale, et refuser la virgule rendrait
 * la saisie impossible sur un téléphone en français sans qu'aucun message ne dise
 * pourquoi.
 *
 * Une chaîne vide rend `null` : le champ n'a pas été renseigné, ce qui n'est pas
 * zéro.
 */
private fun number(text: String): Double? = text
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()

/**
 * Un nombre tel qu'on le remet dans un champ.
 *
 * Sans décimale quand il n'en a pas : « 150 » et non « 150.0 », qui donnerait à
 * chaque relecture d'un plat l'apparence d'une précision au dixième de gramme.
 */
private fun Double?.asField(): String = when {
    this == null -> ""
    this == toLong().toDouble() -> toLong().toString()
    else -> toString()
}
