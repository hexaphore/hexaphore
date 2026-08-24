package app.hexaphore.domain.backup

/**
 * Les réglages et les secrets, hors du journal.
 *
 * **Un port et non une dépendance par réglage.** L'effacement composait jusqu'ici les
 * oublis un à un — les clés d'un côté, le compte Open Food Facts de l'autre — et il en
 * laissait trois derrière lui : l'état de l'adaptation hebdomadaire, le consentement
 * photo, et le compteur d'appels. Le défaut n'était pas dans l'une des lignes, il était
 * dans la forme : une liste écrite dans le cas d'usage se tait quand on oublie de
 * l'allonger.
 *
 * Ici, celui qui répond est celui qui **range** — il connaît ses fichiers, y compris
 * ce que personne n'a modélisé.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
fun interface StoredPreferences {
    /**
     * Vide les réglages et oublie les clés. **Irréversible.**
     *
     * Ne détruit pas la clé du trousseau matériel qui les chiffrait : ce n'est pas une
     * donnée que l'utilisateur a écrite, et sans chiffré à déchiffrer elle ne dit plus
     * rien de lui.
     */
    suspend fun erase()
}
