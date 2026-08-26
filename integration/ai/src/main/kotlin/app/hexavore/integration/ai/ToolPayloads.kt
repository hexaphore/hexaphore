package app.hexavore.integration.ai

import app.hexavore.domain.ai.FoodCandidate
import app.hexavore.domain.ai.LabelCandidates
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.TokenUsage
import app.hexavore.domain.food.Food
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ce qui voyage dans les deux sens : les candidats vers le modèle, son repas vers nous.
 *
 * Sorti de `Tooling.kt` quand le seuil de fonctions par fichier a mordu, et le découpage
 * suit ce que les choses sont : là-bas ce que les outils **sont** — leur nom, la forme
 * de leurs arguments —, ici ce qui **passe** par eux.
 */

/**
 * Ce que l'outil de recherche répond, prêt à repartir chez le modèle.
 *
 * **Du JSON compact, sans les champs vides.** Chaque candidat coûte, multiplié par le
 * nombre de libellés d'une assiette ; un rayon absent qui partirait comme `null`
 * ajouterait une ligne par candidat pour ne rien dire.
 *
 * Les six teneurs partent telles quelles, `null` compris — là, l'absence **est** une
 * information : une fiche sans fibres connues ne doit pas être lue comme une fiche à
 * zéro fibre, et c'est la règle la plus structurante du projet.
 */
internal fun List<LabelCandidates>.asToolAnswer(): String {
    // Hors du `buildString` : a l'interieur, le receveur est le `StringBuilder`, et
    // `forEachIndexed` s'appliquerait a ses caracteres au lieu des groupes.
    val groups = this
    return buildString {
        append("""{"resultats":[""")
        groups.forEachIndexed { index, group ->
            if (index > 0) append(',')
            append("""{"libelle":${group.label.quoted()},"candidats":[""")
            group.candidates.forEachIndexed { position, candidate ->
                if (position > 0) append(',')
                append(candidate.asJson())
            }
            append("]}")
        }
        append("]}")
    }
}

private fun FoodCandidate.asJson(): String = buildString {
    append("""{"reference":${reference.quoted()},"nom":${name.quoted()}""")
    group?.let { append(""","rayon":${it.quoted()}""") }
    append(""","pour_100g":{""")
    append(""""kcal":${per100g.kcal.orNull()},"proteines":${per100g.protein.orNull()}""")
    append(""","glucides":${per100g.carbs.orNull()},"sucres":${per100g.sugars.orNull()}""")
    append(""","lipides":${per100g.fat.orNull()},"fibres":${per100g.fiber.orNull()}""")
    append("}}")
}

private fun Double?.orNull(): String = this?.toString() ?: "null"

/**
 * Une chaîne échappée à la main.
 *
 * Le reste du module sérialise par `kotlinx.serialization` ; ici la réponse d'outil
 * n'est pas un type mais un assemblage, et lui donner une classe sérialisable
 * demanderait quatre `@Serializable` pour un texte qu'on écrit une fois et que
 * personne ne relit en Kotlin.
 */
private fun String.quoted(): String = JsonPrimitive(this).toString()

/**
 * Ce que la conversation entière a coûté.
 *
 * **Chaque tour se paie, et pour son message complet** : les fournisseurs ne gardent
 * aucun état entre deux appels, donc le dernier aller-retour renvoie l'assiette et
 * toutes les recherches qui l'ont précédée. Ne compter que lui annoncerait une fraction
 * de la facture, sur le mode qui coûte précisément le plus cher.
 *
 * **Un seul tour muet rend le total inconnu.** Une somme partielle présentée comme le
 * total serait un chiffre faux ; `null` se dit, et l'appel est alors compté sans ses
 * jetons — c'est exactement ce que le journal d'utilisation attend.
 */
internal fun List<TokenUsage?>.total(): TokenUsage? {
    val connus = filterNotNull()
    if (connus.isEmpty() || connus.size != size) return null
    return TokenUsage(input = connus.sumOf { it.input }, output = connus.sumOf { it.output })
}

/**
 * Les libellés que le modèle a demandés.
 *
 * Un argument absent ou d'une autre forme rend une liste vide plutôt qu'une exception :
 * le modèle a le droit de se tromper, et une boucle qui tombe sur un argument malformé
 * coûterait toute l'analyse.
 */
internal fun JsonObject.requestedLabels(): List<String> =
    runCatching { this["libelles"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty() }.getOrDefault(emptyList())

/**
 * Le repas que le modèle a rendu, relié aux fiches qu'on lui avait montrées.
 *
 * **La résolution se fait ici et non plus tard**, parce que c'est ici qu'on sait ce qui
 * a été montré. Une référence que le modèle a inventée ne correspond à rien et la ligne
 * repart vers l'estimation : on ne remplit pas une ligne avec une fiche qu'on n'a pas.
 */
internal fun parseSubmitted(arguments: JsonObject, shown: Map<String, Food>, usage: TokenUsage?): RecognitionOutcome {
    val outcome = parseRecognition(arguments.toString(), usage)
    if (outcome !is RecognitionOutcome.Recognized) return outcome

    val references = arguments.referencesByLabel()
    return RecognitionOutcome.Recognized(
        Recognition(
            items = outcome.recognition.items.map { item ->
                item.copy(chosen = references[item.label]?.let { shown[it] })
            },
            usage = outcome.recognition.usage,
        ),
    )
}

/**
 * Les références choisies, indexées par libellé.
 *
 * Par le libellé et non par la position : le parseur commun écarte les lignes sans
 * quantité, donc les index ne correspondent plus une fois passé par lui. Deux lignes
 * du même libellé dans une assiette partageraient leur référence — c'est acceptable,
 * elles désignent le même aliment.
 */
private fun JsonObject.referencesByLabel(): Map<String, String> = runCatching {
    this["items"]
        ?.jsonArray
        ?.mapNotNull { element ->
            val line = element as? JsonObject ?: return@mapNotNull null
            val label = line["label"]?.jsonPrimitive?.contentOrNullSafely() ?: return@mapNotNull null
            val reference = line["reference"]?.jsonPrimitive?.contentOrNullSafely() ?: return@mapNotNull null
            label to reference
        }
        ?.toMap()
        .orEmpty()
}.getOrDefault(emptyMap())

/** Le contenu, ou `null` si le champ porte le littéral JSON `null`. */
private fun JsonPrimitive.contentOrNullSafely(): String? =
    content.takeIf { isString || it != "null" }?.takeIf { it.isNotBlank() && it != "null" }
