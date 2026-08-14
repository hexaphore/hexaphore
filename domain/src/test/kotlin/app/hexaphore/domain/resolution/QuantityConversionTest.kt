package app.hexaphore.domain.resolution

import app.hexaphore.domain.ai.EstimatedUnit
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodServing
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La charnière entre ce que le modèle estime et ce que le journal enregistre.
 *
 * Les cas portent sur **une** unité : la multiplication par la quantité est
 * vérifiée une fois et n'a pas à polluer les autres.
 */
class QuantityConversionTest {
    @Test
    fun `des grammes sont des grammes, et rien n est devine`() {
        val rendu = convertToGrams(150.0, EstimatedUnit.G)

        assertEquals(150.0, rendu.grams, TOLERANCE)
        assertFalse(rendu.guessed, "des grammes ne se devinent pas")
    }

    @Test
    fun `sans densite, un millilitre pese un gramme et le dit`() {
        val rendu = convertToGrams(200.0, EstimatedUnit.ML)

        assertEquals(200.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed, "un litre de lait ne pese pas un kilo")
    }

    @Test
    fun `une densite connue s applique et cesse d etre une supposition`() {
        val rendu = convertToGrams(200.0, EstimatedUnit.ML, density = LAIT)

        assertEquals(206.0, rendu.grams, TOLERANCE)
        assertFalse(rendu.guessed)
    }

    @Test
    fun `la portion nommee de la fiche l emporte sur le forfait`() {
        // Le cas qui justifie l'ecart avec docs/04 : la table des portions porte
        // « 1 bol » a 40 g pour des cereales. Le forfait de 250 g se tromperait
        // d'un facteur six, et sans se signaler puisqu'il aurait l'air d'une regle.
        val cereales = aliment(FoodServing("1 bol", grams = 40.0, isDefault = true))

        val rendu = convertToGrams(1.0, EstimatedUnit.BOWL, cereales)

        assertEquals(40.0, rendu.grams, TOLERANCE)
        assertFalse(rendu.guessed, "une portion de la fiche est une donnee, pas une supposition")
    }

    @Test
    fun `sans portion nommee, le bol vaut le forfait et c est signale`() {
        val rendu = convertToGrams(1.0, EstimatedUnit.BOWL, aliment())

        assertEquals(250.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed)
    }

    @Test
    fun `une tranche prend le poids que la fiche lui donne`() {
        val pain = aliment(FoodServing("1 tranche", grams = 45.0))

        assertEquals(45.0, convertToGrams(1.0, EstimatedUnit.SLICE, pain).grams, TOLERANCE)
    }

    @Test
    fun `les deux cuilleres ne se confondent pas`() {
        // « cuillere a soupe » contient « cuillere », et une comparaison sur le seul
        // mot commun rendrait la premiere portion trouvee pour les deux unites.
        val miel = aliment(
            FoodServing("1 cuillère à soupe", grams = 21.0),
            FoodServing("1 cuillère à café", grams = 7.0),
        )

        assertEquals(7.0, convertToGrams(1.0, EstimatedUnit.TSP, miel).grams, TOLERANCE)
    }

    @Test
    fun `sans portion nommee, la cuillere applique la densite au forfait`() {
        val rendu = convertToGrams(1.0, EstimatedUnit.TBSP, density = HUILE)

        assertEquals(13.8, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed, "le forfait de 15 g reste une supposition, densite ou non")
    }

    @Test
    fun `une piece prend la portion par defaut de la fiche`() {
        val pomme = aliment(
            FoodServing("1 fraise", grams = 12.0),
            FoodServing("1 pomme moyenne", grams = 150.0, isDefault = true),
        )

        assertEquals(150.0, convertToGrams(1.0, EstimatedUnit.PIECE, pomme).grams, TOLERANCE)
    }

    @Test
    fun `une piece retombe sur la quantite proposee a l ouverture`() {
        val produit = aliment().copy(defaultServingG = 90.0)

        val rendu = convertToGrams(1.0, EstimatedUnit.PIECE, produit)

        assertEquals(90.0, rendu.grams, TOLERANCE)
        assertFalse(rendu.guessed, "une portion d'emballage est une donnee")
    }

    @Test
    fun `une piece sans rien vaut cent grammes, et c est une supposition`() {
        val rendu = convertToGrams(1.0, EstimatedUnit.PIECE, aliment())

        assertEquals(100.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed)
    }

    @Test
    fun `une assiette reste une supposition meme quand la fiche porte des portions`() {
        // Une assiette n'est pas une propriete de l'aliment : aucune fiche ne peut
        // la mesurer, donc aucune portion ne doit faire croire qu'elle l'a fait.
        val riz = aliment(FoodServing("1 portion", grams = 150.0, isDefault = true))

        val rendu = convertToGrams(1.0, EstimatedUnit.PLATE, riz)

        assertEquals(350.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed)
    }

    @Test
    fun `la quantite met le poids unitaire a l echelle`() {
        val oeuf = aliment(FoodServing("1 oeuf", grams = 50.0, isDefault = true))

        assertEquals(150.0, convertToGrams(3.0, EstimatedUnit.PIECE, oeuf).grams, TOLERANCE)
    }

    @Test
    fun `un verre garde la densite quand la fiche ne dit rien`() {
        val rendu = convertToGrams(1.0, EstimatedUnit.GLASS, density = JUS)

        assertEquals(208.0, rendu.grams, TOLERANCE)
        assertTrue(rendu.guessed)
    }

    private fun aliment(vararg servings: FoodServing) = Food(
        id = FoodId("fiche"),
        source = FoodSource.CIQUAL,
        name = "Aliment",
        per100g = NutrientValues(kcal = 100.0),
        servings = servings.toList(),
    )

    private companion object {
        const val TOLERANCE = 0.001
        const val LAIT = 1.03
        const val JUS = 1.04
        const val HUILE = 0.92
    }
}
