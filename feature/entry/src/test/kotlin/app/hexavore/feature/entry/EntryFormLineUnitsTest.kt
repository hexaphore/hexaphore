package app.hexavore.feature.entry

import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.QuantityUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que le **formulaire** propose comme unités — et c'est là que le défaut vivait.
 *
 * « 1 bol » se choisissait à la saisie et disparaissait du sélecteur en rouvrant le
 * plat. La règle avait été corrigée dans le domaine : l'unité qu'une ligne porte fait
 * toujours partie de celles qu'elle propose. **L'écran ne lisait pas cette règle-là**,
 * il en portait une copie — les deux universelles, puis celles de la fiche — et la
 * correction ne l'a pas touché. Le défaut a donc survécu à son propre correctif.
 *
 * Une règle écrite à deux endroits n'est pas la même règle : c'est deux règles qui se
 * ressemblent jusqu'au jour où l'une change. `EntryFormLine.units` délègue désormais,
 * et ces cas éprouvent le chemin que l'écran emprunte réellement.
 */
class EntryFormLineUnitsTest {
    @Test
    fun `une ligne tapee a la main ne propose que les grammes et les millilitres`() {
        // Faute de fiche pour dire ce que pese une tranche.
        assertEquals(QuantityUnit.universal, ligne().units)
    }

    @Test
    fun `une ligne propose les portions de sa fiche`() {
        val ligne = ligne(servings = listOf(BOL, TRANCHE))

        assertEquals(QuantityUnit.universal + listOf(BOL, TRANCHE), ligne.units)
    }

    @Test
    fun `une ligne rouverte propose l unite qu elle porte, sans sa fiche`() {
        // **Le defaut rapporte a l'usage.** Rouvrir un plat reconstruit l'unite depuis
        // ce qui a ete ecrit, mais sans relire la fiche -- elle a pu etre corrigee ou
        // supprimee depuis, et un journal est un registre d'evenements. La ligne
        // arrive donc avec « 1 bol » et aucune portion.
        val ligne = ligne(unit = BOL)

        assertTrue(BOL in ligne.units, "« ${BOL.code} » doit rester choisissable, or ${ligne.units}")
    }

    @Test
    fun `l unite portee n apparait pas deux fois`() {
        val ligne = ligne(unit = BOL, servings = listOf(BOL))

        assertEquals(1, ligne.units.count { it.code == BOL.code })
    }

    @Test
    fun `la pastille de l unite portee est allumee`() {
        val ligne = ligne(unit = BOL)

        assertTrue(ligne.units.any(ligne::chose), "aucune des ${ligne.units.size} pastilles ne s'allume")
    }

    @Test
    fun `une portion qui a change de poids reste la pastille choisie`() {
        // La comparaison porte sur le **code** et non sur l'unite entiere : la fiche a
        // pu corriger son bol de 250 a 260 g depuis l'enregistrement, et une pastille
        // « 1 bol » qui ne s'allume pas serait incomprehensible.
        val ligne = ligne(unit = BOL, servings = listOf(QuantityUnit.Serving(BOL.code, 260.0)))

        assertTrue(ligne.chose(QuantityUnit.Serving(BOL.code, 260.0)))
    }

    @Test
    fun `une autre unite n est pas choisie`() {
        // Sans ce cas, une comparaison qui repondrait toujours vrai allumerait les
        // trois pastilles a la fois et passerait les cinq precedents.
        val ligne = ligne(unit = BOL)

        assertFalse(ligne.chose(QuantityUnit.Gram))
    }

    private fun ligne(unit: QuantityUnit = QuantityUnit.Gram, servings: List<QuantityUnit.Serving> = emptyList()) =
        EntryFormLine(
            id = DraftLineId("l"),
            name = "Flocons d'avoine",
            quantity = "1",
            unit = unit,
            servings = servings,
        )

    private companion object {
        val BOL = QuantityUnit.Serving("1 bol", 250.0)
        val TRANCHE = QuantityUnit.Serving("1 tranche", 30.0)
    }
}
