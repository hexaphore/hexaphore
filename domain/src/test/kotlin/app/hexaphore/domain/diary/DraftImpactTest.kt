package app.hexaphore.domain.diary

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryDiaryRepository
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.usecase.GetDaySummary
import app.hexaphore.domain.usecase.LogDish
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DraftImpactTest {
    private val diary = InMemoryDiaryRepository()
    private val clock = FixedClock.atNoon(JOUR)
    private val ids = SequentialIdGenerator()

    private val logDish = LogDish(diary, clock, ids)
    private val getDaySummary = GetDaySummary(diary, clock)

    @Test
    fun `le restant retranche le brouillon de l objectif`() = runTest {
        val journee = getDaySummary(JOUR).first()

        val impact = journee.impactOf(brouillon(ligne("a", kcal = 500.0)))

        assertEquals(500.0, impact.draftKcal)
        assertEquals(journee.goal.kcal - 500.0, impact.remainingKcal)
    }

    @Test
    fun `modifier un plat ne le compte pas deux fois`() = runTest {
        // Sans le retrait, corriger un plat de 600 kcal en 700 afficherait un
        // restant ampute de 1 300 : le chiffre le plus visible de l'ecran serait
        // faux precisement au moment ou on relit pour corriger.
        val id = logDish(brouillon(ligne("a", kcal = 600.0)))
        val journee = getDaySummary(JOUR).first()

        val impact = journee.impactOf(brouillon(ligne("a", kcal = 700.0), dishId = id))

        assertEquals(journee.goal.kcal - 700.0, impact.remainingKcal)
    }

    @Test
    fun `un nouveau plat s ajoute a ce qui est deja note`() = runTest {
        logDish(brouillon(ligne("a", kcal = 600.0)))
        val journee = getDaySummary(JOUR).first()

        val impact = journee.impactOf(brouillon(ligne("b", kcal = 400.0)))

        assertEquals(journee.goal.kcal - 1000.0, impact.remainingKcal)
    }

    @Test
    fun `un trou dans une autre macro ne fausse pas le restant`() = runTest {
        // Le restant ne parle que de calories, et les calories ne sont jamais
        // inconnues. Une ligne sans fibres minore le cumul des fibres et rien
        // d'autre : c'est ce qui permet a cet ecran de n'afficher qu'un chiffre.
        logDish(brouillon(ligne("a", kcal = 600.0, fibres = null)))
        val journee = getDaySummary(JOUR).first()

        assertFalse(journee.totals[Macro.FIBER].complete)
        assertTrue(journee.totals[Macro.CALORIES].complete)
        assertEquals(journee.goal.kcal - 800.0, journee.impactOf(brouillon(ligne("b", kcal = 200.0))).remainingKcal)
    }
}
