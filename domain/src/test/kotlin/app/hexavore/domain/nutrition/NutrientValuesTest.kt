package app.hexavore.domain.nutrition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le recalcul à la quantité, et le piège de la tranche appliqué à l'endroit où il
 * est le plus facile à commettre.
 *
 * Multiplier une valeur inconnue est la faute qui ne se voit pas : `(fiber ?: 0.0) *
 * facteur` compile, produit des nombres plausibles, et transforme un aliment dont
 * les fibres n'ont pas été mesurées en un aliment sans fibres.
 */
class NutrientValuesTest {
    @Test
    fun `une valeur connue suit la quantite`() {
        val pomme = NutrientValues(kcal = 54.0, protein = 0.25, fiber = 1.4)

        val portion = pomme.per(150.0)

        assertEquals(81.0, portion.kcal!!, TOLERANCE)
        assertEquals(2.1, portion.fiber!!, TOLERANCE)
    }

    @Test
    fun `une valeur inconnue le reste, quelle que soit la quantite`() {
        val feta = NutrientValues(protein = 17.0, fat = 25.0)

        val portion = feta.per(150.0)

        assertNull(portion.kcal, "une energie non determinee ne devient pas zero kilocalorie")
        assertNull(portion.fiber, "des fibres non mesurees ne deviennent pas zero gramme")
        assertEquals(25.5, portion.protein!!, TOLERANCE)
    }

    @Test
    fun `une teneur nulle reste nulle, parce que c est une mesure`() {
        // Zero n'est pas inconnu : l'huile d'olive contient reellement zero gramme
        // de glucides, et ce zero doit survivre a la multiplication.
        val huile = NutrientValues(kcal = 899.0, carbs = 0.0)

        assertEquals(0.0, huile.per(10.0).carbs)
    }

    @Test
    fun `cent grammes ne changent rien`() {
        val valeurs = NutrientValues(kcal = 54.0, protein = 0.25)

        assertEquals(valeurs, valeurs.per(NutrientValues.REFERENCE_GRAMS))
    }

    @Test
    fun `sans energie il n y a pas de ligne de journal`() {
        // Une ligne de journal exige une energie. En inventer une ici serait ecrire
        // un chiffre que personne n'a donne.
        assertNull(NutrientValues(protein = 17.0).toMacros())
    }

    @Test
    fun `avec une energie les cinq autres traversent telles quelles`() {
        val macros = NutrientValues(kcal = 195.0, protein = 4.0).toMacros()!!

        assertEquals(195.0, macros.kcal)
        assertEquals(4.0, macros.protein)
        assertNull(macros.fiber, "une valeur absente ne devient pas zero en changeant de type")
    }

    @Test
    fun `l aller-retour par les macros ne perd rien`() {
        val depart = NutrientValues(kcal = 195.0, protein = 4.0, fiber = null)

        assertEquals(depart, NutrientValues.of(depart.toMacros()!!))
    }

    @Test
    fun `une ligne vierge est vide, une ligne a zero ne l est pas`() {
        assertTrue(NutrientValues().empty)
        assertTrue(!NutrientValues(kcal = 0.0).empty, "zero kilocalorie est une saisie, pas une absence")
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
