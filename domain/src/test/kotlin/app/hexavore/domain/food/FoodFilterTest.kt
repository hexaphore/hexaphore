package app.hexavore.domain.food

import app.hexavore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les deux façons de combiner des pastilles, éprouvées sur la JVM.
 *
 * C'est tout l'intérêt d'avoir fait descendre la catégorie jusqu'au domaine : cette
 * règle est celle que voient la recherche, les récents et les favoris, et elle se
 * vérifie sans appareil. Un tag qui n'aurait été qu'une clause `WHERE` dans
 * l'adaptateur aurait demandé un émulateur pour éprouver « Favori + Fruits ».
 */
class FoodFilterTest {
    @Test
    fun `sans pastille, tout passe`() {
        assertTrue(FoodFilter.NONE.isEmpty)
        assertTrue(FoodFilter.NONE.matches(POMME))
        assertTrue(FoodFilter.NONE.matches(PATES_DE_MAMIE))
    }

    @Test
    fun `deux categories se cumulent en OU`() {
        // Et non en ET : aucun aliment n'est a la fois un fruit et un legume, donc
        // l'intersection ne rendrait jamais rien.
        val filtre = FoodFilter(categories = setOf(FoodCategory.FRUITS, FoodCategory.LEGUMES))

        assertTrue(filtre.matches(POMME))
        assertTrue(filtre.matches(CAROTTE))
        assertFalse(filtre.matches(RIZ))
    }

    @Test
    fun `une qualite se pose en ET par-dessus les categories`() {
        // « Favori + Fruits » montre les fruits epingles, pas les favoris et les
        // fruits.
        val filtre = FoodFilter(setOf(FoodCategory.FRUITS), setOf(FoodTrait.FAVORITE))

        assertTrue(filtre.matches(POMME.copy(favorite = true)))
        assertFalse(filtre.matches(POMME), "une pomme non epinglee n'a rien a faire la")
        assertFalse(filtre.matches(CAROTTE.copy(favorite = true)), "une carotte n'est pas un fruit")
    }

    @Test
    fun `les deux qualites se cumulent aussi en ET`() {
        val filtre = FoodFilter(traits = setOf(FoodTrait.PERSONAL, FoodTrait.FAVORITE))

        assertTrue(filtre.matches(PATES_DE_MAMIE.copy(favorite = true)))
        assertFalse(filtre.matches(PATES_DE_MAMIE))
        assertFalse(filtre.matches(POMME.copy(favorite = true)))
    }

    @Test
    fun `une fiche sans rayon reste trouvable tant qu aucun rayon n est demande`() {
        // Une huile, une soupe, un aliment personnel : leur en forcer un serait
        // mentir sur ce qu'on trouve en tapant dessus.
        assertTrue(FoodFilter(traits = setOf(FoodTrait.PERSONAL)).matches(PATES_DE_MAMIE))
        assertFalse(FoodFilter(categories = setOf(FoodCategory.FECULENTS)).matches(PATES_DE_MAMIE))
    }

    @Test
    fun `un aliment personnel ne repond qu a Mon aliment`() {
        // La decision : il ne porte aucune categorie. Consequence assumee et
        // eprouvee ici -- des « pates de mamie » ne sortent pas sous « Feculents ».
        val feculents = FoodFilter(categories = setOf(FoodCategory.FECULENTS))

        assertFalse(feculents.matches(PATES_DE_MAMIE))
        assertTrue(FoodFilter(traits = setOf(FoodTrait.PERSONAL)).matches(PATES_DE_MAMIE))
    }

    @Test
    fun `une pastille se pose et se retire`() {
        val pose = FoodFilter.NONE.toggle(FoodCategory.FRUITS).toggle(FoodTrait.FAVORITE)

        assertEquals(setOf(FoodCategory.FRUITS), pose.categories)
        assertEquals(setOf(FoodTrait.FAVORITE), pose.traits)
        assertTrue(pose.toggle(FoodCategory.FRUITS).toggle(FoodTrait.FAVORITE).isEmpty)
    }

    private companion object {
        val POMME = Food(
            id = FoodId("f-pomme"),
            source = FoodSource.CIQUAL,
            sourceRef = "13039",
            name = "Pomme, chair et peau, crue",
            category = FoodCategory.FRUITS,
            per100g = NutrientValues(kcal = 54.0),
        )

        val CAROTTE = POMME.copy(
            id = FoodId("f-carotte"),
            sourceRef = "20009",
            name = "Carotte, crue",
            category = FoodCategory.LEGUMES,
        )

        val RIZ = POMME.copy(
            id = FoodId("f-riz"),
            sourceRef = "9104",
            name = "Riz blanc, cuit",
            category = FoodCategory.FECULENTS,
        )

        /** Un aliment personnel : aucune catégorie, par décision. */
        val PATES_DE_MAMIE = Food(
            id = FoodId("f-pates"),
            source = FoodSource.CUSTOM,
            sourceRef = null,
            name = "Pates de mamie",
            category = null,
            per100g = NutrientValues(kcal = 320.0),
        )
    }
}
