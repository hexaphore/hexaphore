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

    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/",
        // Releves sur la documentation vivante. Aucun de ces trois identifiants
        // n'est celui que j'aurais ecrit de memoire, et c'est la troisieme fois.
        suggestedModels = listOf("gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-sol"),
        vision = VisionSupport.ALWAYS,
    ),

    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/",
        suggestedModels = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
        // Sa documentation ne promet pas la lecture d'images. « Selon le modele »
        // dit exactement ce qu'on sait : le mode photo se signalera indisponible
        // tant qu'un sondage n'aura pas prouve le contraire.
        vision = VisionSupport.MODEL_DEPENDENT,
    ),

    MISTRAL(
        displayName = "Mistral",
        defaultBaseUrl = "https://api.mistral.ai/",
        suggestedModels = listOf("mistral-small-2603", "mistral-medium-3505", "mistral-large-2512"),
        vision = VisionSupport.MODEL_DEPENDENT,
    ),

    /**
     * N'importe quel service qui parle comme OpenAI.
     *
     * **Ce n'est pas un bouche-trou, c'est l'assurance-vie du projet** : une URL de
     * base et un nom de modele suffisent a brancher OpenRouter, Groq, un Ollama du
     * reseau local, LM Studio, ou un fournisseur qui n'existe pas encore. C'est le
     * seul dont l'URL par defaut est vide -- il n'y en a pas, et en proposer une
     * ferait croire a un service par defaut qui n'existe pas.
     */
    COMPATIBLE(
        displayName = "Compatible OpenAI",
        defaultBaseUrl = "",
        suggestedModels = emptyList(),
        vision = VisionSupport.MODEL_DEPENDENT,
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
