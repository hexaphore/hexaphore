package app.hexaphore.domain.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Ce type porte une seule promesse : deux lectures du même produit donnent la même
 * clé. C'est elle que ces cas éprouvent, et c'est d'elle que dépend le cache — donc
 * le « deuxième scan instantané, même en mode avion » de la tranche 5.
 */
class BarcodeTest {
    // Codes reels : Nutella 400 g en EAN-13, et l'exemple canonique de la norme UPC-A.
    private val nutella = "3017620422003"
    private val upcA = "012345678905"

    @Test
    fun `un EAN-13 valide est retenu tel quel`() {
        assertEquals(nutella, Barcode.of(nutella)?.value)
    }

    @Test
    fun `un UPC-A devient le meme code en EAN-13, avec un zero devant`() {
        assertEquals("0$upcA", Barcode.of(upcA)?.value)
    }

    @Test
    fun `un EAN-8 valide reste a huit chiffres`() {
        // 96385074 : l'exemple canonique de la norme EAN-8. Il prouve que la cle de
        // controle se calcule depuis la droite, donc sans dependre de la longueur.
        assertEquals("96385074", Barcode.of("96385074")?.value)
    }

    @Test
    fun `les espaces autour du code ne comptent pas`() {
        assertEquals(nutella, Barcode.of("  $nutella  ")?.value)
    }

    @Test
    fun `une cle de controle fausse est refusee`() {
        // Seul le dernier chiffre change : tout le reste est le code du Nutella.
        assertNull(Barcode.of("3017620422004"))
    }

    @Test
    fun `un UPC-A a la cle fausse est refuse une fois complete`() {
        // La verification a lieu apres l'ajout du zero. Dans l'autre ordre, completer
        // un code faux le rendrait valide sans que rien ne le signale.
        assertNull(Barcode.of("012345678904"))
    }

    @Test
    fun `une longueur qu'aucune symbologie alimentaire n'utilise est refusee`() {
        // Onze chiffres : ni EAN-8, ni UPC-A, ni EAN-13. C'est ce que rend un code de
        // rayonnage ou une etiquette de balance lus par erreur.
        assertNull(Barcode.of("01234567890"))
    }

    @Test
    fun `un code qui n'est pas fait de chiffres est refuse`() {
        assertNull(Barcode.of("301762042200A"))
        assertNull(Barcode.of(""))
    }

    @Test
    fun `un UPC-A et son ecriture EAN-13 sont le meme code`() {
        // La propriete qui fait fonctionner le cache. Sans la mise sous forme
        // canonique, ces deux lectures ecriraient deux fiches pour un seul produit,
        // et le second scan repartirait sur le reseau -- donc echouerait hors ligne.
        assertEquals(Barcode.of("0$upcA"), Barcode.of(upcA))
    }
}
