package app.hexaphore.feature.entry

import app.hexaphore.domain.diary.DraftLineId
import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.nutrition.Macro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La frontière entre le texte des champs et les nombres du domaine.
 *
 * C'est le seul endroit du parcours de saisie où « inconnu » peut se transformer en
 * « zéro » sans que rien ne le signale : il suffirait de lire un champ vide comme un
 * `0` pour éviter tout traitement du cas nul, et le journal porterait alors des
 * zéros que personne n'a saisis.
 */
class EntryFormTest {
    @Test
    fun `un champ de macro laisse vide veut dire inconnu et non zero`() {
        val line = ligne(kcal = "195", protein = "4", fiber = "")

        val values = line.toDraftLine().values

        assertEquals(4.0, values.protein)
        assertNull(values.fiber, "un champ vide n'est pas zero gramme de fibres")
    }

    @Test
    fun `sans energie la ligne n est pas enregistrable, mais garde ce qu on sait d elle`() {
        // Une ligne sans energie n'est pas une ligne a zero calorie : c'est une
        // ligne qu'on ne peut pas encore enregistrer. Les valeurs deja connues
        // restent -- c'est ce qui permet a un aliment sans energie determinee, la
        // feta ou les capres, d'arriver avec ses proteines.
        val line = ligne(kcal = "", protein = "4")

        assertNull(line.toDraftLine().values.kcal)
        assertEquals(4.0, line.toDraftLine().values.protein)
        assertFalse(line.toDraftLine().complete)
    }

    @Test
    fun `la virgule decimale est acceptee comme le point`() {
        // Le clavier decimal d'un telephone en francais produit une virgule.
        // La refuser rendrait la saisie impossible sans qu'aucun message ne dise
        // pourquoi.
        assertEquals(12.5, ligne(quantity = "12,5").toDraftLine().quantity)
        assertEquals(12.5, ligne(quantity = "12.5").toDraftLine().quantity)
    }

    @Test
    fun `une ligne complete est enregistrable`() {
        assertTrue(ligne().toDraftLine().complete)
    }

    @Test
    fun `une quantite nulle ne suffit pas`() {
        // Zero gramme de riz n'est pas un repas, c'est une ligne oubliee.
        assertFalse(ligne(quantity = "0").toDraftLine().complete)
    }

    @Test
    fun `un nom fait de blancs ne suffit pas`() {
        assertFalse(ligne(name = "   ").toDraftLine().complete)
    }

    @Test
    fun `une valeur entiere se relit sans decimale`() {
        // « 150.0 » donnerait a chaque relecture d'un plat l'apparence d'une
        // precision au dixieme de gramme que personne n'a saisie.
        val relu = EntryFormLine.of(ligne(quantity = "150").toDraftLine())

        assertEquals("150", relu.quantity)
    }

    @Test
    fun `une valeur decimale garde sa decimale a la relecture`() {
        val relu = EntryFormLine.of(ligne(quantity = "12,5").toDraftLine())

        assertEquals("12.5", relu.quantity)
    }

    @Test
    fun `une portion nommee survit a l aller-retour`() {
        // Le poids voyage avec l'unite : sans lui, rouvrir un plat demanderait de
        // retrouver une fiche peut-etre supprimee pour savoir ce que pesait
        // « 1 tranche » ce jour-la.
        val portion = QuantityUnit.Serving("1 tranche", gramsPerUnit = 30.0)
        val relu = EntryFormLine.of(ligne(unit = portion, quantity = "2").toDraftLine())

        assertEquals(portion, relu.unit)
    }

    @Test
    fun `une ligne relue est depliee`() {
        // Repliee, il faudrait deplier chaque ligne pour verifier qu'on modifie
        // la bonne.
        assertTrue(EntryFormLine.of(ligne().toDraftLine()).expanded)
    }

    @Test
    fun `une unite choisie survit a l aller-retour`() {
        val relu = EntryFormLine.of(ligne(unit = QuantityUnit.Millilitre).toDraftLine())

        assertEquals(QuantityUnit.Millilitre, relu.unit)
    }

    @Test
    fun `un champ numerique accepte un separateur, pas deux`() {
        // « 12,5,3 » ne se convertit en aucun nombre : la ligne deviendrait
        // inenregistrable sans que rien ne dise pourquoi. Le champ refuse la frappe.
        assertTrue("12".isNumberField())
        assertTrue("12,5".isNumberField())
        assertTrue("12.5".isNumberField())
        assertTrue("".isNumberField(), "un champ vide se saisit forcement en passant")
        assertFalse("12,5,3".isNumberField())
        assertFalse("12a".isNumberField())
        assertFalse("-5".isNumberField(), "une quantite negative n'a pas de sens dans un journal")
    }

    private fun ligne(
        name: String = "Riz",
        quantity: String = "150",
        unit: QuantityUnit = QuantityUnit.Gram,
        kcal: String = "195",
        protein: String = "4",
        fiber: String = "1,2",
    ) = EntryFormLine(
        id = DraftLineId("l1"),
        name = name,
        quantity = quantity,
        unit = unit,
        macros = mapOf(
            Macro.CALORIES to kcal,
            Macro.PROTEIN to protein,
            Macro.FIBER to fiber,
        ),
    )
}
