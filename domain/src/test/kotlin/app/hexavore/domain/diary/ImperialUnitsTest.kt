package app.hexavore.domain.diary

import app.hexavore.domain.profile.UnitSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'once est une **unité de saisie**, pas une conversion d'affichage.
 *
 * C'est tout le choix de conception : une ligne garde ce qui a été tapé et le poids
 * d'une unité voyage avec elle, exactement comme « 1 tranche = 33 g ». Ce que
 * l'utilisateur a écrit est ce qui est enregistré, et relire un plat de l'an dernier ne
 * fait dériver aucun chiffre — là où convertir à l'affichage aurait décalé la valeur
 * d'une fraction de gramme à chaque aller-retour.
 */
class ImperialUnitsTest {
    @Test
    fun `une once pese la definition legale`() {
        assertEquals(28.349523125, QuantityUnit.Ounce.gramsPerUnit, TOLERANCE)
    }

    @Test
    fun `une once liquide est celle des Etats-Unis`() {
        // Celle du Royaume-Uni vaut 28,4 ml : prendre l une pour l autre se tromperait
        // de 4 % sur chaque verre, en silence.
        assertEquals(29.5735295625, QuantityUnit.FluidOunce.gramsPerUnit, TOLERANCE)
    }

    @Test
    fun `chaque systeme propose sa paire, et rien de l autre`() {
        assertEquals(listOf("g", "ml"), QuantityUnit.universal(UnitSystem.METRIC).map { it.code })
        assertEquals(listOf("oz", "fl oz"), QuantityUnit.universal(UnitSystem.IMPERIAL).map { it.code })
    }

    @Test
    fun `une once enregistree se relit comme une once`() {
        // Sans cette reconnaissance, « oz » redeviendrait une portion nommee dont le
        // poids serait redivise depuis les grammes : le compte serait juste, mais la
        // ligne aurait change de nature sans raison.
        assertEquals(QuantityUnit.Ounce, QuantityUnit.of("oz", grams = 56.7, quantity = 2.0))
        assertEquals(QuantityUnit.FluidOunce, QuantityUnit.of("fl oz", grams = 59.15, quantity = 2.0))
    }

    @Test
    fun `un plat note en grammes garde ses grammes apres la bascule`() {
        // La regle qui rend le reglage sans danger : la paire imperiale ne propose plus
        // le gramme, mais la ligne apporte son unite avec elle.
        val ligne = DraftLine.blank(DraftLineId("l1")).measured(150.0, QuantityUnit.Gram)

        val proposees = ligne.units(UnitSystem.IMPERIAL).map { it.code }

        assertEquals(listOf("oz", "fl oz", "g"), proposees)
        assertTrue("g" in proposees, "sinon l ecran ne pourrait pas montrer comme choisie l unite de la ligne")
    }

    @Test
    fun `les portions de la fiche restent proposees dans les deux systemes`() {
        // Une tranche est une tranche : elle ne depend d aucun systeme, et la retirer
        // en imperial ferait perdre ce que la fiche mesure.
        val ligne = DraftLine.blank(DraftLineId("l1")).copy(servings = listOf(QuantityUnit.Serving("1 tranche", 33.0)))

        assertTrue("1 tranche" in ligne.units(UnitSystem.METRIC).map { it.code })
        assertTrue("1 tranche" in ligne.units(UnitSystem.IMPERIAL).map { it.code })
    }

    private companion object {
        const val TOLERANCE = 0.000001
    }
}
