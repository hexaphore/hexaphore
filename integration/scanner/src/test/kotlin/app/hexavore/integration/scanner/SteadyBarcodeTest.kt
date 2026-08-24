package app.hexavore.integration.scanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * La seule partie du scanner qui s'éprouve sans caméra — donc la seule qui porte une
 * règle. Tout le reste de ce module est du câblage que seul un appareil vérifie.
 */
class SteadyBarcodeTest {
    private val nutella = "3017620422003"
    private val cola = "5449000000996"

    @Test
    fun `deux lectures identiques rendent le code`() {
        val steady = SteadyBarcode()

        assertNull(steady.read(nutella))
        assertEquals(nutella, steady.read(nutella)?.value)
    }

    @Test
    fun `deux lectures differentes ne rendent rien`() {
        // Le cadre a bouge entre deux images, ou deux paquets sont dans le champ.
        val steady = SteadyBarcode()

        steady.read(nutella)

        assertNull(steady.read(cola))
    }

    @Test
    fun `deux codes valides separes par une lecture fausse ne sont pas consecutifs`() {
        // Une lecture que la cle de controle refuse n'est pas « rien » : c'est une
        // lecture, et l'accord n'a pas eu lieu. La compter pour rien reviendrait a
        // valider un code que l'optique n'a jamais confirme.
        val steady = SteadyBarcode()

        steady.read(nutella)
        steady.read("3017620422004")

        assertNull(steady.read(nutella))
    }

    @Test
    fun `une lecture illisible ne rend jamais rien, meme repetee`() {
        val steady = SteadyBarcode()

        steady.read("pas un code")

        assertNull(steady.read("pas un code"))
    }

    @Test
    fun `apres une confirmation, la rafale s arrete`() {
        // La moitie de la regle qu'on oublie : le decodeur rend une lecture par image.
        // Sans ce verrou, l'ecran redemanderait le meme produit trente fois par
        // seconde -- le clignotement que docs/02 decrit.
        val steady = SteadyBarcode()
        steady.read(nutella)
        steady.read(nutella)

        assertNull(steady.read(nutella))
        assertNull(steady.read(nutella))
    }

    @Test
    fun `apres une reprise, il faut de nouveau deux lectures`() {
        // La memoire est effacee, pas seulement le verrou : sinon la premiere image
        // qui suit la reprise confirmerait le code deja traite, et l'ecran
        // rouvrirait une fiche sans qu'on ait rien vise.
        val steady = SteadyBarcode()
        steady.read(nutella)
        steady.read(nutella)

        steady.resume()

        assertNull(steady.read(nutella))
        assertEquals(nutella, steady.read(nutella)?.value)
    }

    @Test
    fun `un UPC-A et son ecriture EAN-13 se confirment l un l autre`() {
        // Les deux designent le meme produit apres mise sous forme canonique (D63).
        // Deux images d'un meme paquet ne doivent pas se contredire pour une
        // difference d'ecriture.
        val steady = SteadyBarcode()

        steady.read("012345678905")

        assertEquals("0012345678905", steady.read("0012345678905")?.value)
    }
}
