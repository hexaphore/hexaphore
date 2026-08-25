package app.hexavore.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La mise en page d'un JSON, y compris quand il est cassé.
 *
 * **C'est le cas cassé qui a motivé de ne pas prendre un analyseur.** Le journal
 * tronque les corps à huit mille caractères : les plus longs — ceux d'une boucle
 * d'outillage, ceux d'une réponse inattendue — arrivent avec une accolade en moins, et
 * un analyseur les refuserait en bloc. L'écran afficherait alors une seule ligne
 * illisible, c'est-à-dire l'état qu'on cherchait à corriger.
 */
class JsonIndentTest {
    @Test
    fun `un objet se deplie`() {
        assertEquals("{\n  \"a\": 1\n}", """{"a":1}""".indentedJson())
    }

    @Test
    fun `un JSON tronque se met en page quand meme`() {
        // Le cas qui compte : pas d'accolade fermante, et pourtant lisible.
        val tronque = """{"items":[{"label":"Semoule","quantity":100"""

        val mis = tronque.indentedJson()

        assertTrue(mis.contains("\n"), "un corps tronque doit quand meme se deplier, or $mis")
        assertTrue(mis.contains("Semoule"), mis)
    }

    @Test
    fun `une accolade dans une chaine n ouvre rien`() {
        // Le prompt du projet parle de JSON : sans cette regle, il s'eclaterait sur
        // trente lignes et deviendrait illisible.
        val texte = """{"prompt":"rends un objet {a:1}"}"""

        val mis = texte.indentedJson()

        assertTrue(mis.contains("rends un objet {a:1}"), "la chaine doit rester intacte, or $mis")
    }

    @Test
    fun `un guillemet echappe ne ferme pas la chaine`() {
        val texte = """{"t":"il a dit \"bonjour\", puis {"}"""

        val mis = texte.indentedJson()

        assertTrue(mis.contains("""il a dit \"bonjour\", puis {"""), mis)
    }

    @Test
    fun `les espaces hors des chaines disparaissent`() {
        // Deux sources d'espacement se cumuleraient : celui du fournisseur et le notre.
        assertEquals("{\n  \"a\": 1\n}", """{ "a" : 1 }""".indentedJson())
    }

    @Test
    fun `les espaces dans les chaines restent`() {
        assertTrue("""{"a":"deux mots"}""".indentedJson().contains("deux mots"))
    }

    @Test
    fun `un tableau imbrique s indente par palier`() {
        val mis = """{"a":[1,2]}""".indentedJson()

        assertTrue(mis.contains("\n    1"), "le contenu du tableau est a deux paliers, or $mis")
    }

    @Test
    fun `du texte qui n est pas du JSON traverse`() {
        // Une page d'erreur HTML, un message de passerelle : on n'y comprend rien, et
        // c'est justement ce qu'il faut pouvoir lire.
        assertTrue("Service Unavailable".indentedJson().contains("Service Unavailable"))
    }

    @Test
    fun `une fermeture en trop ne fait pas tomber`() {
        // Une troncature peut couper n'importe ou, y compris apres une fermeture de
        // trop dans un corps deja malforme.
        assertTrue("}}}".indentedJson().isNotEmpty())
    }
}
