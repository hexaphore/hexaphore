package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.InMemorySelectedDay
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FavoriteComponent
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.coroutines.test.runTest
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
 *
 * **La règle s'éprouve sur les portes, pas seulement sur la fabrique.** Elle n'était
 * vérifiée que sur [CreateDraft], et le favori — qui bâtissait son brouillon à la main —
 * est passé à côté sans qu'aucun cas ne bronche.
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

    @Test
    fun `un favori rejoue s ecrit sur le jour regarde`() = runTest {
        // Le defaut qui a motive ce cas : rejouer un plat favori depuis la veille
        // l'enregistrait sur aujourd'hui, en silence et au mauvais endroit.
        selected.select(MARDI)

        assertEquals(MARDI, favori().date)
    }

    @Test
    fun `sans jour choisi, un favori rejoue est date d aujourd hui`() = runTest {
        assertEquals(AUJOURD_HUI, favori().date)
    }

    /** Un favori d'une ligne, rejoué par la porte même que l'écran ouvre. */
    private suspend fun favori(): EntryDraft {
        val favoris = InMemoryFavoriteDishes(
            initial = listOf(
                FavoriteDish(
                    id = SALADE,
                    name = "Salade",
                    components = listOf(
                        FavoriteComponent(
                            name = "Laitue",
                            quantity = 100.0,
                            unit = QuantityUnit.Gram,
                            grams = 100.0,
                            values = NutrientValues(kcal = 15.0),
                        ),
                    ),
                ),
            ),
        )
        val rejeu = GetFavoriteDraft(favoris, InMemoryFoodCatalog(), create(), SequentialIdGenerator("f"))
        return requireNotNull(rejeu(SALADE))
    }

    private fun create() = CreateDraft(FixedClock.atNoon(AUJOURD_HUI), SequentialIdGenerator("l"), selected)

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 23)
        val MARDI: LocalDate = LocalDate.of(2026, 8, 18)
        val SALADE = FavoriteDishId("favori-salade")
    }
}
