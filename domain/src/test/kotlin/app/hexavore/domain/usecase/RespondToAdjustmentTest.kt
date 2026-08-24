package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryAdjustmentSettings
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.goal.AdjustmentSuggestion
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Les trois issues d'une suggestion.
 *
 * **Aucune suggestion n'est appliquée sans accord explicite** — c'est un critère de fin
 * de tranche ([docs/12][plan]), et il tient à une propriété simple : *accepter* est le
 * seul chemin qui écrit un objectif. Les deux autres cas l'affirment.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
class RespondToAdjustmentTest {
    private val goals = InMemoryGoals(listOf(COURANT))
    private val settings = InMemoryAdjustmentSettings()

    @Test
    fun `accepter ouvre une version, il n en modifie aucune`() = runTest {
        // D04 : un objectif n'est jamais modifie en place. L'ancien recoit une date de
        // fin, le neuf prend la journee.
        respond()(AdjustmentResponse.ACCEPT, SUGGESTION)

        assertEquals(2, goals.all.size)
        assertEquals(AUJOURD_HUI, goals.all.single { it.active }.startedAt)
        assertFalse(goals.all.single { it.id == COURANT.id }.active, "l'ancien est clos")
    }

    @Test
    fun `l objectif accepte porte les six chiffres proposes`() = runTest {
        respond()(AdjustmentResponse.ACCEPT, SUGGESTION)

        assertEquals(PROPOSE, goals.all.single { it.active }.daily)
    }

    @Test
    fun `l objectif accepte dit d ou il vient`() = runTest {
        respond()(AdjustmentResponse.ACCEPT, SUGGESTION)

        assertEquals(GoalOrigin.ADJUSTMENT, goals.all.single { it.active }.origin)
    }

    @Test
    fun `le cap ne bouge pas`() = runTest {
        // Seuls les six chiffres changent : la strategie, le poids vise et l'echeance
        // decrivent ce qu'on veut, et l'adaptation ne le renegocie pas.
        respond()(AdjustmentResponse.ACCEPT, SUGGESTION)

        val neuf = goals.all.single { it.active }

        assertEquals(COURANT.strategy, neuf.strategy)
        assertEquals(COURANT.targetWeightKg, neuf.targetWeightKg)
        assertEquals(COURANT.targetDate, neuf.targetDate)
    }

    @Test
    fun `accepter fait taire l adaptation pour deux semaines`() = runTest {
        respond()(AdjustmentResponse.ACCEPT, SUGGESTION)

        assertEquals(AUJOURD_HUI, settings.setup.lastAcceptedOn)
        assertFalse(settings.setup.openOn(AUJOURD_HUI.plusDays(13)))
        assertTrue(settings.setup.openOn(AUJOURD_HUI.plusDays(14)))
    }

    @Test
    fun `ignorer n ecrit aucun objectif`() = runTest {
        respond()(AdjustmentResponse.IGNORE, SUGGESTION)

        assertEquals(listOf(COURANT), goals.all)
        assertEquals(AUJOURD_HUI, settings.setup.lastIgnoredOn)
        assertNull(settings.setup.lastAcceptedOn, "un refus n'est pas un ajustement accepte")
    }

    @Test
    fun `ne plus proposer n ecrit aucun objectif`() = runTest {
        respond()(AdjustmentResponse.STOP, SUGGESTION)

        assertEquals(listOf(COURANT), goals.all)
        assertFalse(settings.setup.enabled)
    }

    @Test
    fun `sans objectif courant, accepter n ecrit rien`() = runTest {
        // L'objectif a pu etre remplace pendant que la carte etait a l'ecran. Ecrire
        // quand meme creerait une version sortie de nulle part.
        val vide = InMemoryGoals()

        RespondToAdjustment(vide, settings, SequentialIdGenerator(), FixedClock.atNoon(AUJOURD_HUI))(
            AdjustmentResponse.ACCEPT,
            SUGGESTION,
        )

        assertEquals(emptyList<Goal>(), vide.all)
        assertNull(settings.setup.lastAcceptedOn)
    }

    private fun respond() =
        RespondToAdjustment(goals, settings, SequentialIdGenerator(), FixedClock.atNoon(AUJOURD_HUI))

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 22)

        val ACTUEL = DailyGoal(kcal = 2400.0, protein = 150.0, carbs = 255.0, sugars = 60.0, fat = 67.0, fiber = 30.0)
        val PROPOSE = DailyGoal(kcal = 2250.0, protein = 150.0, carbs = 218.0, sugars = 56.0, fat = 62.0, fiber = 30.0)

        val COURANT = Goal(
            id = GoalId("courant"),
            startedAt = AUJOURD_HUI.minusDays(60),
            origin = GoalOrigin.CALCULATED,
            strategy = GoalStrategy.LOSE,
            targetWeightKg = 80.0,
            targetDate = AUJOURD_HUI.plusDays(80),
            daily = ACTUEL,
        )

        val SUGGESTION = AdjustmentSuggestion(
            actualWeeklyKg = -0.2,
            aimedWeeklyKg = -0.5,
            current = ACTUEL,
            proposed = PROPOSE,
        )
    }
}
