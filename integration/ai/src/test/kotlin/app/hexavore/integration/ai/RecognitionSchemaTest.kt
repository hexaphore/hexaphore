package app.hexavore.integration.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que le schéma de reconnaissance **exige** de chaque ligne.
 *
 * **Le poids y est exigé, et il a le droit de valoir inconnu.** C'est la même leçon que
 * le schéma d'estimation avait apprise à ses dépens : le décodage contraint de Gemini lit
 * « pas dans `required` » comme « facultatif » et se tait **par défaut**. Un poids
 * facultatif serait donc un poids jamais donné, et le forfait de cent grammes par pièce
 * continuerait de faire cinq cents grammes de cinq cacahuètes.
 *
 * Un schéma ne se vérifie pas à la lecture : il part sur le réseau et c'est le
 * fournisseur qui l'applique. Ces cas affirment sa **forme**, dans les deux dialectes et
 * pour les deux schémas — celui du chemin ordinaire et celui de l'outil de réponse.
 */
class RecognitionSchemaTest {
    @Test
    fun `le poids est exige au meme titre que la quantite`() {
        // Exiger la cle est ce qui interdit le silence par omission, et c'est ce cas
        // qui tombe si on la sort de `required` pour la rendre gentille.
        listOf(true, false).forEach { strict ->
            assertTrue("grams" in requises(ordinaire(strict)), "chemin ordinaire, strict = $strict")
            assertTrue("grams" in requises(outil(strict)), "outil de reponse, strict = $strict")
        }
    }

    @Test
    fun `le poids a le droit de valoir inconnu`() {
        // Exiger sans autoriser `null` ferait inventer un chiffre pour satisfaire un
        // schema, ce que ce projet refuse partout ailleurs.
        listOf(true, false).forEach { strict ->
            assertTrue(nullable(ordinaire(strict), "grams"), "chemin ordinaire, strict = $strict")
            assertTrue(nullable(outil(strict), "grams"), "outil de reponse, strict = $strict")
        }
    }

    @Test
    fun `Anthropic recoit une union de types, Gemini un drapeau`() {
        // Deux dialectes : JSON Schema connait l'union, le sous-ensemble d'OpenAPI 3.0
        // de Gemini ne connait que `nullable`.
        assertEquals(
            listOf("number", "null"),
            champ(ordinaire(strict = true), "grams")["type"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("number", champ(ordinaire(strict = false), "grams")["type"]!!.jsonPrimitive.content)
        assertEquals("true", champ(ordinaire(strict = false), "grams")["nullable"]!!.jsonPrimitive.content)
    }

    @Test
    fun `la quantite et l unite restent obligatoires et non nullables`() {
        // Le poids s'ajoute, il ne remplace rien : une ligne sans quantite est ecartee
        // au parsing, et l'unite dit encore comment l'utilisateur l'a comptee.
        val ligne = ordinaire(strict = true)
        assertTrue(setOf("label", "quantity", "unit", "confidence").all { it in requises(ligne) })
        assertTrue(!nullable(ligne, "quantity"))
        assertTrue(!nullable(ligne, "unit"))
    }

    @Test
    fun `la reference reste facultative, elle`() {
        // L'ecart est voulu : omettre la reference **est** la reponse quand aucune fiche
        // ne convient, alors que se taire sur le poids n'en est jamais une.
        assertTrue("reference" !in requises(outil(strict = true)))
        assertTrue(nullable(outil(strict = true), "reference"))
    }

    private fun ordinaire(strict: Boolean): JsonObject = ligneDe(recognitionSchema(strict))

    private fun outil(strict: Boolean): JsonObject = ligneDe(submitToolSchema(strict))

    private fun ligneDe(schema: JsonObject): JsonObject = schema["properties"]!!
        .jsonObject["items"]!!
        .jsonObject["items"]!!
        .jsonObject

    private fun requises(ligne: JsonObject): Set<String> =
        (ligne["required"] as JsonArray).map { it.jsonPrimitive.content }.toSet()

    private fun champ(ligne: JsonObject, nom: String): JsonObject = ligne["properties"]!!.jsonObject[nom]!!.jsonObject

    /** `null` s'exprime par une union chez l'un, par un drapeau chez l'autre. */
    private fun nullable(ligne: JsonObject, nom: String): Boolean = champ(ligne, nom).let { champ ->
        champ["nullable"]?.jsonPrimitive?.content == "true" ||
            (champ["type"] as? JsonArray)?.any { it.jsonPrimitive.content == "null" } == true
    }
}
