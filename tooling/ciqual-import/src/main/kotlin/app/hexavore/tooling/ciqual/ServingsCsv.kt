package app.hexavore.tooling.ciqual

import java.io.File

/**
 * La table des portions usuelles, écrite et relue à la main.
 *
 * CIQUAL ne donne que des valeurs pour 100 g. Or personne ne pèse une pomme, et
 * c'est ce qui fait la différence entre une application utilisable et une balance
 * de cuisine obligatoire ([docs/04][sources]).
 *
 * Ce fichier est **le point d'entrée idéal pour contribuer sans écrire de Kotlin**,
 * et c'est ce qui dicte la sévérité de ce lecteur : un code CIQUAL inexistant, un
 * poids illisible ou une colonne manquante arrêtent l'import en nommant la ligne.
 * Une contribution mal formée doit échouer à la relecture, pas produire une portion
 * silencieusement absente — ou pire, rattachée au mauvais aliment.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
internal object ServingsCsv {
    private const val HEADER = "code_ciqual,libelle,grammes,par_defaut"
    private const val COLUMNS = 4

    fun read(file: File, knownCodes: Set<String>): List<CiqualServing> {
        val lines =
            file
                .readLines()
                .map(String::trim)
                .withIndex()
                .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") }

        val header = lines.firstOrNull() ?: error("${file.name} est vide.")
        check(header.value == HEADER) { "${file.name}:${header.index + 1} : en-tete attendu [$HEADER], lu [$header]." }

        val servings = lines.drop(1).map { (index, line) -> parse(file, index + 1, line, knownCodes) }
        rejectDuplicateDefaults(file, servings)
        return servings
    }

    private fun parse(file: File, lineNumber: Int, line: String, knownCodes: Set<String>): CiqualServing {
        val fields = line.split(',').map(String::trim)
        check(fields.size == COLUMNS) { "${file.name}:$lineNumber : $COLUMNS colonnes attendues, ${fields.size} lues." }

        val (code, label, grams) = fields
        val byDefault = fields[COLUMNS - 1]
        check(code in knownCodes) { "${file.name}:$lineNumber : le code CIQUAL $code n'existe pas dans la table." }
        check(label.isNotBlank()) { "${file.name}:$lineNumber : libelle vide." }

        val weight = grams.toDoubleOrNull()
        check(weight != null && weight > 0) { "${file.name}:$lineNumber : poids illisible ou nul [$grams]." }
        check(byDefault == "true" || byDefault == "false") {
            "${file.name}:$lineNumber : par_defaut vaut [$byDefault], attendu true ou false."
        }

        return CiqualServing(code = code, label = label, grams = weight, isDefault = byDefault == "true")
    }

    /**
     * Un aliment a une portion par défaut, ou aucune, jamais deux.
     *
     * Deux valeurs par défaut se départageraient par l'ordre des lignes du fichier,
     * c'est-à-dire par hasard. La quantité proposée à l'ouverture d'une fiche
     * changerait alors sans que rien ne l'explique.
     */
    private fun rejectDuplicateDefaults(file: File, servings: List<CiqualServing>) {
        val offenders =
            servings
                .filter { it.isDefault }
                .groupBy { it.code }
                .filterValues { it.size > 1 }
                .map { (code, duplicates) -> "  $code : ${duplicates.joinToString { it.label }}" }

        check(offenders.isEmpty()) {
            "${file.name} : plusieurs portions par defaut pour un meme aliment.\n" + offenders.joinToString("\n")
        }
    }
}
