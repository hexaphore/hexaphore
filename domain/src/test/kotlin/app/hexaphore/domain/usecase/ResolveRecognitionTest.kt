package app.hexaphore.domain.usecase

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.domain.ai.EstimatedUnit
import app.hexaphore.domain.ai.Recognition
import app.hexaphore.domain.ai.RecognizedItem
import app.hexaphore.domain.diary.EntrySource
import app.hexaphore.domain.diary.QuantityUnit
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.food.FoodFilter
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.food.FoodServing
import app.hexaphore.domain.food.FoodSource
import app.hexaphore.domain.nutrition.NutrientValues
import app.hexaphore.domain.resolution.MatchVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le chaînage : reconnaître, résoudre, convertir, remplir.
 *
 * Ce qui se juge ici est **l'assemblage**, pas les pièces. La recherche de candidats, le
 * score, le verdict et la conversion ont chacun leurs cas ailleurs ; les rejouer ici
 * ferait deux jeux à corriger pour une règle. Ce qui n'existe qu'ici : l'ordre des deux
 * étapes, le sort d'un libellé introuvable, et ce que la ligne garde de la proposition.
 */
class ResolveRecognitionTest {
    private val ids = SequentialIdGenerator()

    @Test
    fun `chaque ligne reconnue devient une ligne de brouillon`() = runTest {
        val draft = resolve(item("pomme", 2.0, EstimatedUnit.PIECE), item("pain de mie", 1.0, EstimatedUnit.SLICE))

        assertEquals(listOf("Pomme", "Pain de mie"), draft.lines.map { it.name })
        assertEquals(EntrySource.TEXT_AI, draft.source)
    }

    @Test
    fun `la portion nommee de la fiche l emporte sur le forfait`() = runTest {
        // La regle de D73, vue depuis le chainage : la fiche dit qu'une tranche pese
        // 33 g, et le forfait de docs/04 en dirait 30. Convertir avant de savoir
        // quelle fiche on vise appliquerait le forfait a tous les coups.
        val draft = resolve(item("pain de mie", 2.0, EstimatedUnit.SLICE))

        assertEquals(66.0, draft.lines.single().quantity)
        assertEquals(QuantityUnit.Gram, draft.lines.single().unit)
        assertFalse(draft.lines.single().suggestion!!.estimated, "la fiche a mesure, rien n a ete devine")
    }

    @Test
    fun `une conversion au forfait se signale`() = runTest {
        // « un bol » de riz : aucune portion nommee sur la fiche, donc le forfait --
        // et docs/04 exige que la ligne le dise.
        val draft = resolve(item("riz", 1.0, EstimatedUnit.BOWL))

        assertTrue(draft.lines.single().suggestion!!.estimated)
    }

    @Test
    fun `un libelle introuvable garde son nom et sa quantite, sans valeurs`() = runTest {
        // L'ecarter ferait disparaitre un aliment que l'utilisateur a mange, et il ne
        // saurait pas lequel. La ligne arrive incomplete : l'ecran de validation dit
        // deja qu'une ligne sans energie n'est pas enregistrable.
        val draft = resolve(item("tofu fume au sesame", 100.0, EstimatedUnit.G))

        val line = draft.lines.single()
        assertEquals("tofu fume au sesame", line.name)
        assertEquals(100.0, line.quantity)
        assertNull(line.values.kcal)
        assertNull(line.foodId)
    }

    @Test
    fun `les valeurs suivent la quantite reconnue`() = runTest {
        // 200 g d'une fiche a 52 kcal pour 100 g. Sans cette regle de trois, la ligne
        // porterait les valeurs de la portion par defaut de la fiche, qui n'a rien a
        // voir avec ce que le modele a vu.
        val draft = resolve(item("pomme", 200.0, EstimatedUnit.G))

        assertEquals(104.0, draft.lines.single().values.kcal)
    }

    @Test
    fun `la ligne garde les deux incertitudes separement`() = runTest {
        // Le modele est sur de lui, le catalogue beaucoup moins -- il n'a que des
        // preparations, aucune fiche generique. Les moyenner ferait disparaitre le
        // seul cas qui compte, celui ou l'une des deux doute.
        val draft = resolve(item("pomme de terre", 1.0, EstimatedUnit.PIECE, confidence = 0.95f))

        val suggestion = draft.lines.single().suggestion!!
        assertEquals(0.95f, suggestion.confidence)
        assertEquals(MatchVerdict.REVIEW, suggestion.verdict)
        assertEquals(listOf("Pomme de terre frite au four"), suggestion.alternatives.map { it.name })
    }

    @Test
    fun `une correspondance certaine ne propose aucune alternative`() = runTest {
        val draft = resolve(item("pomme", 1.0, EstimatedUnit.PIECE))

        val suggestion = draft.lines.single().suggestion!!
        assertEquals(MatchVerdict.AUTOMATIC, suggestion.verdict)
        assertEquals(emptyList<Food>(), suggestion.alternatives)
    }

    @Test
    fun `une analyse sans ligne rend quand meme un brouillon saisissable`() = runTest {
        // Le cas ne devrait pas se presenter -- une analyse sans ligne exploitable est
        // une erreur, pas une reussite -- mais un brouillon a zero ligne n'aurait rien
        // a afficher, et l'ecran n'a pas d'etat pour ca.
        val draft = resolve()

        assertEquals(1, draft.lines.size)
        assertTrue(draft.lines.single().blank)
    }

    private suspend fun resolve(vararg items: RecognizedItem) = ResolveRecognition(
        resolve = ResolveFoodLabel(CATALOGUE),
        create = CreateDraft(FixedClock.atNoon(JOUR), ids),
    )(Recognition(items.toList()), EntrySource.TEXT_AI)

    private fun item(label: String, quantity: Double, unit: EstimatedUnit, confidence: Float = 0.9f) =
        RecognizedItem(label = label, quantity = quantity, unit = unit, confidence = confidence)

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 3, 14)

        val POMME = ciqual("Pomme", kcal = 52.0, servings = listOf(FoodServing("1 pomme", 150.0)))
        val PAIN = ciqual("Pain de mie", kcal = 270.0, servings = listOf(FoodServing("1 tranche", 33.0)))
        val RIZ = ciqual("Riz blanc cuit", kcal = 130.0)
        val POMME_DE_TERRE_VAPEUR = ciqual("Pomme de terre vapeur", kcal = 85.0)
        val POMME_DE_TERRE_FRITE = ciqual("Pomme de terre frite au four", kcal = 190.0)

        /**
         * Un catalogue qui récite, comme celui de `ResolveFoodLabelTest`.
         *
         * Un faux fidèle chercherait vraiment, donc ce test dépendrait de la règle de
         * correspondance qu'un autre éprouve déjà — et une ligne verte ici ne dirait
         * plus si c'est le chaînage ou la recherche qui fonctionne.
         */
        val CATALOGUE = object : FoodSearch {
            override fun search(query: String, filter: FoodFilter, limit: Int): Flow<List<Food>> = flowOf(
                when (query) {
                    "pomme" -> listOf(POMME)
                    "pain de mie" -> listOf(PAIN)
                    "riz" -> listOf(RIZ)
                    "pomme de terre" -> listOf(POMME_DE_TERRE_VAPEUR, POMME_DE_TERRE_FRITE)
                    else -> emptyList()
                },
            )
        }

        fun ciqual(name: String, kcal: Double, servings: List<FoodServing> = emptyList()) = Food(
            id = FoodId(name),
            source = FoodSource.CIQUAL,
            name = name,
            per100g = NutrientValues(kcal = kcal),
            servings = servings,
        )
    }
}
