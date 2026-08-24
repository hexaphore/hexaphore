package app.hexavore.domain.diary

import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodServing
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * La quantité recalcule les valeurs — sauf celles qu'on a corrigées à la main.
 *
 * Deux règles qui se contredisent en apparence, et dont la cohabitation est le sujet
 * de ces tests. Sans la première, changer 100 g en 200 g laisse les calories
 * inchangées : la ligne ment. Sans la seconde, corriger les calories d'une pomme
 * parce que la sienne est petite se perd au gramme suivant, sans que rien ne
 * prévienne.
 */
class DraftLineQuantityTest {
    @Test
    fun `doubler la quantite double les valeurs`() {
        val ligne = DraftLine.of(ID, RIZ)

        val double = ligne.measured(quantity = 200.0)

        assertEquals(310.0, double.values.kcal!!, TOLERANCE)
        assertEquals(6.4, double.values.protein!!, TOLERANCE)
    }

    @Test
    fun `une valeur inconnue le reste, quelle que soit la quantite`() {
        val ligne = DraftLine.of(ID, RIZ)

        assertNull(ligne.measured(quantity = 200.0).values.fiber)
    }

    @Test
    fun `changer d unite recalcule aussi`() {
        // Passer de « 1 pomme moyenne » a des grammes change le poids, donc les
        // valeurs. C'est le meme geste vu autrement.
        val ligne = DraftLine.of(ID, POMME)

        val enGrammes = ligne.measured(quantity = 50.0, unit = QuantityUnit.Gram)

        assertEquals(27.0, enGrammes.values.kcal!!, TOLERANCE)
    }

    @Test
    fun `une valeur corrigee a la main ne bouge plus`() {
        // Sa pomme est petite : il corrige les calories. Changer la quantite ne doit
        // pas defaire sa correction sans le prevenir.
        val corrigee = DraftLine.of(ID, POMME).corrected(Macro.CALORIES, 60.0)

        val double = corrigee.measured(quantity = 2.0)

        assertEquals(60.0, double.values.kcal, "la correction est figee")
        assertEquals(4.2, double.values.fiber!!, TOLERANCE, "les autres suivent")
    }

    @Test
    fun `vider un champ le fige aussi`() {
        // Vider est une affirmation -- « je ne sais pas » -- et la quantite n'a pas
        // a la contredire au gramme suivant.
        val videe = DraftLine.of(ID, POMME).corrected(Macro.FIBER, null)

        assertNull(videe.measured(quantity = 2.0).values.fiber)
    }

    @Test
    fun `sans reference rien ne bouge`() {
        // Une ligne dont on ignore les valeurs pour 100 g n'a aucune regle de trois
        // a appliquer, et en inventer une reecrirait des chiffres que personne n'a
        // donnes.
        val libre = DraftLine(id = ID, name = "Reste d hier", quantity = 200.0, values = NutrientValues(kcal = 300.0))

        assertEquals(300.0, libre.measured(quantity = 400.0).values.kcal)
    }

    @Test
    fun `une quantite effacee ne recalcule rien`() {
        val ligne = DraftLine.of(ID, RIZ)

        val videe = ligne.measured(quantity = null)

        assertNull(videe.quantity)
        assertEquals(ligne.values, videe.values, "les valeurs attendent qu'une quantite revienne")
    }

    @Test
    fun `une ligne rouverte retrouve sa reference sans relire la fiche`() {
        // La regle de trois est exacte, et elle porte sur ce qui a ete enregistre :
        // un fabricant qui reformule son produit ne doit pas reecrire un journal
        // vieux de six mois.
        val figees = NutrientValues(kcal = 232.0, protein = 4.8)

        val reference = DraftLine.referenceOf(figees, grams = 150.0)!!

        assertEquals(232.0, reference.per(150.0).kcal!!, TOLERANCE)
        assertEquals(154.67, reference.kcal!!, 0.01)
    }

    @Test
    fun `une quantite enregistree nulle ne donne aucune reference`() {
        assertNull(DraftLine.referenceOf(NutrientValues(kcal = 232.0), grams = 0.0))
    }

    private companion object {
        val ID = DraftLineId("l1")
        const val TOLERANCE = 1e-9

        val RIZ = Food(
            id = FoodId("f-riz"),
            source = FoodSource.CIQUAL,
            name = "Riz blanc, cuit",
            per100g = NutrientValues(kcal = 155.0, protein = 3.2),
        )

        val POMME = Food(
            id = FoodId("f-pomme"),
            source = FoodSource.CIQUAL,
            name = "Pomme, chair et peau, crue",
            per100g = NutrientValues(kcal = 54.0, fiber = 1.4),
            servings = listOf(FoodServing("1 pomme moyenne", grams = 150.0, isDefault = true)),
        )
    }
}
