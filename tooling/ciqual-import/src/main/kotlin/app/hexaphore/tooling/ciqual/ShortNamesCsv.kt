package app.hexaphore.tooling.ciqual

import java.io.File

/** Un titre court associé à un code CIQUAL. */
internal data class CiqualShortName(val code: String, val shortName: String)

/**
 * La table des titres courts, produite par un modèle et **relue à la main**.
 *
 * Les libellés de l'ANSES décrivent une préparation — « Poulet, blanc, sans peau,
 * cuit au four, sans matière grasse ajoutée » — et c'est ce qui rend une liste de
 * trois aliments illisible. Ce fichier porte le raccourci ; le libellé d'origine ne
 * bouge jamais, parce que c'est lui qui relie la fiche à sa source et que c'est sur
 * lui que la recherche compare.
 *
 * **Un CSV et non un appel au fil de l'eau**, pour la même raison que
 * [ServingsCsv] : le résultat est versionné, relisible ligne à ligne, et corrigeable
 * sans rien relancer. Un titre raté se répare en éditant une ligne, et la correction
 * survit à la prochaine génération — [ShortNames] ne redemande que ce qui manque.
 *
 * La sévérité de ce lecteur est celle de [ServingsCsv], et pour la même raison : ce
 * fichier est écrit par une machine puis corrigé par une personne, donc c'est ici que
 * les deux se trompent. Un code inexistant, un titre vide ou plus long que l'original
 * arrêtent l'import en nommant la ligne.
 */
internal object ShortNamesCsv {
    private const val HEADER = "code_ciqual,titre_court"
    private const val COLUMNS = 2

    /**
     * La longueur au-delà de laquelle un titre court cesse d'être court.
     *
     * Ce n'est pas une préférence de mise en page : au-delà, une ligne de liste se
     * tronque sur un téléphone, et un titre tronqué ne vaut pas mieux que le libellé
     * complet qu'il remplace.
     */
    const val MAX_LENGTH = 40

    /**
     * En deçà, un libellé n'a rien à raccourcir.
     *
     * « Bigorneau, cru » se lit très bien. Générer un titre pour lui coûterait un
     * appel et rendrait la fiche moins précise, pas plus lisible.
     */
    const val WORTH_SHORTENING = 30

    /**
     * Un fichier absent, ou réduit à ses commentaires, rend une liste vide.
     *
     * **Ce n'est pas une erreur, c'est l'état de départ.** La table est produite par
     * une passe qu'on paie et qu'on ne lance pas à chaque clone ; tant qu'elle n'a
     * pas tourné, chaque fiche s'affiche sous son libellé, exactement comme avant que
     * cette colonne existe. Faire échouer l'import ici rendrait le dépôt inutilisable
     * pour qui n'a pas de clé.
     */
    fun read(file: File, names: Map<String, String>): List<CiqualShortName> {
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
                    .map { (index, line) -> parse(file, index + 1, line, names) }
                    .also { rejectDuplicates(file, it) }
            }
        }
    }

    private fun parse(file: File, lineNumber: Int, line: String, names: Map<String, String>): CiqualShortName {
        // Sur la premiere virgule seulement : un titre court peut en contenir une,
        // et un `split` complet couperait « Poulet, blanc » en deux colonnes.
        val separator = line.indexOf(',')
        check(separator > 0) { "${file.name}:$lineNumber : $COLUMNS colonnes attendues, separateur introuvable." }

        val code = line.take(separator).trim()
        val title = line.drop(separator + 1).trim()
        val original = names[code]

        check(original != null) { "${file.name}:$lineNumber : le code CIQUAL $code n'existe pas dans la table." }
        check(title.isNotBlank()) { "${file.name}:$lineNumber : titre court vide." }
        check(title.length <= MAX_LENGTH) {
            "${file.name}:$lineNumber : titre de ${title.length} caracteres, $MAX_LENGTH au plus [$title]."
        }
        // Un titre plus long que l'original n'est pas un raccourci : c'est une
        // reformulation, et elle ferait perdre la precision du libelle publie sans
        // rien gagner en lisibilite.
        check(title.length < original.length) {
            "${file.name}:$lineNumber : titre plus long que le libelle d'origine [$title] >= [$original]."
        }

        return CiqualShortName(code = code, shortName = title)
    }

    /**
     * Un aliment a un titre court, ou aucun, jamais deux.
     *
     * Deux lignes pour un même code se départageraient par leur ordre dans le
     * fichier, c'est-à-dire par hasard — et corriger la mauvaise des deux ne
     * changerait rien à l'écran.
     */
    private fun rejectDuplicates(file: File, titles: List<CiqualShortName>) {
        val offenders =
            titles
                .groupBy { it.code }
                .filterValues { it.size > 1 }
                .map { (code, duplicates) -> "  $code : ${duplicates.joinToString { it.shortName }}" }

        check(offenders.isEmpty()) {
            "${file.name} : plusieurs titres courts pour un meme aliment.\n" + offenders.joinToString("\n")
        }
    }

    /**
     * Réécrit le fichier entier, titres triés par code.
     *
     * **Trié, et c'est ce qui rend le diff lisible.** Une génération reprise ajoute
     * ses lignes au milieu plutôt qu'à la fin, donc une relecture voit ce qui a
     * changé et non un fichier réordonné.
     */
    fun write(file: File, titles: List<CiqualShortName>) {
        val body = titles.sortedBy { it.code }.joinToString("\n") { "${it.code},${it.shortName}" }
        file.writeText(PREAMBLE + HEADER + "\n" + body + "\n")
    }

    private val PREAMBLE =
        """
        |# Titres courts, associes a un code CIQUAL.
        |#
        |# Les libelles de l'ANSES decrivent une preparation complete -- « Poulet,
        |# blanc, sans peau, cuit au four, sans matiere grasse ajoutee » -- et trois
        |# d'entre eux rendent une liste illisible. Cette table porte le raccourci.
        |#
        |# Le libelle d'origine ne bouge jamais : c'est lui qui relie la fiche a sa
        |# source, et c'est sur lui que la recherche compare. Un titre court n'est
        |# qu'un affichage.
        |#
        |# Produit par la tache `generateShortNames`, puis **relu et corrigeable a la
        |# main**. Une correction survit a la generation suivante, qui ne redemande
        |# que les codes absents de ce fichier.
        |#
        |# Un titre doit etre plus court que son libelle d'origine et tenir en
        |# $MAX_LENGTH caracteres, sans quoi l'import s'arrete en nommant la ligne.
        |
        |
        """.trimMargin()
}
