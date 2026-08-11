package app.hexaphore.domain.goal

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Quels jours un objectif couvre, et lesquels il ne couvre pas.
 *
 * `Goal.coversOn` est publique et porte la convention « début inclus, fin exclue ».
 * Rien ne l'éprouvait : les tests qui en dépendent passent tous par `InMemoryGoals`,
 * dont le `maxByOrNull { startedAt }` rend le bon objectif **même si la borne est
 * relâchée**, exactement comme le `ORDER BY started_at DESC LIMIT 1` le fait côté SQL.
 * Deux mécanismes de tri masquaient donc la même règle des deux côtés.
 *
 * Ici, la règle est interrogée seule, sans historique ni tri pour la rattraper.
 */
class GoalCoverageTest {
    @Test
    fun `le jour de debut est couvert`() {
        assertTrue(ouvert.coversOn(DEBUT), "borne de debut incluse")
    }

    @Test
    fun `la veille du debut ne l est pas`() {
        assertFalse(ouvert.coversOn(DEBUT.minusDays(1)))
    }

    @Test
    fun `un objectif qui court couvre tout jour ulterieur`() {
        assertTrue(ouvert.coversOn(DEBUT.plusYears(1)))
    }

    @Test
    fun `le jour de fin n est pas couvert`() {
        // Borne de fin exclue : ce jour-la appartient au successeur. Sans cette
        // convention, une journee releverait de deux objectifs et son resume
        // dependrait de l'ordre de lecture.
        assertFalse(clos.coversOn(FIN), "borne de fin exclue")
    }

    @Test
    fun `la veille de la fin est couverte`() {
        assertTrue(clos.coversOn(FIN.minusDays(1)))
    }

    @Test
    fun `un jour posterieur a la fin ne l est pas`() {
        assertFalse(clos.coversOn(FIN.plusDays(1)))
    }

    @Test
    fun `un objectif clos le jour de son debut ne couvre aucune journee`() {
        // Ce que produit une correction faite le jour meme : l'ancien objectif est
        // clos a sa propre date de debut. Il n'a jamais rien couvert, et c'est juste
        // -- le nouveau prend la journee entiere. Le cas arrivera des que les
        // reglages profil existeront.
        val remplaceLeJourMeme = ouvert.copy(endedAt = DEBUT)

        assertFalse(remplaceLeJourMeme.coversOn(DEBUT))
        assertFalse(remplaceLeJourMeme.coversOn(DEBUT.minusDays(1)))
        assertFalse(remplaceLeJourMeme.coversOn(DEBUT.plusDays(1)))
    }

    @Test
    fun `seul un objectif sans date de fin court`() {
        assertTrue(ouvert.active)
        assertFalse(clos.active)
    }

    private val ouvert = goal(endedAt = null)
    private val clos = goal(endedAt = FIN)

    private fun goal(endedAt: LocalDate?) = Goal(
        id = GoalId("g"),
        startedAt = DEBUT,
        endedAt = endedAt,
        origin = GoalOrigin.CALCULATED,
        strategy = GoalStrategy.MAINTAIN,
        daily = DailyGoal(kcal = 2_000.0, protein = 112.0, carbs = 223.0, sugars = 50.0, fat = 67.0, fiber = 28.0),
    )

    private companion object {
        val DEBUT: LocalDate = LocalDate.of(2026, 6, 1)
        val FIN: LocalDate = LocalDate.of(2026, 7, 1)
    }
}
