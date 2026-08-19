package app.hexaphore.domain.ai

/**
 * Un fournisseur de reconnaissance, et ce qu'il faut en savoir pour le proposer.
 *
 * **L'énumération ne porte que les fournisseurs implémentés**, et elle grandit avec
 * eux. Une entrée sans classe laisserait une branche morte dans la fabrique, qui
 * devrait alors rendre une erreur pour un fournisseur que l'écran des réglages vient
 * d'offrir — un choix qu'on présente et qu'on refuse. C'est aussi ce qui fait de
 * « ajouter un fournisseur » une opération vérifiable : le `when` de la fabrique est
 * exhaustif, donc une entrée nouvelle **ne compile pas** tant que sa classe n'existe
 * pas ([docs/05][ia] § Ajouter un fournisseur).
 *
 * **[displayName] est ici et non dans les ressources** : ce sont des noms de marque,
 * ils ne se traduisent pas. Les mettre ailleurs obligerait l'écran à un `when` sur le
 * fournisseur — précisément le signal que [docs/12][plan] nomme comme la fuite de
 * l'abstraction.
 *
 * [ia]: docs/05-ia.md
 * [plan]: docs/12-plan-de-developpement.md
 */
enum class AiProvider(
    val displayName: String,
    /** Modifiable par l'utilisateur : c'est ce qui rend un relais ou un modèle local branchable. */
    val defaultBaseUrl: String,
    /**
     * Ce qu'on propose de saisir, quand on le sait de source sûre.
     *
     * Vide plutôt que deviné : un identifiant de modèle écrit de mémoire rend un
     * `404` que l'utilisateur lira comme une panne de l'application. Le champ reste
     * libre — la liste ne fait qu'épargner une frappe.
     */
    val suggestedModels: List<String>,
    val vision: VisionSupport,
) {
    ANTHROPIC(
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com/",
        suggestedModels = listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5"),
        vision = VisionSupport.ALWAYS,
    ),

    GEMINI(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/",
        // Releves sur la documentation vivante, pas ecrits de memoire : les
        // identifiants que j'aurais devines n'existaient pas.
        suggestedModels = listOf("gemini-3.5-flash-lite", "gemini-3.7-flash", "gemini-2.5-flash"),
        vision = VisionSupport.ALWAYS,
    ),
}

/**
 * Si le fournisseur lit les images, et si la réponse dépend du modèle choisi.
 *
 * Deux cas parce que [docs/05][ia] en distingue deux : certains fournisseurs
 * répondent « oui » pour toute leur gamme, d'autres « selon modèle ». Pour les
 * seconds, c'est le bouton **Tester** qui tranche à l'enregistrement de la clé — et
 * non l'instant où quelqu'un tient son téléphone au-dessus d'une assiette.
 *
 * [ia]: docs/05-ia.md
 */
enum class VisionSupport {
    /** Toute la gamme lit les images. */
    ALWAYS,

    /** Cela dépend du modèle : à éprouver avant tout usage réel. */
    MODEL_DEPENDENT,
}
