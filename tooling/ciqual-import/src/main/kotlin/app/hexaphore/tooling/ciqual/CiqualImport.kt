package app.hexaphore.tooling.ciqual

import java.io.File
import java.security.MessageDigest

/**
 * La tache `importCiqual` : XML de l'ANSES vers `assets/ciqual.db`.
 *
 * Elle n'est branchee sur aucun cycle de vie de Gradle. La base generee est
 * versionnee -- sans elle, un clone suivi d'un build donnerait une application sans
 * catalogue -- et la regenerer a chaque compilation produirait un binaire de
 * plusieurs megaoctets modifie dans chaque diff. On la lance quand l'ANSES publie.
 *
 * Sortie en ASCII sans accent : une console Windows n'est pas en UTF-8 par defaut,
 * et un message d'erreur illisible est un message perdu.
 */
fun main(args: Array<String>) {
    require(args.size == ARGUMENT_COUNT) {
        "Usage : importCiqual <archive.zip> <servings.csv> <SOURCE.sha256> <sortie.db>"
    }
    val files = args.map(::File)
    val (archive, servings, checksums) = files
    val output = files.last()

    verifyChecksum(archive, checksums)

    val table = CiqualArchive(archive).use { CiqualReader(it).read() }
    failOnUnrecognisedValues(table.unrecognised)

    val portions = ServingsCsv.read(servings, table.foods.map { it.code }.toSet())
    CiqualDatabaseWriter(output).write(table.foods, portions)

    report(table.foods, portions, output)
}

private const val ARGUMENT_COUNT = 4

/**
 * Le controle d'empreinte demande par docs/04.
 *
 * Il porte sur la **source** et non sur la base produite. Une base SQLite n'est pas
 * garantie octet pour octet d'une execution a l'autre -- l'ordre des pages depend
 * de details internes -- donc un controle sur la sortie produirait de fausses
 * alertes, et de fausses alertes finissent par se desactiver. La source, elle, est
 * un fichier fige : si son empreinte bouge, quelque chose a change qu'il faut
 * regarder.
 */
private fun verifyChecksum(archive: File, checksums: File) {
    val expected =
        checksums
            .readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .firstNotNullOfOrNull { line ->
                line.split(Regex("\\s+")).takeIf { it.size == 2 && it[1] == archive.name }?.first()
            }
            ?: error("Aucune empreinte pour ${archive.name} dans ${checksums.name}.")

    val actual = MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") { "%02x".format(it) }

    check(actual == expected) {
        """
        |L'archive CIQUAL ne porte pas l'empreinte attendue.
        |  fichier : ${archive.absolutePath}
        |  attendu : $expected
        |  lu      : $actual
        |
        |Soit l'archive est corrompue, soit elle a ete remplacee par une autre
        |edition. Dans le second cas, mettre a jour ${checksums.name} apres avoir
        |verifie les empreintes des membres contre un telechargement neuf.
        """.trimMargin()
    }
}

/**
 * Le point ou cette tranche se joue.
 *
 * Une ecriture que le parseur ne connait pas ne devient **ni zero ni inconnu** :
 * elle arrete l'import. Les deux replis silencieux se valent en gravite -- l'un
 * invente des valeurs, l'autre en efface -- et aucun des deux ne se voit avant des
 * mois de journal fausse.
 */
private fun failOnUnrecognisedValues(unrecognised: List<UnrecognisedValue>) {
    if (unrecognised.isEmpty()) return

    val shown = unrecognised.take(SHOWN_SAMPLES)
    error(
        buildString {
            appendLine("${unrecognised.size} teneur(s) CIQUAL d'une ecriture inconnue.")
            appendLine()
            shown.forEach { appendLine("  $it") }
            if (unrecognised.size > shown.size) appendLine("  ... et ${unrecognised.size - shown.size} autre(s).")
            appendLine()
            appendLine("L'import s'arrete au lieu de deviner. Une valeur inconnue traitee")
            appendLine("comme un zero fausse des mois de journal en silence, et traitee comme")
            appendLine("une absence efface une donnee qui existait.")
            appendLine()
            append("Ajouter la convention a CiqualValueParser, avec son cas de test.")
        },
    )
}

private const val SHOWN_SAMPLES = 20

private fun report(foods: List<CiqualFood>, servings: List<CiqualServing>, output: File) {
    val missing = Nutrient.entries.associateWith { nutrient -> foods.count { it[nutrient] == null } }

    println("ciqual.db ecrite : ${output.absolutePath}")
    println("  ${foods.size} aliments, ${servings.size} portions, ${output.length() / KILOBYTE} Ko")
    println("  valeurs inconnues, par colonne (NULL, jamais zero) :")
    missing.forEach { (nutrient, count) ->
        println("    ${nutrient.column.padEnd(NUTRIENT_COLUMN_WIDTH)} $count / ${foods.size}")
    }
}

private const val KILOBYTE = 1024
private const val NUTRIENT_COLUMN_WIDTH = 18
