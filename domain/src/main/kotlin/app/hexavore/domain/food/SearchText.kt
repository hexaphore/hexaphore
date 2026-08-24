package app.hexavore.domain.food

import java.text.Normalizer
import java.util.Locale

/**
 * La forme sous laquelle un nom entre dans l'index de recherche, et sous laquelle
 * une saisie y est comparée.
 *
 * **C'est ici que « creme brulee » trouve « crème brûlée »**, et non dans le
 * tokenizer SQLite. [docs/04][sources] et [docs/07][modele] demandaient FTS5 avec
 * `unicode61 remove_diacritics 2` ; ni l'un ni l'autre ne tient sous `minSdk 26`.
 * FTS5 n'est compilé dans le SQLite embarqué d'aucune version d'Android — c'est la
 * raison pour laquelle Room n'expose que `@Fts3` et `@Fts4` — et
 * `remove_diacritics 2` exige SQLite 3.27, soit l'API 29 ([D49][decisions]).
 *
 * Normaliser à l'import plutôt qu'à la lecture rend le point discutable sans
 * regret : le travail est fait une fois, par la JVM, dont l'implémentation Unicode
 * couvre bien plus que le latin-1 auquel `remove_diacritics 2` se limite. Ce que
 * SQLite doit encore savoir faire se réduit à découper sur les espaces.
 *
 * **La seule règle qui compte** : cette fonction est appliquée aux deux bouts. Un
 * nom indexé sans elle, ou une saisie comparée sans elle, ne se rencontrent jamais.
 * C'est ce qui la place dans `:domain` plutôt que dans le module d'import : les
 * deux bouts sont dans deux modules différents, et deux copies d'une même règle
 * divergent le jour où l'une apprend les ligatures et l'autre non.
 *
 * [sources]: docs/04-sources-de-donnees.md
 * [modele]: docs/07-modele-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
object SearchText {
    /**
     * Les ligatures que la décomposition Unicode ne défait pas.
     *
     * `NFD` sépare une lettre de son accent, mais `œ` n'est pas un `o` accentué :
     * c'est un caractère à part entière. Sans cette table, « œuf » resterait
     * introuvable à qui tape « oeuf » — c'est-à-dire à tout le monde, sur un
     * clavier mobile. Or « œuf » est l'un des trois exemples qui justifient la
     * recherche dès deux caractères ([D23][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    private val LIGATURES = mapOf('œ' to "oe", 'æ' to "ae", 'ß' to "ss")

    /** Marques diacritiques, une fois la décomposition faite. */
    private val COMBINING_MARKS = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    /**
     * Tout ce qui n'est ni lettre latine ni chiffre devient une coupure de mot.
     *
     * Les apostrophes, tirets, virgules et parenthèses des libellés CIQUAL —
     * « Pomme de terre, à l'eau (aliment moyen) » — deviennent des séparateurs. Le
     * tokenizer en ferait autant ; le faire ici rend le contenu de l'index lisible
     * dans un client SQLite, ce qui compte le jour où une recherche ne rend rien.
     */
    private val NON_WORD = "[^a-z0-9]+".toRegex()

    fun normalise(raw: String): String {
        val expanded = buildString(raw.length) {
            raw.forEach { character -> append(LIGATURES[character.lowercaseChar()] ?: character) }
        }
        return Normalizer
            .normalize(expanded, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            // `Locale.ROOT` et non la locale par defaut : en turc, `I` minuscule
            // donne `ı`, et l'index d'un appareil turc ne ressemblerait a aucun
            // autre. Un index genere au build doit etre le meme partout.
            .lowercase(Locale.ROOT)
            .replace(NON_WORD, " ")
            .trim()
    }
}
