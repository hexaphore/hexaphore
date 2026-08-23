package app.hexaphore.domain.usecase

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemorySelectedDay
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.domain.diary.EntrySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le jour sur lequel un brouillon s'écrit.
 *
 * **C'est ce qui permet de rattraper un repas oublié.** L'accueil porte une date, et le
 * bouton d'ajout écrit sur elle — sans quoi un plat noté depuis un jour passé
 * atterrirait aujourd'hui, en silence et au mauvais endroit.
 *
 * Le jour voyage par un port et non par la navigation : entre l'accueil et l'écran de
 * validation il y a la recherche, un scan, ou une modale d'IA, et aucune de ces routes
 * n'a de raison de porter une date.
 */
class DraftDayTest {
    private val selected = InMemorySelectedDay(today = AUJOURD_HUI)

    @Test
    fun `sans jour choisi, un brouillon est date d aujourd hui`() {
        assertEquals(AUJOURD_HUI, create()(EntrySource.MANUAL).date)
    }

    @Test
    fun `un jour passe choisi date le brouillon de ce jour-la`() {
        selected.select(MARDI)

        assertEquals(MARDI, create()(EntrySource.MANUAL).date)
    }

    @Test
    fun `revenir a aujourd hui redate les brouillons suivants`() {
        selected.select(MARDI)
        selected.select(null)

        assertEquals(AUJOURD_HUI, create()(EntrySource.MANUAL).date)
    }

    @Test
    fun `choisir aujourd hui range null, et non la date du jour`() {
        // Sans cette regle, un ecran laisse ouvert pendant la nuit continuerait
        // d'afficher la veille jusqu'a ce que quelqu'un touche une autre pastille.
        selected.select(AUJOURD_HUI)

        assertEquals(null, selected.current())
    }

    @Test
    fun `un brouillon de plusieurs lignes suit le meme jour`() {
        // Celui d'une reconnaissance : il n'y a aucune raison qu'une photo prise pour
        // rattraper hier atterrisse aujourd'hui.
        selected.select(MARDI)

        assertEquals(MARDI, create()(EntrySource.PHOTO_AI, emptyList()).date)
    }

    private fun create() = CreateDraft(FixedClock.atNoon(AUJOURD_HUI), SequentialIdGenerator("l"), selected)

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 23)
        val MARDI: LocalDate = LocalDate.of(2026, 8, 18)
    }
}
