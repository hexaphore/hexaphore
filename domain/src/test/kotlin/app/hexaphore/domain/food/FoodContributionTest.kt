package app.hexaphore.domain.food

import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce qui a le droit de sortir de l'appareil.
 *
 * **La première écriture sortante du projet**, et [01][perimetre] n'en prévoit aucune.
 * Chaque refus de cette classe est donc une porte qu'on ferme volontairement : ce qui
 * part est public, définitif, et relu par d'autres contributeurs.
 *
 * [perimetre]: docs/01-perimetre.md
 */
class FoodContributionTest {
    @Test
    fun `un aliment personnel avec code-barres part`() {
        val contribution = FoodContribution.of(TAPENADE)

        assertNotNull(contribution)
        assertEquals("3017620422003", contribution!!.barcode.value)
        assertEquals("Tapenade maison", contribution.name)
    }

    @Test
    fun `une fiche de l ANSES ne part pas`() {
        // On ne reverse pas la table de l'ANSES a Open Food Facts : ce sont deux
        // bases, et celle-ci n'a pas a recopier celle-la.
        assertNull(FoodContribution.of(TAPENADE.copy(source = FoodSource.CIQUAL)))
    }

    @Test
    fun `une fiche venue d Open Food Facts ne repart pas`() {
        // Elle y est deja, et la renvoyer ecraserait le travail de quelqu'un d'autre
        // avec une copie vieillie de ce qu'il avait ecrit.
        assertNull(FoodContribution.of(TAPENADE.copy(source = FoodSource.OFF)))
    }

    @Test
    fun `une fiche sans code-barres ne part pas`() {
        // C'est la cle sous laquelle Open Food Facts range un produit : sans elle, la
        // fiche n'y designe rien.
        assertNull(FoodContribution.of(TAPENADE.copy(sourceRef = null)))
    }

    @Test
    fun `un code-barres invalide ne part pas`() {
        // La cle de controle est verifiee : une suite de chiffres qui n'en est pas un
        // designerait un produit qui n'existe pas, et la fiche s'y perdrait.
        assertNull(FoodContribution.of(TAPENADE.copy(sourceRef = "3017620422004")))
    }

    @Test
    fun `une fiche dont une teneur est estimee ne part pas`() {
        // **Un chiffre invente qui entre dans une base publique y devient une mesure**
        // pour tous ceux qui le liront. C'est la regle la plus importante de cette
        // livraison, et elle se tient ici plutot que dans l'ecran, qui l'oublierait.
        val completee = TAPENADE.copy(estimated = setOf(Macro.FIBER))

        assertNull(FoodContribution.of(completee))
    }

    @Test
    fun `une fiche sans nom ne part pas`() {
        assertNull(FoodContribution.of(TAPENADE.copy(name = "   ")))
    }

    @Test
    fun `le nom et la marque sont debarrasses de leurs blancs`() {
        val contribution = FoodContribution.of(TAPENADE.copy(name = "  Tapenade  ", brand = "  Maison "))!!

        assertEquals("Tapenade", contribution.name)
        assertEquals("Maison", contribution.brand)
    }

    @Test
    fun `une marque vide ne part pas comme une chaine vide`() {
        // L'envoyer effacerait la marque cote Open Food Facts si quelqu'un l'avait
        // renseignee, alors qu'on n'a rien a en dire.
        assertNull(FoodContribution.of(TAPENADE.copy(brand = "   "))!!.brand)
    }

    @Test
    fun `les six teneurs traversent telles quelles, absences comprises`() {
        val sansSucres = TAPENADE.copy(per100g = TAPENADE.per100g.copy(sugars = null))

        val contribution = FoodContribution.of(sansSucres)!!

        assertEquals(39.0, contribution.per100g.kcal)
        assertNull(contribution.per100g.sugars, "une absence reste une absence jusqu au bout")
    }

    @Test
    fun `la portion par defaut de la fiche l emporte sur son poids de service`() {
        val avecPortion = TAPENADE.copy(
            servings = listOf(FoodServing("1 cuillere", grams = 15.0, isDefault = true)),
            defaultServingG = 30.0,
        )

        assertEquals(15.0, FoodContribution.of(avecPortion)!!.servingGrams)
    }

    @Test
    fun `sans portion nommee, le poids de service sert`() {
        assertEquals(30.0, FoodContribution.of(TAPENADE)!!.servingGrams)
    }

    // --- Le compte -------------------------------------------------------------

    @Test
    fun `un compte a moitie saisi n ouvre rien`() {
        assertFalse(OffAccount(userId = "charly", password = "").usable)
        assertFalse(OffAccount(userId = "", password = "secret").usable)
        assertTrue(OffAccount(userId = "charly", password = "secret").usable)
    }

    @Test
    fun `un compte ne s imprime jamais`() {
        // **L'identifiant est la moitie d'un secret.** Le publier dans un journal de
        // plantage reduirait le mot de passe au seul obstacle -- et un `data class`
        // imprime tous ses champs par defaut.
        val ecrit = OffAccount(userId = "charly", password = "secret").toString()

        assertFalse(ecrit.contains("secret"), "le mot de passe n apparait pas")
        assertFalse(ecrit.contains("charly"), "l identifiant non plus")
    }

    @Test
    fun `sans compte, la contribution n est pas ouverte`() {
        // Le bouton n'apparait pas plutot que de refuser : c'est la regle des modes
        // d'IA sans cle.
        assertFalse(ContributionSetup().open)
        assertFalse(ContributionSetup(account = OffAccount("charly", "")).open)
        assertTrue(ContributionSetup(account = OffAccount("charly", "secret")).open)
    }

    @Test
    fun `le bac a sable est eteint par defaut`() {
        // Une contribution qui partirait par defaut vers un bac a sable serait une
        // contribution qui n'existe pas, offerte a quelqu'un qui croit contribuer.
        assertFalse(ContributionSetup().sandbox)
    }

    private companion object {
        val TAPENADE = Food(
            id = FoodId("f-tapenade"),
            source = FoodSource.CUSTOM,
            sourceRef = "3017620422003",
            name = "Tapenade maison",
            brand = "Sans marque",
            per100g = NutrientValues(
                kcal = 39.0,
                protein = 2.18,
                carbs = 3.5,
                sugars = 0.4,
                fat = 0.86,
                fiber = 3.6,
            ),
            defaultServingG = 30.0,
        )
    }
}
