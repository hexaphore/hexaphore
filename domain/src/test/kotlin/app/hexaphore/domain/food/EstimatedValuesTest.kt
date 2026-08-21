package app.hexaphore.domain.food

import app.hexaphore.domain.diary.DraftLine
import app.hexaphore.domain.diary.DraftLineId
import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La provenance d'une valeur, portée jusqu'à la ligne de saisie.
 *
 * **Une valeur complétée reste une valeur inventée**, et la seule façon de le dire
 * sans mentir est de le dire valeur par valeur : une fiche dont l'énergie a été
 * devinée mais dont les protéines sont mesurées n'est ni une fiche mesurée, ni une
 * estimation. Un drapeau unique aurait menti dans les deux sens.
 */
class EstimatedValuesTest {
    @Test
    fun `une ligne herite de la provenance de sa fiche`() {
        val ligne = DraftLine.of(DraftLineId("l1"), CAPRES)

        assertEquals(setOf(Macro.CALORIES), ligne.estimated)
    }

    @Test
    fun `une fiche entierement mesuree ne marque rien`() {
        // Le cas courant : 91 % de la table de l'ANSES n'a aucun trou, et une ligne
        // sans marque ne porte aucun contour en pointilles.
        assertTrue(DraftLine.of(DraftLineId("l1"), CAROTTE).estimated.isEmpty())
    }

    @Test
    fun `une valeur estimee pour cent grammes reste estimee pour cent-cinquante`() {
        // La regle de trois ne transforme pas une supposition en mesure. C'est le
        // pendant exact de « une valeur inconnue le reste » : la quantite ne change
        // pas ce qu'on sait d'une valeur, seulement combien il y en a.
        val ligne = DraftLine.of(DraftLineId("l1"), CAPRES).measured(150.0, QuantityUnit.Gram)

        assertEquals(setOf(Macro.CALORIES), ligne.estimated)
        assertEquals(58.5, ligne.values.kcal!!, TOLERANCE)
    }

    @Test
    fun `corriger une valeur efface sa marque`() {
        // La valeur est celle de l'utilisateur desormais. Continuer a la presenter
        // comme incertaine serait faux, et le contour en pointilles designerait un
        // chiffre que personne n'a devine.
        val ligne = DraftLine.of(DraftLineId("l1"), CAPRES).corrected(Macro.CALORIES, 45.0)

        assertTrue(ligne.estimated.isEmpty())
        assertEquals(setOf(Macro.CALORIES), ligne.edited, "et elle est desormais tenue pour saisie")
    }

    @Test
    fun `corriger une valeur n efface pas la marque des autres`() {
        // Une fiche a plusieurs trous : en corriger un ne dit rien des autres, et les
        // effacer ensemble ferait passer pour mesuree une valeur toujours devinee.
        val ligne = DraftLine.of(DraftLineId("l1"), SANS_RIEN).corrected(Macro.CALORIES, 45.0)

        assertEquals(Macro.entries.toSet() - Macro.CALORIES, ligne.estimated)
    }

    @Test
    fun `l originale l emporte toujours sur la completion`() {
        // La regle qui commande toute la passe, vue depuis le modele : une fiche dont
        // l'ANSES publie l'energie ne la marque jamais, meme si une estimation existe
        // encore dans le fichier. La marque suit ce qui s'affiche, pas ce qui dort.
        val ligne = DraftLine.of(DraftLineId("l1"), CAROTTE)

        assertEquals(30.2, ligne.values.kcal!!, TOLERANCE)
        assertTrue(Macro.CALORIES !in ligne.estimated)
    }

    private companion object {
        const val TOLERANCE = 1e-9

        /** L'énergie devinée, les macros publiées : le cas type d'une fiche comblée. */
        val CAPRES = Food(
            id = FoodId("f-capres"),
            source = FoodSource.CIQUAL,
            sourceRef = "11040",
            name = "Câpres, au vinaigre",
            per100g = NutrientValues(kcal = 39.0, protein = 2.18, carbs = 3.5, fat = 0.86, fiber = 3.6),
            estimated = setOf(Macro.CALORIES),
        )

        val CAROTTE = Food(
            id = FoodId("f-carotte"),
            source = FoodSource.CIQUAL,
            sourceRef = "20009",
            name = "Carotte, crue",
            per100g = NutrientValues(kcal = 30.2, protein = 0.78, carbs = 5.16, fat = 0.25, fiber = 2.9),
        )

        /** Les six devinées : le cas qui montre qu'une correction n'en efface qu'une. */
        val SANS_RIEN = Food(
            id = FoodId("f-inconnu"),
            source = FoodSource.CIQUAL,
            sourceRef = "00001",
            name = "Aliment sans aucune teneur mesuree",
            per100g = NutrientValues(kcal = 1.0, protein = 1.0, carbs = 1.0, sugars = 1.0, fat = 1.0, fiber = 1.0),
            estimated = Macro.entries.toSet(),
        )
    }
}
