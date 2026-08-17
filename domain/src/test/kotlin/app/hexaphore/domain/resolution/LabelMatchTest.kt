package app.hexaphore.domain.resolution

import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les étapes 2 et 3 de [docs/04][sources] : ce qu'on fait d'une liste de candidats.
 *
 * Les libellés sont ceux de la table de l'ANSES, et le chorizo n'est pas une
 * plaisanterie : c'est ce que `LIKE '%riz%'` ramène, et c'est exactement le candidat
 * que le ×0,8 des produits de marque doit écarter.
 *
 * Ce que valent les seuils eux-mêmes se juge ailleurs — `MatchVerdictTest` pour les
 * bornes, `FoodRankingTest` pour l'échelle. Ici on tient ce que le résolveur en fait.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
class LabelMatchTest {
    @Test
    fun `sans candidat, il n y a rien a peser`() {
        val rendu = matchFor(emptyList(), "riz")

        assertEquals(MatchVerdict.NONE, rendu.verdict)
        assertNull(rendu.food)
        assertEquals(0.0, rendu.confidence, TOLERANCE)
        assertTrue(rendu.alternatives.isEmpty())
    }

    @Test
    fun `un nom exact se retient seul, et ne propose rien d autre`() {
        val rendu = matchFor(listOf(RIZ, RIZ_BLANC_CUIT), "riz")

        assertEquals(MatchVerdict.AUTOMATIC, rendu.verdict)
        assertEquals(RIZ, rendu.food)
        assertTrue(rendu.alternatives.isEmpty(), "une correspondance sure n a pas de rechange a offrir")
    }

    @Test
    fun `un simple prefixe demande une relecture, et propose ses suivants`() {
        val rendu = matchFor(listOf(RIZ_BLANC_CUIT, RIZ_COMPLET_CRU), "riz")

        assertEquals(MatchVerdict.REVIEW, rendu.verdict)
        assertEquals(RIZ_BLANC_CUIT, rendu.food)
        assertEquals(listOf(RIZ_COMPLET_CRU), rendu.alternatives)
    }

    @Test
    fun `une alternative qu on refuserait comme correspondance n est pas proposee`() {
        // L'arbitrage de D75 : le seuil des alternatives est celui de la decision, et
        // non un second ecrit a cote. Le chorizo est sous 0,40 — le proposer
        // remplirait la liste de bruit.
        val rendu = matchFor(listOf(RIZ_BLANC_CUIT, RIZ_COMPLET_CRU, CHORIZO), "riz")

        assertEquals(listOf(RIZ_COMPLET_CRU), rendu.alternatives)
    }

    @Test
    fun `jamais plus de trois alternatives`() {
        val candidats = listOf(RIZ_BLANC_CUIT, RIZ_COMPLET_CRU, RIZ_SAUVAGE_CRU, RIZ_BASMATI_CUIT, RIZ_ROND_CRU)

        assertEquals(3, matchFor(candidats, "riz").alternatives.size)
    }

    @Test
    fun `sous le seuil bas, le meilleur candidat est ecarte mais sa confiance se dit`() {
        val rendu = matchFor(listOf(CHORIZO), "riz")

        assertEquals(MatchVerdict.NONE, rendu.verdict)
        assertNull(rendu.food, "en dessous de 0,40, docs/04 ne retient personne")
        assertTrue(rendu.confidence > 0.0, "la confiance dit de combien on a manque, et sert au calibrage")
    }

    @Test
    fun `le classement est refait ici, quel que soit l ordre recu`() {
        // Les deux implementations de la recherche n'ordonnent pas pareil — la vraie
        // par FoodRanking, le faux par usage puis longueur du nom. S'appuyer sur
        // l'ordre recu ferait du resolveur une regle qui change avec l'implementation.
        val rendu = matchFor(listOf(RIZ_ROND_CRU, RIZ_BLANC_CUIT), "riz")

        assertEquals(RIZ_BLANC_CUIT, rendu.food)
    }

    private companion object {
        const val TOLERANCE = 0.001

        val RIZ = ciqual("Riz")
        val RIZ_BLANC_CUIT = ciqual("Riz blanc, cuit")
        val RIZ_COMPLET_CRU = ciqual("Riz complet, cru")
        val RIZ_SAUVAGE_CRU = ciqual("Riz sauvage, cru")
        val RIZ_BASMATI_CUIT = ciqual("Riz basmati, cuit")
        val RIZ_ROND_CRU = ciqual("Riz rond dessert, cru")

        /** Ce que `LIKE '%riz%'` ramene et que le poids des produits de marque ecarte. */
        val CHORIZO = Food(
            id = FoodId("chorizo"),
            source = FoodSource.OFF,
            name = "Chorizo iberique pur porc tranche fin",
            per100g = NutrientValues(kcal = 450.0),
        )

        fun ciqual(name: String) = Food(
            id = FoodId(name),
            source = FoodSource.CIQUAL,
            name = name,
            per100g = NutrientValues(kcal = 100.0),
        )
    }
}
