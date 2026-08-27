package app.hexavore.integration.ai

import android.content.Context

/**
 * Le prompt système, tel qu'il part au modèle.
 *
 * **Un port et non une lecture directe**, pour deux raisons qui se renforcent : le
 * texte vit dans un asset, donc le lire demande un `Context` qu'aucun test de
 * fournisseur n'a envie de monter ; et les six fournisseurs partagent le **même**
 * prompt, donc le point de lecture doit être unique. Un `assets.open()` recopié dans
 * chaque implémentation aurait fini par diverger d'une version.
 *
 * @see docs/05-ia.md § Prompt
 */
fun interface SystemPrompt {
    fun text(): String
}

/**
 * Le prompt lu dans les assets, une fois.
 *
 * **Versionné dans le nom du fichier**, et jamais construit par concaténation
 * dispersée dans le code : sans cela, comprendre plus tard pourquoi une extraction
 * s'est mal passée demanderait de reconstituer ce qui avait été envoyé
 * ([docs/05][ia] § Prompt).
 *
 * `by lazy` plutôt qu'une lecture par appel : le fichier ne change pas sous
 * l'application, et une analyse ne doit pas payer un accès disque pour le redécouvrir.
 *
 * [ia]: docs/05-ia.md
 */
internal class AssetSystemPrompt(private val context: Context, private val asset: String) : SystemPrompt {
    private val cached: String by lazy {
        context.assets.open(asset).use { it.readBytes().decodeToString() }
    }

    override fun text(): String = cached
}

/**
 * La version d'un prompt, qui est son nom de fichier.
 *
 * Elle est destinée au compteur de coût, qui enregistrera avec chaque analyse ce qui
 * a servi à la produire. Elle est déclarée maintenant parce qu'un identifiant qu'on
 * n'a pas noté ne se retrouve pas : les analyses d'ici là seraient sans version.
 *
 * **Une version par prompt, et non une pour les deux.** Ce sont deux textes qui
 * répondent à deux questions et qui bougent séparément : corriger l'estimation a
 * renuméroté l'extraction quand ils partageaient un compteur, ce qui aurait fait croire
 * à un changement là où il n'y en avait pas.
 */
const val EXTRACT_PROMPT_VERSION: String = "fr_v2"

/**
 * L'estimation en est à sa deuxième version.
 *
 * La première laissait le modèle taire une valeur en omettant sa clé ; le décodage
 * contraint de Gemini s'en servait par défaut, et une ligne revenait sans glucides,
 * sans lipides et sans fibres. La deuxième exige les six clés et fait de `null` la
 * seule façon de dire « je ne sais pas » ([D98][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
const val ESTIMATE_PROMPT_VERSION: String = "fr_v2"

/** Le prompt d'extraction : identifier des aliments et estimer des quantités. */
internal const val EXTRACT_PROMPT_ASSET = "prompts/extract_$EXTRACT_PROMPT_VERSION.txt"

/**
 * Le prompt d'estimation — l'étape 4 de [docs/04][sources].
 *
 * Un second fichier et non un paragraphe ajouté au premier : ce sont deux questions,
 * posées dans deux appels, et les mêler ferait payer à chaque reconnaissance les
 * consignes d'une estimation qui n'a le plus souvent pas lieu.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
internal const val ESTIMATE_PROMPT_ASSET = "prompts/estimate_$ESTIMATE_PROMPT_VERSION.txt"

/**
 * Le prompt de l'analyse approfondie.
 *
 * **Un troisième fichier et non un paragraphe ajouté au premier.** Les consignes
 * d'outillage ne servent qu'au mode approfondi ; les joindre au prompt ordinaire ferait
 * payer à chaque analyse simple une demi-page qui parle d'outils qu'elle ne déclare
 * pas — et un modèle à qui l'on décrit un outil absent finit par annoncer qu'il va
 * l'appeler.
 *
 * Le début des deux textes est identique, et ce n'est pas une duplication à corriger :
 * ils décriront la même tâche jusqu'au jour où l'un des deux devra en dire plus, et les
 * factoriser dans un fragment commun rendrait chacun illisible pour économiser des
 * lignes que personne ne compte.
 */
internal const val DEEP_PROMPT_VERSION: String = "fr_v2"

internal const val DEEP_PROMPT_ASSET = "prompts/deep_$DEEP_PROMPT_VERSION.txt"

/**
 * Les trois textes que les fournisseurs se partagent.
 *
 * `extract` identifie et estime les quantités, `estimate` complète ce que le catalogue
 * n'a pas rejoint, `deep` fait la même chose qu'`extract` mais en parlant des outils.
 *
 * **Groupés comme les trois interfaces Retrofit**, et pour la même raison : passés un
 * par un, ils poussaient la fabrique au-delà du seuil de paramètres. Le regroupement dit
 * en outre quelque chose de vrai — ce sont les trois textes communs, là où le reste des
 * dépendances d'un fournisseur lui est propre.
 */
internal data class AiPrompts(val extract: SystemPrompt, val estimate: SystemPrompt, val deep: SystemPrompt)
