package app.hexavore.domain.food

import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce qu'une fiche d'aliment devient quand on la choisit.
 *
 * C'est le point que [D42][decisions] avait reporté à cette tranche : le recalcul à
 * la quantité suppose une référence pour 100 g, et les portions nommées supposent
 * une fiche. Les deux existent enfin.
 *
 * [decisions]: docs/11-decisions.md
 */
class FoodDraftLineTest {
    @Test
    fun `une pomme arrive a sa portion usuelle, pas a cent grammes`() {
        // Personne ne pese une pomme. C'est toute la raison d'etre de servings.csv.
        val ligne = DraftLine.of(ID, POMME)

        assertEquals(1.0, ligne.quantity)
        assertEquals("1 pomme moyenne", ligne.unit.code)
        assertEquals(150.0, ligne.grams)
    }

    @Test
    fun `les valeurs suivent la portion et non les cent grammes de la fiche`() {
        val ligne = DraftLine.of(ID, POMME)

        assertEquals(81.0, ligne.values.kcal!!, TOLERANCE)
        assertEquals(2.1, ligne.values.fiber!!, TOLERANCE)
    }

    @Test
    fun `un aliment sans portion declaree propose cent grammes`() {
        val ligne = DraftLine.of(ID, RIZ)

        assertEquals(100.0, ligne.quantity)
        assertEquals(QuantityUnit.Gram, ligne.unit)
        assertEquals(155.0, ligne.values.kcal)
    }

    @Test
    fun `un aliment sans energie arrive quand meme, avec ce qu on sait de lui`() {
        // 143 aliments de la table sont dans ce cas, et ce ne sont pas des rebuts :
        // la feta, les capres, la canneberge. Les ecarter aurait retire des aliments
        // courants du catalogue ; les vider de leurs valeurs connues aurait oblige a
        // tout retaper.
        val ligne = DraftLine.of(ID, FETA)

        assertNull(ligne.values.kcal, "l energie n a pas ete determinee")
        assertEquals(17.0, ligne.values.protein)
        assertEquals(25.0, ligne.values.fat)
    }

    @Test
    fun `un aliment sans energie n est pas enregistrable tel quel`() {
        // Il manque une seule chose, et l'ecran le dira. Le bouton reste indisponible
        // plutot que d'ecrire une energie que personne n'a donnee.
        assertTrue(!DraftLine.of(ID, FETA).complete)
        assertTrue(DraftLine.of(ID, POMME).complete)
    }

    @Test
    fun `les portions de la fiche s ajoutent aux deux unites universelles`() {
        val ligne = DraftLine.of(ID, POMME)

        assertEquals(
            listOf("g", "ml", "1 pomme moyenne", "1 quartier"),
            ligne.units.map { it.code },
        )
    }

    @Test
    fun `une ligne tapee a la main ne propose aucune portion`() {
        // Sans fiche, rien ne peut dire ce que pese une tranche, et le demander a
        // l'utilisateur est exactement le travail qu'on veut lui epargner.
        assertEquals(listOf("g", "ml"), DraftLine.blank(ID).units.map { it.code })
    }

    @Test
    fun `la fiche voyage avec la ligne, pour etre versee au catalogue`() {
        val ligne = DraftLine.of(ID, POMME)

        assertEquals(POMME.id, ligne.foodId)
        assertEquals(POMME, ligne.food)
    }

    private companion object {
        val ID = DraftLineId("l1")
        const val TOLERANCE = 1e-9

        val POMME = Food(
            id = FoodId("f-pomme"),
            source = FoodSource.CIQUAL,
            sourceRef = "13039",
            name = "Pomme, chair et peau, crue",
            per100g = NutrientValues(kcal = 54.0, protein = 0.25, fiber = 1.4),
            servings = listOf(
                FoodServing("1 pomme moyenne", grams = 150.0, isDefault = true),
                FoodServing("1 quartier", grams = 40.0),
            ),
        )

        val RIZ = Food(
            id = FoodId("f-riz"),
            source = FoodSource.CIQUAL,
            sourceRef = "9104",
            name = "Riz blanc, cuit, sans sel ajouté",
            per100g = NutrientValues(kcal = 155.0, protein = 3.2),
        )

        val FETA = Food(
            id = FoodId("f-feta"),
            source = FoodSource.CIQUAL,
            sourceRef = "12999",
            name = "Fromage type feta, au lait de brebis 100%",
            per100g = NutrientValues(protein = 17.0, fat = 25.0),
        )
    }
}
