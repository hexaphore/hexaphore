package app.hexaphore.domain.ai

/**
 * Ce que l'utilisateur a configuré, s'il a configuré quelque chose.
 *
 * **`null` est une réponse et non un trou** : c'est l'état de départ de toute
 * installation, et celui qui rend les deux boutons IA visibles et **grisés** plutôt
 * que cachés ([D73][decisions]). L'appelant n'a pas à distinguer « pas encore
 * ouvert les réglages » de « clé effacée ».
 *
 * **Une lecture et non un flux**, contrairement à `FoodSearch` : personne ne regarde
 * ses réglages pendant qu'une analyse tourne, et une reconnaissance déjà partie ne
 * doit pas changer de fournisseur en vol. La configuration est relue à chaque appel,
 * ce qui suffit à prendre en compte une clé qu'on vient de corriger.
 *
 * L'implémentation adosse ce port aux `EncryptedSharedPreferences` ([docs/05][ia]
 * § Sécurité des clés) ; le domaine n'en sait rien et ne doit rien en savoir.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
fun interface AiSettings {
    suspend fun current(): AiConfiguration?
}
