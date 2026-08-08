package app.hexaphore.tooling.ciqual

/**
 * Ce qu'une teneur CIQUAL peut valoir une fois lue.
 *
 * Trois cas et non deux. La distinction qui compte est évidemment celle entre
 * [Known] et [Unknown] — c'est la règle du projet, `null` ne veut jamais dire zéro.
 * Mais un parseur n'a pas que deux issues possibles : il en a une troisième, celle
 * où il rencontre une écriture qu'il ne connaît pas.
 *
 * Ranger ce troisième cas avec [Unknown] serait la faute exacte que cette tranche
 * doit éviter. Une convention nouvelle de l'ANSES deviendrait alors 3 484 valeurs
 * manquantes, et rien ne le dirait : la base se génère, l'application se lance, et
 * les fibres du pain sont simplement absentes pour toujours. [Unrecognised] existe
 * pour que ce cas fasse **échouer l'import**.
 *
 * @see docs/04-sources-de-donnees.md
 */
sealed interface CiqualValue {
    /** Une teneur exploitable, dans l'unité de sa colonne. */
    data class Known(val amount: Double) : CiqualValue

    /** L'ANSES dit qu'elle ne sait pas. **Jamais zéro.** */
    data object Unknown : CiqualValue

    /** Une écriture que ce parseur ne connaît pas. Fait échouer l'import. */
    data class Unrecognised(val raw: String) : CiqualValue
}

/**
 * Le parseur des teneurs de la table CIQUAL.
 *
 * CIQUAL n'est pas un fichier de nombres : c'est un fichier de chaînes, avec des
 * conventions à interpréter. Elles sont peu nombreuses et chacune a son cas de test.
 *
 * | Écriture | Résultat | Pourquoi |
 * |---|---|---|
 * | `12,5` | `12.5` | Virgule décimale française |
 * | `traces` | `0.0` | Présence négligeable — mesurée, et négligeable |
 * | `< 0,5` | `0.25` | Milieu de l'intervalle, sans biaiser vers le haut |
 * | `-` | inconnu | Non déterminé, **différent de zéro** |
 * | `NC` | inconnu | Non communiqué |
 * | vide | inconnu | Idem |
 *
 * **Le seuil de `<` est quelconque.** [docs/04][sources] ne cite que `< 0,5`, mais
 * l'édition 2025 en compte 250 valeurs distinctes, de `< 0,0001` à `< 700`. Un
 * parseur qui aurait reconnu la seule écriture citée aurait rejeté les 249 autres —
 * soit, selon ce qu'on en fait, 16 000 valeurs perdues ou 16 000 zéros inventés.
 *
 * **`NC` et la chaîne vide n'apparaissent pas dans le XML 2025** ; elles
 * apparaissent dans l'export XLS de la même table. Elles sont traitées quand même :
 * le coût est de deux lignes, et l'alternative est qu'un jour une de ces écritures
 * arrive par un chemin auquel on n'avait pas pensé.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
object CiqualValueParser {
    /** Ce que l'ANSES écrit quand la valeur n'a pas été déterminée. */
    private val UNKNOWN_MARKERS = setOf("", "-", "nc")

    /** Présence détectée mais trop faible pour être chiffrée. */
    private const val TRACES = "traces"

    /** Une majoration : `< 0,5` dit que la valeur est quelque part entre 0 et 0,5. */
    private const val BELOW = '<'

    /**
     * La part de la majoration retenue.
     *
     * Le milieu de l'intervalle. Prendre le seuil biaiserait vers le haut, prendre
     * zéro le confondrait avec une absence — or `< 0,5` est une mesure, pas une
     * lacune.
     */
    private const val MIDPOINT = 0.5

    fun parse(raw: String?): CiqualValue {
        val text = raw.orEmpty().trim()
        val marker = text.lowercase()

        return when {
            marker in UNKNOWN_MARKERS -> CiqualValue.Unknown
            marker == TRACES -> CiqualValue.Known(0.0)
            text.startsWith(BELOW) -> midpoint(text)
            else -> number(text)?.let(CiqualValue::Known) ?: CiqualValue.Unrecognised(text)
        }
    }

    private fun midpoint(text: String): CiqualValue = number(text.drop(1).trim())
        ?.let { CiqualValue.Known(it * MIDPOINT) }
        ?: CiqualValue.Unrecognised(text)

    /**
     * Un nombre décimal français.
     *
     * Le point est accepté au même titre que la virgule : le XML n'en contient
     * aucun, mais `facteur_Jones` en utilise dans le même jeu de fichiers, et
     * refuser cette écriture ferait échouer l'import sur une valeur parfaitement
     * lisible. Tout le reste — espace de milliers, exposant, unité collée — reste
     * une écriture inconnue, donc bruyante.
     */
    private fun number(text: String): Double? = text.replace(',', '.').toDoubleOrNull()
}
