package app.hexaphore.feature.entry

import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.nutrition.Macro

/**
 * Ce qu'une interaction peut changer sur une ligne.
 *
 * Un type fermé plutôt que cinq méthodes de `ViewModel` et cinq lambdas dans
 * [EntryActions]. Le gain n'est pas la brièveté : c'est qu'ajouter un champ — la
 * confiance d'une proposition en tranche 6, le marqueur « estimée » d'une valeur —
 * ajoute une variante que le `when` refuse de compiler tant qu'elle n'est pas
 * traitée. Cinq méthodes indépendantes auraient laissé la sixième s'ajouter en
 * silence, et l'oubli n'aurait été visible que sur l'appareil.
 */
internal sealed interface LineEdit {
    data class Name(val value: String) : LineEdit

    data class Quantity(val value: String) : LineEdit

    /** L'unité dans laquelle la quantité est exprimée. */
    data class Measurement(val value: QuantityUnit) : LineEdit

    data class MacroValue(val macro: Macro, val value: String) : LineEdit

    /** Déplie ou replie les cinq valeurs facultatives. */
    data object ToggleDetails : LineEdit
}

/**
 * Applique une modification à une ligne.
 *
 * Ici et non dans le `ViewModel` : c'est une transformation de la forme du
 * formulaire, elle se lit et se teste sans état ni coroutine.
 */
internal fun EntryFormLine.apply(edit: LineEdit): EntryFormLine = when (edit) {
    is LineEdit.Name -> copy(name = edit.value)
    // La quantite et l unite reecrivent les valeurs ; une valeur ecrite a la main
    // se marque et cesse de suivre.
    is LineEdit.Quantity -> remeasured(edit.value)
    is LineEdit.Measurement -> remeasured(quantity, edit.value)
    is LineEdit.MacroValue -> copy(macros = macros + (edit.macro to edit.value), edited = edited + edit.macro)
    LineEdit.ToggleDetails -> copy(expanded = !expanded)
}
