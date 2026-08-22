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

/**
 * La forme d'une estimation groupée — l'étape 4 de [docs/04][sources].
 *
 * **Les six valeurs sont obligatoires, et chacune peut valoir `null`.**
 *
 * ~~Aucun champ n'était requis à part le libellé~~, pour qu'un modèle qui ne connaît
 * pas les fibres d'un plat puisse se taire dessus plutôt que d'en inventer une. Le
 * raisonnement était bon, l'effet ne l'était pas : le décodage contraint de Gemini lit
 * « pas dans `required` » comme « facultatif », et se tait **par défaut**. Un mangoustan
 * revenait sans glucides, sans lipides et sans fibres — une ligne inutilisable, arrivée
 * par le seul chemin qui avait le droit de produire des chiffres.
 *
 * Exiger la clé tout en autorisant `null` dit exactement ce qu'on voulait dire : le
 * modèle **doit** se prononcer sur chacune des six, et « je ne sais pas » reste
 * exprimable. Le silence par omission, lui, ne l'est plus.
 *
 * Se taire sur un aliment **entier** reste possible, et c'est le prompt qui le porte :
 * un libellé dont le modèle ne sait rien s'omet de la réponse.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
internal fun estimationSchema(strict: Boolean): JsonObject = Json.parseToJsonElement(
    """
    {
      "type": "object",
      "properties": {
        "foods": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "label": { "type": "string" },
              "kcal": ${unknowable(strict)},
              "protein": ${unknowable(strict)},
              "carbs": ${unknowable(strict)},
              "sugars": ${unknowable(strict)},
              "fat": ${unknowable(strict)},
              "fiber": ${unknowable(strict)}
            },
            "required": ["label", "kcal", "protein", "carbs", "sugars", "fat", "fiber"]${closure(strict)}
          }
        }
      },
      "required": ["foods"]${closure(strict)}
    }
    """.trimIndent(),
) as JsonObject

/**
 * Un nombre qui a le droit de valoir « inconnu », **dans deux dialectes**.
 *
 * Anthropic lit du JSON Schema, où l'union de types est la façon canonique de le dire.
 * Gemini lit un sous-ensemble d'OpenAPI 3.0, qui ne connaît pas l'union et exige
 * `nullable`. C'est la seconde différence entre les deux — la première étant
 * `additionalProperties` — et [strict] les porte toutes les deux plutôt que d'ouvrir
 * un second paramètre qui dirait la même chose.
 */
private fun unknowable(strict: Boolean) =
    if (strict) """{ "type": ["number", "null"] }""" else """{ "type": "number", "nullable": true }"""

private fun closure(strict: Boolean) = if (strict) ",\n\"additionalProperties\": false" else ""
