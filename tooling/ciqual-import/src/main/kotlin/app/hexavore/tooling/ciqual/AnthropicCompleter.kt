package app.hexavore.tooling.ciqual

import app.hexavore.domain.nutrition.Macro
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams

/**
 * L'estimation des teneurs manquantes, demandée à Claude.
 *
 * **Le seul endroit du projet où un modèle produit un chiffre qui entrera au
 * catalogue.** [D83][decisions] avait posé la règle inverse — une estimation ne
 * devient jamais une fiche — et cette passe est l'exception assumée qui la prolonge :
 * ici l'estimation entre, mais elle entre dans ses **propres colonnes**, et chaque
 * valeur porte sa provenance jusqu'à l'écran.
 *
 * Hors de l'application, comme les titres courts, et pour les mêmes raisons. La clé
 * est passée à l'invocation de la tâche et n'est écrite nulle part.
 *
 * @param model l'identifiant du modèle. Relevé, jamais écrit de mémoire.
 */
internal class AnthropicCompleter(
    apiKey: String,
    private val model: String,
    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build(),
) : NutritionCompleter {
    override fun complete(gaps: List<Gap>): Map<Pair<String, Macro>, Double> {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .system(SYSTEM_PROMPT)
            .addUserMessage(gaps.joinToString("\n") { "${it.code}\t${it.macro.name}\t${it.name}" })
            .build()

        val answer = client.messages().create(params).content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("\n")

        return parseCompletions(answer)
    }

    private companion object {
        const val MAX_TOKENS = 8_000L

        /**
         * La consigne, et son insistance sur le droit de se taire.
         *
         * C'est la même que celle du repli d'estimation ([D83][decisions]) : une
         * omission se corrige à la main, un chiffre inventé passe inaperçu. Ici
         * davantage encore — ces valeurs entrent au catalogue et se retrouveront dans
         * un journal alimentaire.
         *
         * [decisions]: docs/11-decisions.md
         */
        val SYSTEM_PROMPT =
            """
            Tu estimes des teneurs nutritionnelles manquantes de la table CIQUAL de l'ANSES.

            Chaque ligne recue porte un code, une tabulation, le nom de la teneur, une
            tabulation, puis le libelle de l'aliment.
            Reponds une ligne par teneur : le meme code, une tabulation, le meme nom de
            teneur, une tabulation, la valeur **pour 100 g**.
            Rien d'autre -- ni preambule, ni unite, ni commentaire.

            Les six teneurs, et leur unite :
            - CALORIES : kilocalories pour 100 g
            - PROTEIN, CARBS, SUGARS, FAT, FIBER : grammes pour 100 g

            Trois regles :
            - Les glucides s'entendent **hors fibres**, comme dans CIQUAL et dans le
              reglement UE 1169/2011. Les sucres sont un sous-ensemble des glucides.
            - Zero est une reponse valide : une huile contient reellement zero gramme
              de glucides, et une viande zero gramme de fibres.
            - Une valeur doit rester coherente avec les teneurs que la fiche publie
              deja. Si tu estimes une energie, elle doit s'accorder avec les macros
              connues : 4 kcal par gramme de proteines et de glucides, 9 pour les
              lipides, 2 pour les fibres.

            **Si tu ne sais pas, omets la ligne.** Une omission se corrige a la main ;
            un chiffre invente entre dans un journal alimentaire et n'en ressort pas.
            Ces valeurs seront affichees comme estimees, mais elles seront comptees.
            """.trimIndent()
    }
}

/**
 * La réponse, relue sans croire le modèle sur parole.
 *
 * Trois champs séparés par des tabulations ; toute ligne qui n'en porte pas deux est
 * ignorée, ce qui absorbe un préambule ou un commentaire ajouté malgré la consigne.
 * Un nom de teneur inconnu est écarté ici plutôt que plus loin : c'est le seul endroit
 * qui sache que la chaîne devait désigner une [Macro].
 *
 * Ce qui n'est pas absorbé ici l'est par [Completions] : les couples qu'on n'a pas
 * demandés, et les valeurs hors des bornes physiques.
 *
 * Hors de la classe pour être éprouvable sans réseau.
 */
internal fun parseCompletions(answer: String): Map<Pair<String, Macro>, Double> = answer
    .lineSequence()
    .mapNotNull { line ->
        val fields = line.split('\t').map(String::trim)
        if (fields.size < FIELDS) return@mapNotNull null
        val macro = Macro.entries.firstOrNull { it.name == fields[1] } ?: return@mapNotNull null
        val value = fields[2].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        (fields[0] to macro) to value
    }
    .filter { (key, _) -> key.first.isNotEmpty() }
    .toMap()

/** Code, nom de teneur, valeur. */
private const val FIELDS = 3
