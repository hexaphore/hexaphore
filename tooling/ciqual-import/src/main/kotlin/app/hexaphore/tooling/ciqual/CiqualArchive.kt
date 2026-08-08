package app.hexaphore.tooling.ciqual

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * L'archive des quatre fichiers de l'ANSES, lue sans être décompressée sur disque.
 *
 * 69 Mo de XML tiennent en 2 Mo compressés, parce que ce sont 257 816 fois la même
 * poignée de balises. C'est ce qui rend la source versionnable dans le dépôt, comme
 * [docs/04][sources] le demande, sans y déposer un fichier de 69 Mo que chaque clone
 * paierait.
 *
 * Les membres sont désignés par leur préfixe et non par leur nom complet : l'ANSES
 * date ses fichiers, donc `alim_2025_11_03.xml` devient `alim_2026_xx_xx.xml` à la
 * publication suivante. Le préfixe, lui, ne bouge pas depuis dix ans.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
internal class CiqualArchive(file: File) : AutoCloseable {
    private val zip = ZipFile(file)

    /** Les aliments : code, nom, groupes. */
    fun foods(onRecord: (Map<String, String>) -> Unit) = read("alim_", "ALIM", onRecord)

    /** Les groupes et sous-groupes, pour donner un rayon à chaque aliment. */
    fun groups(onRecord: (Map<String, String>) -> Unit) = read("alim_grp_", "ALIM_GRP", onRecord)

    /** Les teneurs : un enregistrement par couple aliment / constituant. */
    fun compositions(onRecord: (Map<String, String>) -> Unit) = read("compo_", "COMPO", onRecord)

    /** Les constituants et leur intitulé, qui sert à vérifier les codes. */
    fun constituents(onRecord: (Map<String, String>) -> Unit) = read("const_", "CONST", onRecord)

    private fun read(prefix: String, element: String, onRecord: (Map<String, String>) -> Unit) {
        val entry = entry(prefix)
        zip.getInputStream(entry).use { XmlRecords.forEach(it, element, onRecord) }
    }

    private fun entry(prefix: String): ZipEntry {
        // `alim_` est aussi le prefixe de `alim_grp_` : sans cette exclusion, lire
        // les aliments rendrait peut-etre les groupes, selon l'ordre des entrees.
        val candidates =
            zip
                .entries()
                .asSequence()
                .filter { it.name.startsWith(prefix) && it.name.endsWith(".xml") }
                .filter { prefix != "alim_" || !it.name.startsWith("alim_grp_") }
                .toList()

        return candidates.singleOrNull()
            ?: error("Archive CIQUAL : ${candidates.size} entree(s) en $prefix*.xml, il en faut exactement une.")
    }

    override fun close() = zip.close()
}
