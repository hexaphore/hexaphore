package app.hexaphore.feature.home

import androidx.compose.ui.unit.dp
import app.hexaphore.core.designsystem.theme.Spacing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * La semaine que le bandeau montre, et les jours qu'il n'ouvre pas.
 *
 * **Une semaine calendaire, pas sept jours glissants.** Le bandeau plaçait
 * aujourd'hui toujours à droite, ce qui se lisait « il y a six jours, il y a cinq
 * jours… » et obligeait à compter pour retrouver hier. Une semaine se lit d'un coup
 * d'œil, et sa géométrie ne bouge pas d'un jour à l'autre.
 */
class CalendarUiStateTest {
    @Test
    fun `la semaine commence au premier jour donne`() {
        // **Deux locales et non une**, et c'est ce qui fait le test : sur une machine
        // francaise, comparer au premier jour de la locale laissait passer un « lundi »
        // ecrit en dur. La campagne de defaite l'a montre.
        assertEquals(DayOfWeek.MONDAY, semaine(DayOfWeek.MONDAY).first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, semaine(DayOfWeek.SUNDAY).first().dayOfWeek)
    }

    @Test
    fun `aujourd hui reste dans la semaine, quel que soit le premier jour`() {
        assertTrue(MERCREDI in semaine(DayOfWeek.MONDAY))
        assertTrue(MERCREDI in semaine(DayOfWeek.SUNDAY))
        assertTrue(MERCREDI in semaine(DayOfWeek.SATURDAY))
    }

    @Test
    fun `la semaine d un premier jour dominical commence la veille du lundi`() {
        // Un mercredi, avec la semaine commencant dimanche : elle part du dimanche
        // precedent, pas du lundi.
        assertEquals(MERCREDI.minusDays(3), semaine(DayOfWeek.SUNDAY).first())
    }

    @Test
    fun `la semaine porte sept jours consecutifs`() {
        val semaine = semaine(DayOfWeek.MONDAY)

        assertEquals(7, semaine.size)
        assertEquals(semaine.first().plusDays(6), semaine.last())
    }

    @Test
    fun `aujourd hui est dans la semaine, et pas forcement a la fin`() {
        // C'est tout le changement : un mercredi, aujourd'hui est au milieu.
        val semaine = semaine(DayOfWeek.MONDAY)

        assertTrue(MERCREDI in semaine)
        assertTrue(semaine.last().isAfter(MERCREDI), "la semaine continue apres aujourd'hui")
    }

    @Test
    fun `la semaine ne bouge pas d un jour a l autre`() {
        // La geometrie du bandeau est stable : sept cases, aux memes dates, tant qu'on
        // reste dans la meme semaine. Avec sept jours glissants, tout se decalait
        // chaque matin.
        val lundi = CalendarUiState(today = MERCREDI.with(DayOfWeek.MONDAY), firstDayOfWeek = DayOfWeek.MONDAY).week
        val jeudi = CalendarUiState(today = MERCREDI.with(DayOfWeek.THURSDAY), firstDayOfWeek = DayOfWeek.MONDAY).week

        assertEquals(lundi, jeudi)
    }

    @Test
    fun `un jour a venir est reconnu comme tel`() {
        val etat = CalendarUiState(today = MERCREDI)

        assertTrue(etat.isFuture(MERCREDI.plusDays(1)))
        assertFalse(etat.isFuture(MERCREDI), "aujourd'hui n'est pas a venir")
        assertFalse(etat.isFuture(MERCREDI.minusDays(1)))
    }

    // --- La taille des pastilles ------------------------------------------------

    @Test
    fun `sept pastilles tiennent dans un ecran etroit`() {
        // **Le defaut rapporte a l'usage** : sept pastilles de 44 dp plus leurs marges
        // depassaient l'ecran, et la septieme se faisait ecraser.
        val largeur = 320.dp

        val total = cellDiameter(largeur) * 7 + Spacing.xs * 8

        assertTrue(total <= largeur, "sept pastilles et leurs marges tiennent dans $largeur, or elles font $total")
    }

    @Test
    fun `la pastille grandit avec la largeur, jusqu a une borne`() {
        assertTrue(cellDiameter(360.dp) > cellDiameter(320.dp), "un ecran plus large donne des pastilles plus grandes")
        assertEquals(MaxCellDiameter, cellDiameter(1200.dp), "une tablette ne montre pas sept medaillons")
    }

    @Test
    fun `la pastille ne descend jamais sous sa taille minimale`() {
        // En deca, le chiffre du jour cesse d'etre lisible : mieux vaut deborder que
        // montrer sept points illisibles.
        assertEquals(MinCellDiameter, cellDiameter(120.dp))
    }

    private fun semaine(first: DayOfWeek) = CalendarUiState(today = MERCREDI, firstDayOfWeek = first).week

    private companion object {
        /** Un mercredi : au milieu de sa semaine, donc des jours avant et après. */
        val MERCREDI: LocalDate = LocalDate.of(2026, 8, 19)
    }
}
