package app.hexaphore.tooling.ciqual

import app.hexaphore.domain.nutrition.Macro
import java.io.File

/** Une teneur complétée par un modèle, pour un code CIQUAL et une macro. */
internal data class CiqualCompletion(val code: String, val macro: Macro, val value: Double)

/**
 * La table des teneurs complétées, produite par un modèle et **relue à la main**.
 *
 * CIQUAL laisse des trous : 313 fiches sur 3 484 ont au moins une des six teneurs
 * inconnue, et 143 n'ont aucune énergie déterminée — la feta, les câpres, le
 * bigorneau. Une ligne sans énergie n'est pas enregistrable, donc ces fiches sont
 * inutilisables en l'état.
 *
 * **Une ligne par valeur et non par fiche.** C'est la granularité de la règle qui
 * commande toute la conception — la provenance se porte valeur par valeur — et c'est
 * aussi ce qui rend une complétion supprimable seule, sans toucher aux cinq autres
 * teneurs de la même fiche.
 *
 * **Une complétion pour une teneur que l'ANSES publie arrête l'import.** Ce n'est pas
 * une sévérité gratuite : ce fichier existe pour combler des trous, et une ligne qui
 * en vise un qui n'existe plus est soit une erreur de saisie, soit un reliquat d'un
 * import précédent. Dans les deux cas elle doit se voir, et non dormir dans le
 * fichier en attendant qu'un import ultérieur la fasse resurgir.
 *
 * Le nom de la macro est celui de **l'énumération du domaine**, comme le rayon dans
 * `ciqual_food.category` : un intitulé français dans le fichier demanderait une table
 * de correspondance de plus, qui divergerait le jour où l'on renomme une macro.
 */
internal object CompletionsCsv {
    private const val HEADER = "code_ciqual,macro,teneur"

    fun read(file: File, table: Map<String, CiqualFood>): List<CiqualCompletion> {
        val lines =
            file
                .takeIf(File::exists)
                ?.readLines()
                .orEmpty()
                .map(String::trim)
                .withIndex()
                .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") }

        val header = lines.firstOrNull()
        return when (header) {
            null -> emptyList()
            else -> {
                check(header.value == HEADER) {
                    "${file.name}:${header.index + 1} : en-tete attendu [$HEADER], lu [${header.value}]."
                }
                lines
                    .drop(1)
                    .map { (index, line) -> parse(file, index + 1, line, table) }
                    .also { rejectDuplicates(file, it) }
            }
        }
    }

    private fun parse(file: File, lineNumber: Int, line: String, table: Map<String, CiqualFood>): CiqualCompletion {
        val fields = line.split(',').map(String::trim)
        check(fields.size == COLUMNS) { "${file.name}:$lineNumber : $COLUMNS colonnes attendues, ${fields.size} lues." }

        val (code, name, amount) = fields
        val food = table[code]
        check(food != null) { "${file.name}:$lineNumber : le code CIQUAL $code n'existe pas dans la table." }

        val macro = Macro.entries.firstOrNull { it.name == name }
        check(macro != null) {
            "${file.name}:$lineNumber : macro inconnue [$name]. Attendu : ${Macro.entries.joinToString { it.name }}."
        }
        // Le trou doit exister. Une completion posee sur une teneur publiee serait
        // une valeur inventee **par-dessus** une mesure -- exactement ce que la
        // separation des colonnes existe pour rendre impossible.
        check(food[macro.nutrient] == null) {
            "${file.name}:$lineNumber : $code publie deja sa teneur en $name. Retirer cette ligne."
        }

        val value = amount.toDoubleOrNull()
        check(value != null && value >= 0.0) { "${file.name}:$lineNumber : teneur illisible ou negative [$amount]." }

        return CiqualCompletion(code = code, macro = macro, value = value)
    }

    /**
     * Une teneur complétée une seule fois.
     *
     * Deux lignes pour un même couple se départageraient par leur ordre dans le
     * fichier, c'est-à-dire par hasard.
     */
    private fun rejectDuplicates(file: File, completions: List<CiqualCompletion>) {
        val offenders =
            completions
                .groupBy { it.code to it.macro }
                .filterValues { it.size > 1 }
                .map { (key, rows) -> "  ${key.first} ${key.second} : " + rows.joinToString { "${it.value}" } }

        check(offenders.isEmpty()) {
            "${file.name} : plusieurs teneurs pour un meme couple.\n" + offenders.joinToString("\n")
        }
    }

    /** Réécrit le fichier entier, trié par code puis par macro : un diff qui se lit. */
    fun write(file: File, completions: List<CiqualCompletion>) {
        val body = completions
            .sortedWith(compareBy({ it.code }, { it.macro.ordinal }))
            .joinToString("\n") { "${it.code},${it.macro.name},${format(it.value)}" }
        file.writeText(PREAMBLE + HEADER + "\n" + body + "\n")
    }

    /** Sans décimale quand il n'en faut pas : « 2 » plutôt que « 2.0 ». */
    private fun format(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private const val COLUMNS = 3

    private val PREAMBLE =
        """
        |# Teneurs completees par un modele, associees a un code CIQUAL.
        |#
        |# CIQUAL laisse des trous : 143 fiches n'ont aucune energie determinee -- la
        |# feta, les capres, le bigorneau -- et une ligne sans energie n'est pas
        |# enregistrable. Ce fichier les comble.
        |#
        |# **Une valeur completee reste une valeur inventee.** Elle ne remplace jamais
        |# une mesure : elle vit dans ses propres colonnes, la lecture prefere toujours
        |# l'originale, et l'ecran dit champ par champ ce qui a ete devine.
        |#
        |# Une ligne par valeur, et non par fiche : c'est la granularite de la regle,
        |# et c'est ce qui rend une completion supprimable seule.
        |#
        |# Produit par la tache `generateCompletions`, puis **relu et corrigeable a la
        |# main**. Une correction survit a la generation suivante, qui ne redemande que
        |# les trous absents de ce fichier -- et qui retire les lignes dont l'ANSES
        |# publie desormais la mesure.
        |#
        |# Une teneur que l'ANSES publie arrete l'import : la ligne est soit fautive,
        |# soit un reliquat, et dans les deux cas elle doit se voir.
        |
        |
        """.trimMargin()
}
