package app.hexavore.core.common.diary

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemorySelectedDay
import app.hexavore.domain.diary.SelectedDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Ce que **les deux** implémentations du jour regardé doivent tenir.
 *
 * Le port a un vrai — [CurrentSelectedDay], qui normalise contre l'horloge — et un
 * faux, `InMemorySelectedDay`, qui normalise contre une date qu'on lui donne. Rien ne
 * les éprouvait ensemble, et le faux acceptait de ne pas normaliser du tout : sa date
 * du jour était facultative. C'est la configuration exacte que [D53][decisions]
 * décrit — un faux plus indulgent que le vrai, des tests écrits contre le faux — et
 * qui avait déjà coûté quatre défauts livrés.
 *
 * Ce jeu est écrit une fois et exécuté deux fois.
 *
 * **Ce qu'il ne prouve pas.** Que quoi que ce soit à l'écran suive ce flux, ni que le
 * bouton d'ajout écrive bien sur le jour rendu : c'est le rôle de `DraftDayTest`.
 *
 * [decisions]: docs/11-decisions.md
 */
abstract class SelectedDayContract {
    protected abstract fun jour(): SelectedDay

    @Test
    fun `au depart, aucun jour choisi`() {
        assertNull(jour().current(), "rouvrir l'application montre aujourd'hui, et non le dernier jour visite")
    }

    @Test
    fun `un jour passe choisi se retrouve`() {
        val jour = jour()

        jour.select(MARDI)

        assertEquals(MARDI, jour.current())
    }

    @Test
    fun `choisir aujourd hui range null, et non sa date`() {
        // La regle structurante : sans elle, un ecran laisse ouvert pendant la nuit
        // continuerait d'afficher la veille jusqu'a ce que quelqu'un touche une
        // pastille. `null` veut dire aujourd'hui, quel que soit le jour qu'il est.
        val jour = jour()

        jour.select(AUJOURD_HUI)

        assertNull(jour.current())
    }

    @Test
    fun `revenir a aujourd hui efface le jour choisi`() {
        val jour = jour()
        jour.select(MARDI)

        jour.select(null)

        assertNull(jour.current())
    }

    @Test
    fun `le flux porte la meme valeur que la lecture immediate`() = runBlocking {
        // Deux facons de lire, et rien ne garantissait qu'elles disent la meme chose :
        // l'ecran observe le flux, `CreateDraft` lit sans suspendre.
        val jour = jour()
        jour.select(MARDI)

        assertEquals(jour.current(), jour.observe().first())
    }

    @Test
    fun `le flux emet le retour a aujourd hui`() = runBlocking {
        val jour = jour()
        jour.select(MARDI)

        jour.select(null)

        assertNull(jour.observe().first())
    }

    protected companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 23)
        val MARDI: LocalDate = LocalDate.of(2026, 8, 18)
    }
}

/** Le vrai, celui que l'application injecte. */
class CurrentSelectedDayTest : SelectedDayContract() {
    override fun jour(): SelectedDay = CurrentSelectedDay(FixedClock.atNoon(AUJOURD_HUI))
}

/** Le faux, celui contre lequel presque tous les autres tests sont écrits. */
class InMemorySelectedDayTest : SelectedDayContract() {
    override fun jour(): SelectedDay = InMemorySelectedDay(today = AUJOURD_HUI)
}
