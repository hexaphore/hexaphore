package app.hexavore.tooling.ciqual

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Chaque convention d'écriture de CIQUAL a son cas, y compris celles que le XML
 * 2025 ne contient pas.
 *
 * Le piège de cette tranche est écrit depuis la conception : traiter une valeur
 * inconnue comme un zéro. Ces tests sont ce qui le rend impossible à commettre par
 * distraction — mais c'est le dernier bloc, celui des écritures inconnues, qui
 * porte la vraie garantie. Sans lui, ajouter une convention à l'ANSES suffirait à
 * remplir la base de trous ou de zéros, sans qu'aucun test ne rougisse.
 */
class CiqualValueParserTest {
    @ParameterizedTest
    @CsvSource("12,5|12.5", "0,0|0.0", "1140|1140.0", "37,5|37.5", delimiter = '|')
    fun `la virgule est le separateur decimal`(raw: String, expected: Double) {
        assertEquals(expected, known(raw))
    }

    @Test
    fun `des traces valent zero, parce que c est une mesure`() {
        // « traces » n'est pas une lacune : quelqu'un a cherche et n'a presque rien
        // trouve. C'est la seule ecriture de CIQUAL qui donne legitimement zero.
        assertEquals(0.0, known("traces"))
    }

    @ParameterizedTest
    @CsvSource("< 0,5|0.25", "< 0,01|0.005", "< 20|10.0", "< 700|350.0", "<0,1|0.05", delimiter = '|')
    fun `une majoration vaut le milieu de son intervalle`(raw: String, expected: Double) {
        // docs/04 ne cite que « < 0,5 ». L'edition 2025 compte 250 seuils distincts,
        // de « < 0,0001 » a « < 700 » : reconnaitre la seule ecriture citee aurait
        // rejete 16 000 valeurs.
        assertEquals(expected, known(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["-", "NC", "nc", "", "   "])
    fun `un inconnu reste inconnu, et jamais zero`(raw: String) {
        assertEquals(CiqualValue.Unknown, CiqualValueParser.parse(raw))
    }

    @Test
    fun `une valeur absente est inconnue`() {
        assertEquals(CiqualValue.Unknown, CiqualValueParser.parse(null))
    }

    @ParameterizedTest
    @ValueSource(strings = ["environ 12", "12 g", "1 140", "> 5", "12,5,3", "n/a", "~3"])
    fun `une ecriture inconnue n est ni zero ni inconnue`(raw: String) {
        // Le troisieme cas est la raison d'etre de ce parseur. Range avec les
        // inconnus, il effacerait en silence une colonne entiere le jour ou l'ANSES
        // change de convention ; range avec les zeros, il en inventerait une.
        val value = CiqualValueParser.parse(raw)

        assertInstanceOf(CiqualValue.Unrecognised::class.java, value)
        assertEquals(raw, (value as CiqualValue.Unrecognised).raw)
    }

    @Test
    fun `une majoration illisible est une ecriture inconnue`() {
        assertInstanceOf(CiqualValue.Unrecognised::class.java, CiqualValueParser.parse("< beaucoup"))
    }

    @Test
    fun `les espaces autour d une valeur sont ceux du XML`() {
        // Le XML de l'ANSES ecrit « <teneur> 1140 </teneur> » : l'espace est sa mise
        // en forme, pas une donnee.
        assertEquals(1140.0, known(" 1140 "))
    }

    private fun known(raw: String): Double {
        val value = CiqualValueParser.parse(raw)
        assertInstanceOf(CiqualValue.Known::class.java, value, "attendu une valeur connue pour [$raw]")
        return (value as CiqualValue.Known).amount
    }
}
