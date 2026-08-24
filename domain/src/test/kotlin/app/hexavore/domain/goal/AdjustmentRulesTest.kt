package app.hexavore.domain.goal

import app.hexavore.domain.profile.WeightEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Les trois règles de l'adaptation, éprouvées **directement**.
 *
 * Pas seulement à travers le cas d'usage : la campagne de défaite l'a montré. La
 * fenêtre d'adhérence y survivait à sa propre suppression, parce que le cas d'usage lit
 * déjà le journal borné à quatorze jours — rien ne pouvait donc arriver de trop vieux,
 * et la borne de la règle ne servait qu'à se répéter. Une règle qu'aucun appelant ne
 * peut mettre en défaut n'est pas éprouvée par lui.
 */
class AdjustmentRulesTest {
    // --- L'adherence ------------------------------------------------------------

    @Test
    fun `dix des quatorze derniers jours suffisent`() {
        assertTrue(jours(0..9).adherentOn(AUJOURD_HUI))
    }

    @Test
    fun `neuf ne suffisent pas`() {
        assertFalse(jours(0..8).adherentOn(AUJOURD_HUI))
    }

    @Test
    fun `un jour plus vieux que la fenetre ne compte pas`() {
        // Neuf jours dans la quinzaine, et dix ailleurs. Sans la borne, l'adherence
        // se calculerait sur toute l'histoire du journal : quelqu'un qui a note trente
        // jours l'an dernier serait declare assidu aujourd'hui.
        val journal = jours(0..8) + jours(30..39)

        assertFalse(journal.adherentOn(AUJOURD_HUI))
    }

    @Test
    fun `le quatorzieme jour compte encore`() {
        // La fenetre couvre J-13 .. J : quatorze jours, bornes comprises.
        val journal = jours(0..8) + setOf(AUJOURD_HUI.minusDays(13))

        assertTrue(journal.adherentOn(AUJOURD_HUI))
    }

    @Test
    fun `le quinzieme jour ne compte plus`() {
        val journal = jours(0..8) + setOf(AUJOURD_HUI.minusDays(14))

        assertFalse(journal.adherentOn(AUJOURD_HUI))
    }

    // --- La correction ------------------------------------------------------------

    @Test
    fun `un ecart d un kilo par semaine vaut onze cents kilocalories, bornees`() {
        // 1 × 7700 / 7 = 1100, ramene a 150.
        assertEquals(MAX_ADJUSTMENT_KCAL, correctionKcal(1.0), TOLERANCE)
        assertEquals(-MAX_ADJUSTMENT_KCAL, correctionKcal(-1.0), TOLERANCE)
    }

    @Test
    fun `un petit ecart passe sous la borne`() {
        // 0,1 × 7700 / 7 = 110, en deca de la borne : la correction n'est pas rabotee.
        assertEquals(110.0, correctionKcal(0.1), TOLERANCE)
    }

    // --- La persistance -------------------------------------------------------------

    @Test
    fun `sans deuxieme fenetre, il n y a pas de persistance`() {
        // Trois semaines de pesees sont necessaires : la persistance compare deux
        // pentes, et une pente compare deux moyennes.
        assertNull(pesees(perteHebdoKg = listOf(0.2, 0.2)).persistentGap(RYTHME_VISE, AUJOURD_HUI))
    }

    @Test
    fun `un ecart sous le seuil ne persiste pas`() {
        // 0,36 perdu pour 0,5 vise : 0,14 d'ecart, sous les 0,15 qui comptent. Le
        // seuil exact ne se teste pas ici -- a cette precision, la difference entre
        // « depasse » et « atteint » est celle de deux soustractions flottantes, et
        // un cas qui en depend mesurerait l'arithmetique plutot que la regle.
        val journal = pesees(perteHebdoKg = listOf(0.36, 0.36, 0.36))

        assertNull(journal.persistentGap(RYTHME_VISE, AUJOURD_HUI))
    }

    @Test
    fun `l ecart rendu est celui de la semaine en cours`() {
        // Deux semaines au-dela du seuil, mais pas du meme montant : c'est le plus
        // recent qui pilote la correction.
        val journal = pesees(perteHebdoKg = listOf(0.1, 0.1, 0.2))

        assertEquals(-0.3, journal.persistentGap(RYTHME_VISE, AUJOURD_HUI)!!, TOLERANCE)
    }

    private fun jours(offsets: IntRange) = offsets.map { AUJOURD_HUI.minusDays(it.toLong()) }.toSet()

    /** Trois pesées par semaine, au poids qu'impose la perte demandée. */
    private fun pesees(perteHebdoKg: List<Double>): List<WeightEntry> {
        var poids = DEPART_KG
        return perteHebdoKg.flatMapIndexed { semaine, perte ->
            poids -= perte
            val fin = AUJOURD_HUI.minusDays(((perteHebdoKg.size - 1 - semaine) * SEMAINE).toLong())
            (0 until PESEES_PAR_SEMAINE).map { WeightEntry(fin.minusDays(it.toLong()), poids) }
        }
    }

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 22)
        const val SEMAINE = 7
        const val PESEES_PAR_SEMAINE = 3
        const val DEPART_KG = 90.0

        /** Un demi-kilo perdu par semaine, comme le cap du décor de la tranche. */
        const val RYTHME_VISE = -0.5
        const val TOLERANCE = 1e-9
    }
}
