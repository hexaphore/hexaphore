package app.hexavore.integration.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Les deux outils, écrits une fois pour les deux fournisseurs.
 *
 * ### Pourquoi la réponse finale passe par un outil elle aussi
 *
 * Le chemin ordinaire force la sortie par un schéma — `output_config.format` chez
 * Anthropic, `responseSchema` chez Gemini. **Rien dans la documentation des deux ne dit
 * si ce forçage se combine avec un appel d'outil**, et le silence n'est pas une
 * confirmation. Bâtir dessus reviendrait à parier sur une combinaison non vérifiée,
 * dans les deux dialectes à la fois.
 *
 * En mode approfondi, il n'y a donc **aucun forçage de sortie** : le modèle rend son
 * repas en appelant [TOOL_SUBMIT], et le JSON arrive dans les arguments de l'appel.
 * C'est le même parseur qui le lit — on lui passe la chaîne, il ne sait pas d'où elle
 * vient.
 *
 * ### Un seul appel par tour
 *
 * Les deux fournisseurs savent brider les appels parallèles, et on le leur demande.
 * Un tour qui rendrait trois appels d'outil obligerait à répondre aux trois dans un
 * seul message — la règle est documentée, et la moindre erreur y apprend au modèle à
 * cesser d'appeler en parallèle. On s'épargne le problème.
 *
 * @see docs/04-sources-de-donnees.md
 */
internal const val TOOL_SEARCH = "chercher_aliments"

internal const val TOOL_SUBMIT = "rendre_le_repas"

/**
 * L'outil de recherche : des libellés en entrée, des candidats en sortie.
 *
 * La description compte autant que le schéma — c'est sur elle que le modèle décide
 * d'appeler. Elle dit **quand** appeler et **ce qu'on attend en retour**, pas seulement
 * ce que la fonction fait.
 */
internal fun searchToolSchema(strict: Boolean): JsonObject = Json.parseToJsonElement(
    """
    {
      "type": "object",
      "properties": {
        "libelles": {
          "type": "array",
          "items": { "type": "string" },
          "description": "Les noms d'aliments à chercher, en français, au singulier."
        }
      },
      "required": ["libelles"]${closure(strict)}
    }
    """.trimIndent(),
) as JsonObject

/**
 * L'outil de réponse : le repas, ligne par ligne, avec la référence choisie.
 *
 * `reference` est **facultative**, et c'est tout le mécanisme : la donner désigne une
 * fiche du catalogue, l'omettre laisse la ligne partir vers l'estimation. Le modèle n'a
 * donc jamais à inventer une référence pour remplir un champ obligatoire — ce qu'il
 * ferait, et ce qui donnerait une fiche qui n'existe pas.
 */
internal fun submitToolSchema(strict: Boolean): JsonObject = Json.parseToJsonElement(
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
              "confidence": { "type": "number" },
              "grams": ${unknowable(strict)},
              "reference": ${optionalString(strict)}
            },
            "required": ["label", "quantity", "unit", "confidence", "grams"]${closure(strict)}
          }
        }
      },
      "required": ["items"]${closure(strict)}
    }
    """.trimIndent(),
) as JsonObject

/**
 * Une référence, ou rien.
 *
 * Le même écart de dialecte que pour les six teneurs : Anthropic accepte une union de
 * types, Gemini veut `nullable`. Écrire l'un pour l'autre rend un `400` que
 * l'utilisateur lirait comme une clé refusée.
 */
private fun optionalString(strict: Boolean) =
    if (strict) """{ "type": ["string", "null"] }""" else """{ "type": "string", "nullable": true }"""
