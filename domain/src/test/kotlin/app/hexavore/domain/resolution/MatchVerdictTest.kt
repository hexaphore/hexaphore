package app.hexavore.domain.resolution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Les deux bornes de [docs/04][sources], et de quel côté elles tombent.
 *
 * Ce que la valeur des seuils vaut se juge ailleurs — sur de vraies fiches, dans
 * `FoodRankingTest`. Ici on ne tient qu'une chose : une confiance **égale** au seuil
 * appartient au verdict du haut, et pas à celui du bas.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
class MatchVerdictTest {
    @Test
    fun `une confiance egale au seuil appartient au verdict du haut`() {
        assertEquals(MatchVerdict.AUTOMATIC, verdictFor(0.75))
        assertEquals(MatchVerdict.REVIEW, verdictFor(0.40))
    }

    @Test
    fun `juste en dessous d un seuil, le verdict descend`() {
        assertEquals(MatchVerdict.REVIEW, verdictFor(0.7499))
        assertEquals(MatchVerdict.NONE, verdictFor(0.3999))
    }
}
