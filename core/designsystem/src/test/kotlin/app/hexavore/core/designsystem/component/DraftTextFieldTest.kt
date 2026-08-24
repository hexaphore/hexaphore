package app.hexavore.core.designsystem.component

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

    @Test
    fun `un champ d une ligne refuse un retour a la ligne`() {
        // La regle vaut meme quand le champ en montre deux : replie, il n'est plus
        // `singleLine` pour Compose, donc son clavier propose une touche entree -- et
        // un nom d'aliment coupe en deux se retrouverait tel quel dans le journal,
        // puis dans une sauvegarde, puis dans une recherche qui ne le trouve plus.
        assertTrue("Riz blanc, cuit".isSingleLine())
        assertTrue("".isSingleLine())
        assertFalse("Riz blanc,\ncuit".isSingleLine())
        assertFalse("\n".isSingleLine())
    }
}
