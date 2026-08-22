package app.hexaphore.integration.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que le schéma d'estimation **exige**, et ce qu'il autorise à ignorer.
 *
 * **Le défaut qui a motivé ces cas** : les six valeurs n'étaient pas requises, pour
 * qu'un modèle puisse se taire sur celle qu'il ne connaît pas. Le décodage contraint de
 * Gemini lit « pas dans `required` » comme « facultatif » et se tait **par défaut** —
 * un mangoustan revenait sans glucides, sans lipides et sans fibres, par le seul chemin
 * qui avait le droit de produire des chiffres.
 *
 * Un schéma ne se vérifie pas à la lecture : il part sur le réseau et c'est le
 * fournisseur qui l'applique. Ces cas sont donc ce qui reste — ils affirment sa
 * **forme**, dans les deux dialectes.
 */
class EstimationSchemaTest {
    @Test
    fun `les six valeurs sont exigees, dans les deux dialectes`() {
        // Exiger la cle est ce qui interdit le silence par omission. C'est la
        // correction, et c'est ce cas qui tombe si on revient en arriere.
        listOf(true, false).forEach { strict ->
            assertEquals(SIX_ET_LE_LIBELLE, requises(strict), "dialecte strict = $strict")
        }
    }

    @Test
    fun `chaque valeur a le droit de valoir inconnu`() {
        // Exiger les six sans autoriser `null` ferait inventer des chiffres pour
        // satisfaire un schema -- exactement ce que le projet refuse depuis toujours.
        MACROS.forEach { macro ->
            assertTrue(nullable(strict = true, macro), "$macro doit accepter null chez Anthropic")
            assertTrue(nullable(strict = false, macro), "$macro doit accepter null chez Gemini")
        }
    }

    @Test
    fun `Anthropic recoit une union de types, Gemini un drapeau`() {
        // Deux dialectes : JSON Schema connait l'union, le sous-ensemble d'OpenAPI 3.0
        // de Gemini ne connait que `nullable`. Envoyer la forme de l'un a l'autre fait
        // refuser la requete, ou pire, ignorer la contrainte en silence.
        assertEquals(
            listOf("number", "null"),
            champ(strict = true, "kcal")["type"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("number", champ(strict = false, "kcal")["type"]!!.jsonPrimitive.content)
        assertEquals("true", champ(strict = false, "kcal")["nullable"]!!.jsonPrimitive.content)
    }

    @Test
    fun `le libelle reste une chaine, et non un nombre qui s ignore`() {
        // C'est lui qui rattache l'estimation a la ligne qui l'a demandee : le laisser
        // nullable rendrait des estimations orphelines.
        assertEquals("string", champ(strict = true, "label")["type"]!!.jsonPrimitive.content)
        assertTrue(!nullable(strict = true, "label"))
    }

    @Test
    fun `Gemini ne recoit jamais additionalProperties`() {
        // Son sous-ensemble de schema ne connait pas le mot-cle et refuserait la
        // requete entiere. C'est la premiere des deux differences entre les dialectes.
        assertTrue("additionalProperties" !in estimationSchema(strict = false).toString())
        assertTrue("additionalProperties" in estimationSchema(strict = true).toString())
    }

    private fun aliment(strict: Boolean): JsonObject = estimationSchema(strict)["properties"]!!
        .jsonObject["foods"]!!
        .jsonObject["items"]!!
        .jsonObject

    private fun requises(strict: Boolean): Set<String> =
        (aliment(strict)["required"] as JsonArray).map { it.jsonPrimitive.content }.toSet()

    private fun champ(strict: Boolean, nom: String): JsonObject =
        aliment(strict)["properties"]!!.jsonObject[nom]!!.jsonObject

    /** `null` s'exprime par une union chez l'un, par un drapeau chez l'autre. */
    private fun nullable(strict: Boolean, nom: String): Boolean = champ(strict, nom).let { champ ->
        champ["nullable"]?.jsonPrimitive?.content == "true" ||
            (champ["type"] as? JsonArray)?.any { it.jsonPrimitive.content == "null" } == true
    }

    private companion object {
        val MACROS = listOf("kcal", "protein", "carbs", "sugars", "fat", "fiber")
        val SIX_ET_LE_LIBELLE = (MACROS + "label").toSet()
    }
}
