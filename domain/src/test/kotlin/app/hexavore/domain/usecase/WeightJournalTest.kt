package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.WeightEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le journal de poids : ce qu'on y écrit, et la courbe qu'on en lit.
 */
class WeightJournalTest {
    private val weights = InMemoryWeightLog()
    private val goals = InMemoryGoals()

    // --- Noter une pesee --------------------------------------------------------

    @Test
    fun `une pesee du jour est notee`() = runTest {
        assertTrue(recordWeight()(WeightEntry(AUJOURD_HUI, 82.0)))

        assertEquals(listOf(WeightEntry(AUJOURD_HUI, 82.0)), weights.entries)
    }

    @Test
    fun `une pesee datee de demain est refusee`() = runTest {
        // docs/02 interdit la saisie dans le futur. Une pesee a venir dechirerait en
        // plus la moyenne mobile, dont la fenetre finit toujours au jour dit.
        assertFalse(recordWeight()(WeightEntry(AUJOURD_HUI.plusDays(1), 82.0)))

        assertEquals(emptyList<WeightEntry>(), weights.entries)
    }

    @Test
    fun `un poids nul ou negatif est refuse`() = runTest {
        assertFalse(recordWeight()(WeightEntry(AUJOURD_HUI, 0.0)))
        assertFalse(recordWeight()(WeightEntry(AUJOURD_HUI, -5.0)))

        assertEquals(emptyList<WeightEntry>(), weights.entries)
    }

    @Test
    fun `une pesee ancienne est acceptee`() = runTest {
        // Rattraper une pesee oubliee est courant, et la moyenne mobile la veut.
        assertTrue(recordWeight()(WeightEntry(AUJOURD_HUI.minusDays(3), 82.0)))
    }

    // --- La courbe --------------------------------------------------------------

    @Test
    fun `sans pesee, la courbe est vide`() = runTest {
        val tendance = GetWeightTrend(weights, goals)().first()

        assertEquals(emptyList<TrendPoint>(), tendance.points)
        assertNull(tendance.latest)
    }

    @Test
    fun `chaque pesee donne un point, du plus ancien au plus recent`() = runTest {
        peser(AUJOURD_HUI, 80.0)
        peser(AUJOURD_HUI.minusDays(2), 82.0)

        val tendance = GetWeightTrend(weights, goals)().first()

        assertEquals(listOf(AUJOURD_HUI.minusDays(2), AUJOURD_HUI), tendance.points.map { it.date })
        assertEquals(WeightEntry(AUJOURD_HUI, 80.0), tendance.latest)
    }

    @Test
    fun `un point sans assez de voisins n a pas de moyenne`() = runTest {
        // Le trace lisse a un trou, et non un zero ni une interpolation : relier deux
        // moyennes separees par trois semaines de silence dessinerait une progression
        // que personne n'a mesuree.
        peser(AUJOURD_HUI.minusDays(30), 90.0)
        repeat(3) { peser(AUJOURD_HUI.minusDays(it.toLong()), 80.0) }

        val tendance = GetWeightTrend(weights, goals)().first()

        assertNull(tendance.points.first().averageKg, "la pesee isolee d'il y a un mois ne se lisse pas")
        assertEquals(80.0, tendance.points.last().averageKg)
    }

    @Test
    fun `la courbe porte la trajectoire de l objectif courant`() = runTest {
        peser(DEBUT, 90.0)
        goals.replace(objectif())

        val tendance = GetWeightTrend(weights, goals)().first()

        assertEquals(DEBUT, tendance.aim?.from)
        assertEquals(90.0, tendance.aim?.fromKg)
        assertEquals(80.0, tendance.aim?.toKg)
    }

    @Test
    fun `sans objectif courant, la courbe n a pas de trajectoire`() = runTest {
        peser(DEBUT, 90.0)

        assertNull(GetWeightTrend(weights, goals)().first().aim)
    }

    @Test
    fun `la pente reelle se lit sur la courbe`() = runTest {
        // Trois pesees a 81 la semaine d'avant, trois a 80 celle-ci : un kilo par
        // semaine, et c'est ce chiffre que l'adaptation comparera au cap annonce.
        repeat(3) { peser(AUJOURD_HUI.minusDays(7L + it), 81.0) }
        repeat(3) { peser(AUJOURD_HUI.minusDays(it.toLong()), 80.0) }

        val tendance = GetWeightTrend(weights, goals)().first()

        assertEquals(-1.0, tendance.weeklySlopeOn(AUJOURD_HUI)!!, TOLERANCE)
    }

    private suspend fun peser(date: LocalDate, kg: Double) = weights.record(WeightEntry(date, kg))

    private fun recordWeight() = RecordWeight(weights, FixedClock.atNoon(AUJOURD_HUI))

    private fun objectif() = Goal(
        id = GoalId("g"),
        startedAt = DEBUT,
        origin = GoalOrigin.CALCULATED,
        strategy = GoalStrategy.LOSE,
        targetWeightKg = 80.0,
        targetDate = DEBUT.plusDays(140),
        daily = DailyGoal(kcal = 2000.0, protein = 150.0, carbs = 200.0, sugars = 50.0, fat = 60.0, fiber = 30.0),
    )

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 22)
        val DEBUT: LocalDate = LocalDate.of(2026, 7, 1)
        const val TOLERANCE = 1e-9
    }
}
