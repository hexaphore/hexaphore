package app.hexavore.domain.diary

import app.hexavore.domain.profile.UnitSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce qu'une ligne propose comme unités.
 *
 * **Le défaut rapporté à l'usage** : « 1 bol » se choisissait à la saisie, puis
 * disparaissait du sélecteur en rouvrant le plat — alors que la quantité, elle, restait
 * juste. Rouvrir reconstruit l'unité depuis ce qui a été écrit, mais sans relire la
 * fiche, donc sans ses portions : le sélecteur ne pouvait pas montrer comme choisie une
 * unité absente de sa propre liste.
 */
class DraftLineUnitsTest {
    @Test
    fun `une ligne propose toujours les grammes et les millilitres`() {
        assertEquals(QuantityUnit.universal(UnitSystem.METRIC), ligne().units(UnitSystem.METRIC))
    }

    @Test
    fun `une ligne propose les portions de sa fiche`() {
        val ligne = ligne(servings = listOf(BOL, TRANCHE))

        assertEquals(QuantityUnit.universal(UnitSystem.METRIC) + listOf(BOL, TRANCHE), ligne.units(UnitSystem.METRIC))
    }

    @Test
    fun `une ligne propose l unite qu elle porte, meme sans fiche`() {
        // Le cas du plat rouvert : l'unite a survecu, ses portions non.
        val ligne = ligne(unit = BOL)

        assertTrue(
            BOL in ligne.units(UnitSystem.METRIC),
            "« ${BOL.code} » doit rester choisissable, or ${ligne.units(UnitSystem.METRIC)}",
        )
    }

    @Test
    fun `l unite portee n apparait pas deux fois`() {
        val ligne = ligne(unit = BOL, servings = listOf(BOL))

        assertEquals(1, ligne.units(UnitSystem.METRIC).count { it.code == BOL.code })
    }

    @Test
    fun `une portion qui a change de poids n apparait pas deux fois`() {
        // La comparaison porte sur le **code** et non sur l'unite entiere : la fiche a
        // pu corriger son bol de 250 a 260 g depuis, et deux « 1 bol » dans la meme
        // liste ne se distingueraient pas a l'oeil.
        val ligne = ligne(unit = BOL, servings = listOf(QuantityUnit.Serving(BOL.code, 260.0)))

        assertEquals(1, ligne.units(UnitSystem.METRIC).count { it.code == BOL.code })
    }

    private fun ligne(unit: QuantityUnit = QuantityUnit.Gram, servings: List<QuantityUnit.Serving> = emptyList()) =
        DraftLine(id = DraftLineId("l"), name = "Flocons d'avoine", unit = unit, servings = servings)

    private companion object {
        val BOL = QuantityUnit.Serving("1 bol", 250.0)
        val TRANCHE = QuantityUnit.Serving("1 tranche", 30.0)
    }
}
