package app.hexavore.tooling.ciqual

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * La génération des titres courts, sans réseau.
 *
 * Trois choses s'y jouent, et chacune coûterait cher autrement : ce qui est **déjà
 * acquis** n'est jamais redemandé, ce que le modèle rend est **rattaché par code** et
 * non par libellé, et ce qui ne raccourcit rien est **écarté** plutôt que corrigé.
 */
class ShortNamesTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `un libelle deja lisible n est jamais demande`() {
        // « Bigorneau, cru » se lit tres bien. Payer un titre pour lui rendrait la
        // fiche moins precise, pas plus lisible.
        val demandes = mutableListOf<String>()
        val names = ShortNames({ labels ->
            labels.forEach { demandes += it.code }
            emptyMap()
        })

        names.generate(listOf(COURT, LONG), emptyList())

        assertEquals(listOf(LONG.code), demandes, "seul le libelle a rallonge part")
    }

    @Test
    fun `ce qui est deja ecrit n est pas redemande`() {
        // **La propriete qui rend soixante-dix requetes supportables.** Sans elle,
        // une coupure au trentieme lot fait tout repayer.
        val demandes = mutableListOf<String>()
        val names = ShortNames({ labels ->
            labels.forEach { demandes += it.code }
            emptyMap()
        })

        names.generate(listOf(LONG, AUTRE), listOf(CiqualShortName(LONG.code, "Blanc de poulet au four")))

        assertEquals(listOf(AUTRE.code), demandes)
    }

    @Test
    fun `un titre acquis survit a une passe qui ne rend rien`() {
        val acquis = CiqualShortName(LONG.code, "Blanc de poulet au four")

        val produits = ShortNames({ emptyMap() }).generate(listOf(LONG, AUTRE), listOf(acquis))

        assertEquals(listOf(acquis), produits)
    }

    @Test
    fun `un code qu on n a pas demande est ignore`() {
        // Un modele qui invente une cle produirait un titre qu'on ne peut rattacher
        // a rien -- ou pire, qu'on rattacherait a la mauvaise fiche.
        val names = ShortNames({ mapOf("99999" to "Inconnu au bataillon") })

        assertTrue(names.generate(listOf(LONG), emptyList()).isEmpty())
    }

    @Test
    fun `un titre plus long que le libelle est ecarte`() {
        // **Le libelle doit etre court**, sinon c'est la longueur maximale qui
        // ecarte le titre et cette regle-ci n'est pas eprouvee. La campagne de
        // defaite a trouve exactement ce defaut : le cas passait sans rien mesurer.
        val names = ShortNames({ mapOf(JUSTE_LONG.code to TITRE_PLUS_LONG) })

        assertTrue(TITRE_PLUS_LONG.length <= ShortNamesCsv.MAX_LENGTH, "sinon l autre regle ferait le travail")
        assertTrue(TITRE_PLUS_LONG.length > JUSTE_LONG.name.length)
        assertTrue(names.generate(listOf(JUSTE_LONG), emptyList()).isEmpty(), "ce n'est pas un raccourci")
    }

    @Test
    fun `un titre au-dela de la longueur maximale est ecarte`() {
        val trop = "T".repeat(ShortNamesCsv.MAX_LENGTH + 1)
        val names = ShortNames({ mapOf(LONG.code to trop) })

        assertTrue(names.generate(listOf(LONG), emptyList()).isEmpty(), "il se tronquerait dans une liste")
    }

    @Test
    fun `un titre vide est ecarte, et sera redemande`() {
        val names = ShortNames({ mapOf(LONG.code to "   ") })

        assertTrue(names.generate(listOf(LONG), emptyList()).isEmpty())
    }

    @Test
    fun `les libelles partent par lots`() {
        // Un lot par fiche paierait la consigne trois mille fois ; un lot de mille
        // rendrait une reponse qu'une coupure perdrait en entier.
        val lots = mutableListOf<Int>()
        val labels = (1..5).map { LongLabel("%05d".format(it), LONG.name) }
        val names = ShortNames(
            { lot ->
                lots += lot.size
                emptyMap()
            },
            batchSize = 2,
        )

        names.generate(labels, emptyList())

        assertEquals(listOf(2, 2, 1), lots)
    }

    @Test
    fun `chaque lot est annonce avec tout ce qui est acquis`() {
        // L'appelant reecrit le fichier entier a chaque lot. Lui passer le seul lot
        // l'obligerait a tenir un cumul de son cote, et deux comptes divergent.
        val labels = (1..4).map { LongLabel("%05d".format(it), LONG.name) }
        val etats = mutableListOf<Int>()
        val names = ShortNames({ batch -> batch.associate { it.code to "Court" } }, batchSize = 2)

        names.generate(labels, emptyList()) { acquis -> etats += acquis.size }

        assertEquals(listOf(2, 4), etats, "le second appel voit aussi ce que le premier a rendu")
    }

    // --- La relecture de la reponse -------------------------------------------

    @Test
    fun `un preambule n emporte pas le lot`() {
        // Le modele a la consigne de n'ecrire que des lignes. S'il en ajoute une,
        // perdre cinquante titres deja payes serait le pire des deux maux.
        val reponse = "Voici les titres :\n\n13039\tBlanc de poulet\n"

        assertEquals(mapOf("13039" to "Blanc de poulet"), parseShortNames(reponse))
    }

    @Test
    fun `un titre qui contient une virgule traverse entier`() {
        assertEquals(mapOf("13039" to "Poulet, blanc roti"), parseShortNames("13039\tPoulet, blanc roti"))
    }

    // --- Le fichier -----------------------------------------------------------

    @Test
    fun `un fichier absent rend une liste vide, sans echouer`() {
        // C'est l'etat de depart du depot : la passe se paie et n'a pas tourne.
        // Echouer ici rendrait le projet inconstruisible sans cle.
        assertTrue(ShortNamesCsv.read(File(directory.toFile(), "absent.csv"), NAMES).isEmpty())
    }

    @Test
    fun `l aller-retour par le fichier ne perd rien`() {
        val titles = listOf(CiqualShortName(LONG.code, "Blanc de poulet au four"))
        val file = File(directory.toFile(), "short-names.csv")

        ShortNamesCsv.write(file, titles)

        assertEquals(titles, ShortNamesCsv.read(file, NAMES))
    }

    @Test
    fun `le fichier est ecrit trie par code`() {
        // Une generation reprise ajoute ses lignes au milieu plutot qu'a la fin :
        // une relecture voit ce qui a change, pas un fichier reordonne.
        val file = File(directory.toFile(), "short-names.csv")
        ShortNamesCsv.write(file, listOf(CiqualShortName(AUTRE.code, "Riz cuit"), CiqualShortName(LONG.code, "Poulet")))

        assertEquals(listOf(LONG.code, AUTRE.code).sorted(), ShortNamesCsv.read(file, NAMES).map { it.code })
    }

    @Test
    fun `un code inexistant arrete l import`() {
        val file = write("99999,Inconnu")

        val faute = assertThrows<IllegalStateException> { ShortNamesCsv.read(file, NAMES) }

        assertTrue(faute.message!!.contains("99999"), "la ligne fautive est nommee")
    }

    @Test
    fun `un titre plus long que son libelle arrete l import`() {
        // Le modele est cense l'ecarter ; une correction a la main peut le
        // reintroduire, et c'est ici qu'on l'attrape. Le libelle est court a
        // dessein : sinon c'est la longueur maximale qui refuserait, et cette
        // regle-ci ne serait pas eprouvee.
        val file = write("${JUSTE_LONG.code},$TITRE_PLUS_LONG")

        assertThrows<IllegalStateException> { ShortNamesCsv.read(file, NAMES) }
    }

    @Test
    fun `deux titres pour un meme code arretent l import`() {
        // Ils se departageraient par l'ordre des lignes, c'est-a-dire par hasard --
        // et corriger le mauvais des deux ne changerait rien a l'ecran.
        val file = write("${LONG.code},Blanc de poulet", "${LONG.code},Poulet au four")

        assertThrows<IllegalStateException> { ShortNamesCsv.read(file, NAMES) }
    }

    @Test
    fun `un titre du fichier qui contient une virgule traverse entier`() {
        // Le CSV se coupe sur la **premiere** virgule seulement : « Poulet, blanc »
        // est un titre parfaitement legitime, et un split complet en ferait deux
        // colonnes -- donc une erreur de format sur une ligne correcte.
        val file = write("${LONG.code},Poulet, blanc au four")

        assertEquals("Poulet, blanc au four", ShortNamesCsv.read(file, NAMES).single().shortName)
    }

    @Test
    fun `un titre de plus de quarante caracteres arrete l import`() {
        // Distinct du cas precedent : celui-la est plus court que son libelle, donc
        // seule la longueur maximale peut le refuser. Il se tronquerait dans une
        // liste, et un titre tronque ne vaut pas mieux que le libelle qu'il remplace.
        val trop = "T".repeat(ShortNamesCsv.MAX_LENGTH + 1)
        check(trop.length < LONG.name.length) { "sinon c'est l'autre regle qui refuserait" }

        assertThrows<IllegalStateException> { ShortNamesCsv.read(write("${LONG.code},$trop"), NAMES) }
    }

    @Test
    fun `un titre vide arrete l import`() {
        assertThrows<IllegalStateException> { ShortNamesCsv.read(write("${LONG.code},"), NAMES) }
    }

    @Test
    fun `une ligne sans separateur arrete l import`() {
        assertThrows<IllegalStateException> { ShortNamesCsv.read(write(LONG.code), NAMES) }
    }

    @Test
    fun `un en-tete inattendu arrete l import`() {
        val file = File(directory.toFile(), "short-names.csv")
        file.writeText("code,titre\n${LONG.code},Poulet\n")

        assertThrows<IllegalStateException> { ShortNamesCsv.read(file, NAMES) }
    }

    @Test
    fun `un fichier reduit a ses commentaires rend une liste vide`() {
        val file = File(directory.toFile(), "short-names.csv")
        file.writeText("# rien encore\n#\n")

        assertNull(ShortNamesCsv.read(file, NAMES).firstOrNull())
    }

    private fun write(vararg rows: String): File {
        val file = File(directory.toFile(), "short-names.csv")
        file.writeText("code_ciqual,titre_court\n" + rows.joinToString("\n") + "\n")
        return file
    }

    private companion object {
        val COURT = LongLabel("20009", "Bigorneau, cru")
        val LONG = LongLabel("36004", "Poulet, blanc, sans peau, cuit au four, sans matiere grasse ajoutee")
        val AUTRE = LongLabel("36005", "Riz blanc, cuit a l'eau, non sale, avec matiere grasse ajoutee")

        /**
         * Trente-deux caractères : assez pour qu'on demande un titre, assez court
         * pour qu'un titre de trente-quatre le dépasse **sans** franchir la longueur
         * maximale. C'est la seule fixture qui isole la règle « plus court que
         * l'original » de la règle « pas plus de quarante caractères ».
         */
        val JUSTE_LONG = LongLabel("09104", "Riz blanc, cuit, sans sel ajoute")
        const val TITRE_PLUS_LONG = "Riz blanc cuit sans sel ni matiere"

        val NAMES = listOf(COURT, LONG, AUTRE, JUSTE_LONG).associate { it.code to it.name }
    }
}
