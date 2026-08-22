package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.EstimationOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que le repli de l'étape 4 accepte de croire.
 *
 * Le parseur partage sa tolérance avec celui de la reconnaissance — texte autour, JSON
 * tronqué, champs inconnus — et ces cas-là sont éprouvés là-bas. Ce qui n'existe
 * qu'ici : **une liste vide est une réponse valide**, et une valeur absurde vaut
 * inconnu plutôt que zéro.
 */
class EstimationParserTest {
    @Test
    fun `une estimation bien formee rend ses six valeurs`() {
        val brut = """
            {"foods":[{"label":"tofu fume","kcal":180,"protein":16,"carbs":3,"sugars":1,"fat":11,"fiber":2}]}
        """.trimIndent()

        val food = (parseEstimation(brut) as EstimationOutcome.Estimated).foods.single()

        assertEquals("tofu fume", food.label)
        assertEquals(180.0, food.per100g.kcal)
        assertEquals(16.0, food.per100g.protein)
        assertEquals(2.0, food.per100g.fiber)
    }

    @Test
    fun `une valeur nulle traverse comme inconnue`() {
        // Le prompt demande desormais les six cles et fait de `null` la seule facon de
        // dire « je ne sais pas ». Un `null` qui se coercerait en zero ferait entrer
        // une affirmation que personne n'a faite.
        val brut = """
            {"foods":[{"label":"kombucha","kcal":13,"protein":0,"carbs":3,"sugars":3,"fat":0,"fiber":null}]}
        """.trimIndent()

        val food = (parseEstimation(brut) as EstimationOutcome.Estimated).foods.single()

        assertNull(food.per100g.fiber, "inconnu n'est pas zero")
        assertEquals(0.0, food.per100g.fat, "zero, lui, est une mesure")
    }

    @Test
    fun `une liste vide est une reponse et non un echec`() {
        // Le prompt demande d'omettre ce qu'on ne sait pas. Un modele qui ne connait
        // aucun des libelles a raison de se taire, et les lignes restent a completer a
        // la main -- ce qu'elles etaient avant l'appel.
        val outcome = parseEstimation("""{"foods":[]}""")

        assertEquals(EstimationOutcome.Estimated(emptyList()), outcome)
    }

    @Test
    fun `une estimation sans libelle est ecartee`() {
        // Sans lui, elle ne se rattache a aucune ligne : il n'y a rien a en faire.
        val brut = """{"foods":[{"kcal":180},{"label":"tofu fume","kcal":180}]}"""

        val foods = (parseEstimation(brut) as EstimationOutcome.Estimated).foods

        assertEquals(listOf("tofu fume"), foods.map { it.label })
    }

    @Test
    fun `une valeur negative vaut inconnu, jamais zero`() {
        // Un zero est une affirmation. Le projet n'en ecrit pas a la place de
        // l'utilisateur, et surtout pas a partir d'un chiffre absurde.
        val brut = """{"foods":[{"label":"sauce","kcal":-10,"protein":2}]}"""

        val food = (parseEstimation(brut) as EstimationOutcome.Estimated).foods.single()

        assertNull(food.per100g.kcal)
        assertEquals(2.0, food.per100g.protein)
    }

    @Test
    fun `une valeur manquante reste manquante`() {
        val brut = """{"foods":[{"label":"sauce","kcal":90}]}"""

        val food = (parseEstimation(brut) as EstimationOutcome.Estimated).foods.single()

        assertEquals(90.0, food.per100g.kcal)
        assertNull(food.per100g.fiber, "un champ absent n'est pas zero gramme de fibres")
    }

    @Test
    fun `une reponse illisible se dit illisible`() {
        val outcome = parseEstimation("Je ne peux pas vous aider.")

        assertEquals(EstimationOutcome.Failed(AiError.Unparseable), outcome)
    }

    @Test
    fun `du texte autour de la reponse ne gene pas`() {
        val brut = """
            Voici mes estimations :
            {"foods":[{"label":"sauce maison","kcal":250}]}
            Elles sont approximatives.
        """.trimIndent()

        val foods = (parseEstimation(brut) as EstimationOutcome.Estimated).foods

        assertTrue(foods.single().label == "sauce maison", foods.toString())
    }
}
