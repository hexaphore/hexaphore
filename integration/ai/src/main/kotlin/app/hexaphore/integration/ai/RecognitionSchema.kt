package app.hexaphore.integration.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * La forme que la réponse doit avoir, écrite une fois pour tous les fournisseurs.
 *
 * C'est le pendant du parseur commun : une seule description de ce qu'on attend, donc
 * une seule chose à corriger le jour où le vocabulaire d'estimation bouge. Six copies
 * auraient divergé sur l'unité qu'un fournisseur oublie.
 *
 * **Il ne borne pas `confidence` entre 0 et 1.** Les contraintes numériques ne font
 * partie du sous-ensemble accepté ni chez l'un ni chez l'autre, et c'est le parseur
 * qui ramène la valeur dans l'intervalle ([D72][decisions]) — la garde existait avant
 * les schémas et leur survit.
 *
 * @param strict ajoute `additionalProperties: false`. La sortie structurée d'Anthropic
 *   **l'exige** ; le sous-ensemble de schéma de Gemini ne le connaît pas et le
 *   refuserait. C'est la seule différence entre les deux, et la nommer vaut mieux que
 *   deux constantes qu'on croirait identiques.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun recognitionSchema(strict: Boolean): JsonObject = Json.parseToJsonElement(
    """
    {
      "type": "object",
      "properties": {
        "items": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "label": { "type": "string" },
              "quantity": { "type": "number" },
              "unit": {
                "type": "string",
                "enum": ["G", "ML", "PIECE", "SLICE", "TBSP", "TSP", "BOWL", "PLATE", "GLASS"]
              },
              "confidence": { "type": "number" }
            },
            "required": ["label", "quantity", "unit", "confidence"]${closure(strict)}
          }
        }
      },
      "required": ["items"]${closure(strict)}
    }
    """.trimIndent(),
) as JsonObject

private fun closure(strict: Boolean) = if (strict) ",\n\"additionalProperties\": false" else ""
