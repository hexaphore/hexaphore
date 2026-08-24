package app.hexavore.tooling.ciqual

import app.hexavore.domain.food.FoodCategory
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
    val paths = ImportPaths.of(args)

    verifyChecksum(paths.archive, paths.checksums)

    val table = CiqualArchive(paths.archive).use { CiqualReader(it).read() }
    failOnUnrecognisedValues(table.unrecognised)

    val portions = ServingsCsv.read(paths.servings, table.foods.map { it.code }.toSet())
    // Les libelles servent a valider : un titre court doit designer un code qui
    // existe, et etre plus court que ce qu'il remplace.
    val titles = ShortNamesCsv.read(paths.shortNames, table.foods.associate { it.code to it.name })
    // La table entiere, cette fois : une completion doit viser un trou qui existe
    // encore, et c'est la seule facon de le savoir.
    val completions = CompletionsCsv.read(paths.completions, table.foods.associateBy { it.code })
    CiqualDatabaseWriter(paths.output).write(table.foods, portions, titles, completions)

    report(table.foods, portions, titles, completions, paths.output)
}

/**
 * Les cinq chemins de l'import, nommes.
 *
 * Un type plutot qu'une deconstruction : a cinq elements, l'ordre ne se lit plus, et
 * intervertir servings.csv et short-names.csv produirait une erreur d'en-tete qui
 * parle d'autre chose que du vrai probleme.
 */
private data class ImportPaths(
    val archive: File,
    val servings: File,
    val shortNames: File,
    val completions: File,
    val checksums: File,
    val output: File,
) {
    companion object {
        fun of(args: Array<String>): ImportPaths {
            require(args.size == ARGUMENT_COUNT) {
                "Usage : importCiqual <archive.zip> <servings.csv> <short-names.csv> " +
                    "<completions.csv> <SOURCE.sha256> <sortie.db>"
            }
            val files = args.map(::File)
            return ImportPaths(
                archive = files[0],
                servings = files[1],
                shortNames = files[2],
                completions = files[3],
                checksums = files[4],
                output = files[5],
            )
        }
    }
}

private const val ARGUMENT_COUNT = 6

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

private fun report(
    foods: List<CiqualFood>,
    servings: List<CiqualServing>,
    titles: List<CiqualShortName>,
    completions: List<CiqualCompletion>,
    output: File,
) {
    val missing = Nutrient.entries.associateWith { nutrient -> foods.count { it[nutrient] == null } }
    val worthShortening = foods.count { it.name.length > ShortNamesCsv.WORTH_SHORTENING }

    println("ciqual.db ecrite : ${output.absolutePath}")
    println("  ${foods.size} aliments, ${servings.size} portions, ${output.length() / KILOBYTE} Ko")
    // Le second chiffre est celui qui informe : un titre court manquant sur un
    // libelle deja lisible n'est pas un trou, un titre manquant sur un libelle a
    // rallonge en est un.
    println("  ${titles.size} titres courts, sur $worthShortening libelles qui en valent la peine")
    reportAmbiguousTitles(titles)
    println("  valeurs inconnues, par colonne (NULL, jamais zero) :")
    val completed = completions.groupingBy { it.macro.nutrient }.eachCount()
    missing.forEach { (nutrient, count) ->
        // Ce qui est complete se lit **a cote** du trou et non a sa place : la
        // colonne d'origine reste vide, et c'est tout l'objet de cette separation.
        val estimated = completed[nutrient]?.let { " (dont $it completees)" }.orEmpty()
        println("    ${nutrient.column.padEnd(NUTRIENT_COLUMN_WIDTH)} $count / ${foods.size}$estimated")
    }
    reportCategories(foods)
}

/**
 * Deux fiches sous le meme titre court : signale, jamais bloquant.
 *
 * Un titre court abandonne ce qui ne distingue rien, et deux fiches voisines peuvent
 * se retrouver sous le meme nom -- « Poulet roti » pour la version avec peau et celle
 * sans. Dans une liste de recherche, ou le libelle d'origine ne s'affiche pas, les
 * deux deviennent alors indiscernables.
 *
 * **Un avertissement et non une erreur** : le modele ne voit qu'un lot de cinquante
 * a la fois et ne peut pas garantir l'unicite sur trois mille, donc en faire une
 * condition d'import rendrait la table impossible a produire. Ce qui est possible,
 * c'est de nommer les collisions pour qu'on les corrige a la main dans le CSV.
 */
private fun reportAmbiguousTitles(titles: List<CiqualShortName>) {
    val collisions = titles.groupBy { it.shortName.lowercase() }.filterValues { it.size > 1 }
    if (collisions.isEmpty()) return

    println("  ${collisions.size} titre(s) court(s) porte(s) par plusieurs fiches :")
    collisions.entries.take(SHOWN_SAMPLES).forEach { (title, duplicates) ->
        println("    $title : ${duplicates.joinToString { it.code }}")
    }
    if (collisions.size > SHOWN_SAMPLES) println("    ... et ${collisions.size - SHOWN_SAMPLES} autre(s).")
    println("    Une liste de recherche ne les distinguera pas. A departager dans short-names.csv.")
}

/**
 * Le decompte par rayon, imprime a chaque import.
 *
 * C'est la seule facon de voir qu'un arbitrage de CiqualCategories a derape : un
 * rayon a douze aliments quand on en attendait deux cents se lit d'un coup d'oeil,
 * la ou aucun test ne dirait qu'on s'est trompe de case.
 */
private fun reportCategories(foods: List<CiqualFood>) {
    val byCategory = foods.groupingBy { it.category }.eachCount()
    println("  rayons du bandeau de recherche (table version ${CiqualCategories.VERSION}) :")
    FoodCategory.entries.forEach {
        println("    ${it.name.padEnd(CATEGORY_COLUMN_WIDTH)} ${byCategory[it] ?: 0}")
    }
    println("    ${"(sans rayon)".padEnd(CATEGORY_COLUMN_WIDTH)} ${byCategory[null] ?: 0}")
}

private const val KILOBYTE = 1024
private const val NUTRIENT_COLUMN_WIDTH = 18
private const val CATEGORY_COLUMN_WIDTH = 20
