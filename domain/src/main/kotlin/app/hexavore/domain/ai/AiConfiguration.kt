package app.hexavore.domain.ai

/**
 * Une clé d'API, et la seule chose qu'on lui ajoute : elle refuse de s'imprimer.
 *
 * `toString()` rend `***`. Ce n'est pas une décoration — c'est la fuite la plus
 * banale qui existe. Une `data class` qui porte la clé fabrique un `toString()`
 * automatique, et il suffit qu'un `Log.d(config.toString())` ou qu'un message
 * d'exception traverse la pile pour que la clé de quelqu'un se retrouve dans un
 * rapport de plantage. L'intercepteur de redaction couvre le réseau ; ceci couvre
 * tout le reste.
 *
 * `value` reste accessible pour l'unique appelant qui en a besoin — l'en-tête HTTP.
 *
 * **Une `value class`, et elle ne traverse jamais Dagger** : elle arrive par
 * [AiSettings] au moment de l'appel, pas par une liaison. Le nom décoré d'un
 * `@Provides` qui prend une classe en ligne n'est pas un identifiant Java valide
 * ([docs/10][qualite] § Gradle).
 *
 * [qualite]: docs/10-qualite-et-livraison.md
 */
@JvmInline
value class ApiKey(val value: String) {
    override fun toString(): String = "***"
}

/**
 * Ce qu'il faut pour appeler : qui, avec quelle clé, quel modèle, et où.
 *
 * **`baseUrl` est ici plutôt que lu sur [AiProvider]**, parce que l'utilisateur peut
 * l'avoir changée. C'est ce qui rend branchables un relais, un modèle local ou un
 * fournisseur qui n'existe pas encore — l'assurance-vie du projet, dit
 * [docs/05][ia]. Le défaut vient de `provider.defaultBaseUrl` au moment de la
 * saisie, pas ici.
 *
 * [ia]: docs/05-ia.md
 */
data class AiConfiguration(val provider: AiProvider, val apiKey: ApiKey, val model: String, val baseUrl: String)
