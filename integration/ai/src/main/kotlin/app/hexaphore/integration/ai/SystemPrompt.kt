package app.hexaphore.integration.ai

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
 * La version du prompt, qui est son nom de fichier.
 *
 * Elle est destinée au compteur de coût, qui enregistrera avec chaque analyse ce qui
 * a servi à la produire. Elle est déclarée maintenant parce qu'un identifiant qu'on
 * n'a pas noté ne se retrouve pas : les analyses d'ici là seraient sans version.
 */
const val PROMPT_VERSION: String = "fr_v1"

/** Le prompt d'extraction : identifier des aliments et estimer des quantités. */
internal const val EXTRACT_PROMPT_ASSET = "prompts/extract_$PROMPT_VERSION.txt"

/**
 * Le prompt d'estimation — l'étape 4 de [docs/04][sources].
 *
 * Un second fichier et non un paragraphe ajouté au premier : ce sont deux questions,
 * posées dans deux appels, et les mêler ferait payer à chaque reconnaissance les
 * consignes d'une estimation qui n'a le plus souvent pas lieu.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
internal const val ESTIMATE_PROMPT_ASSET = "prompts/estimate_$PROMPT_VERSION.txt"
