package app.hexaphore.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce qu'un champ numérique laisse entrer.
 *
 * La règle est ici et non dans un écran : elle vaut pour tous, et deux copies
 * divergeraient le jour où l'une accepterait ce que l'autre refuse.
 */
class DraftTextFieldTest {
    @Test
    fun `un champ numerique accepte un separateur, pas deux`() {
        // « 12,5,3 » ne se convertit en aucun nombre : la saisie deviendrait
        // invalide sans que rien ne dise pourquoi. Le champ refuse la frappe.
        assertTrue("12".isNumberField())
        assertTrue("12,5".isNumberField())
        assertTrue("12.5".isNumberField())
        assertTrue("".isNumberField(), "un champ vide se saisit forcement en passant")
        assertFalse("12,5,3".isNumberField())
        assertFalse("12a".isNumberField())
        assertFalse("-5".isNumberField(), "une quantite negative n'a pas de sens dans un journal")
    }
}
