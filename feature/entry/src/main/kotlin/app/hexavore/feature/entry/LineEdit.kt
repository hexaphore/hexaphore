package app.hexavore.feature.entry

import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.food.Food
import app.hexavore.domain.nutrition.Macro

/**
 * Ce qu'une interaction peut changer sur une ligne.
 *
 * Un type fermé plutôt qu'une méthode de `ViewModel` et une lambda par geste dans
 * [EntryActions]. Le gain n'est pas la brièveté : c'est qu'ajouter un champ ajoute une
 * variante que le `when` refuse de compiler tant qu'elle n'est pas traitée. Des
 * méthodes indépendantes auraient laissé la suivante s'ajouter en silence, et l'oubli
 * n'aurait été visible que sur l'appareil.
 *
 * **Plus de pliage.** Les six valeurs sont toujours visibles : cette application sert
 * à suivre des macros, et les cacher derrière un bouton demandait un geste de plus par
 * aliment pour voir ce qu'on est venu voir.
 */
internal sealed interface LineEdit {
    data class Name(val value: String) : LineEdit

    data class Quantity(val value: String) : LineEdit

    /** L'unité dans laquelle la quantité est exprimée. */
    data class Measurement(val value: QuantityUnit) : LineEdit

    data class MacroValue(val macro: Macro, val value: String) : LineEdit

    /**
     * Une des alternatives proposées, choisie.
     *
     * La sixième variante, et le `when` ci-dessous ne compilait pas tant qu'elle
     * n'était pas traitée — c'est exactement ce que ce type fermé achète.
     */
    data class Substitute(val food: Food) : LineEdit

    /**
     * Le calcul de l'énergie d'après les macros, accepté.
     *
     * **Sans valeur portée**, et c'est volontaire : la ligne recalcule au moment où
     * elle applique. Transporter le chiffre affiché ferait de l'écran la source d'une
     * valeur nutritionnelle, alors qu'il n'en est que le miroir — et rendrait
     * possible d'écrire une énergie que la règle du domaine n'aurait pas proposée.
     */
    data object AcceptEnergy : LineEdit
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
    is LineEdit.MacroValue -> copy(
        macros = macros + (edit.macro to edit.value),
        edited = edited + edit.macro,
        // La valeur est la sienne desormais : la presenter encore comme estimee
        // serait faux. Meme regle que `DraftLine.corrected`.
        estimated = estimated - edit.macro,
    )
    is LineEdit.Substitute -> substituted(edit.food)
    // Une energie calculee est une energie voulue : elle se marque comme une saisie,
    // et le champ doit renaitre pour la montrer.
    LineEdit.AcceptEnergy -> withComputedEnergy()
}
