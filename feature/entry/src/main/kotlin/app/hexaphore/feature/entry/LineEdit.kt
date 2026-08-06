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
    is LineEdit.Quantity -> copy(quantity = edit.value)
    is LineEdit.Measurement -> copy(unit = edit.value)
    is LineEdit.MacroValue -> copy(macros = macros + (edit.macro to edit.value))
    LineEdit.ToggleDetails -> copy(expanded = !expanded)
}

/**
 * Ce qu'un champ numérique laisse entrer.
 *
 * Des chiffres et **au plus un** séparateur décimal, virgule ou point. Le clavier
 * décimal d'Android laisse passer plus que ça selon les fabricants, et « 12,5,3 »
 * ne se convertit en aucun nombre : la ligne deviendrait inenregistrable sans que
 * rien ne dise pourquoi.
 *
 * Le champ **refuse** la frappe plutôt que de l'accepter puis de la nettoyer. Une
 * frappe refusée ne change rien, ni à l'écran ni dans le brouillon ; une frappe
 * nettoyée obligerait à réécrire le texte affiché, donc à repositionner le curseur
 * — exactement ce que [DraftTextField][app.hexaphore.feature.entry] évite.
 */
internal fun String.isNumberField(): Boolean =
    all { it.isDigit() || it in DECIMAL_SEPARATORS } && count { it in DECIMAL_SEPARATORS } <= 1

private const val DECIMAL_SEPARATORS = ",."
