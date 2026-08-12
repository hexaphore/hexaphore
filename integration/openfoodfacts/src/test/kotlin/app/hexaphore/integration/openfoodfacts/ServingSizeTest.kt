package app.hexaphore.integration.openfoodfacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * `serving_size` est un champ libre rempli à la main par des contributeurs. Ces cas
 * sont les écritures relevées dans la base, et non celles qu'on imaginerait.
 */
class ServingSizeTest {
    @ParameterizedTest
    @CsvSource(
        "30 g|30.0",
        "30g|30.0",
        "1.5 kg|1500.0",
        "250 ml|250.0",
        "250ML|250.0",
        "33 cl|330.0",
        "2 dl|200.0",
        "1 l|1000.0",
        "12,5 g|12.5",
        delimiter = '|',
    )
    fun `une mesure ecrite se lit en grammes`(text: String, expected: Double) {
        assertEquals(expected, gramsOfServing(text))
    }

    @Test
    fun `la premiere mesure trouvee gagne, pas le premier nombre`() {
        // « 1 verre (200 ml) » : le 1 n'est pas une masse, il compte des verres. Une
        // decoupe sur le premier nombre donnerait une portion d'un gramme.
        assertEquals(200.0, gramsOfServing("1 verre (200 ml)"))
    }

    @Test
    fun `une portion decrite en toutes lettres ne rend rien`() {
        // Et c'est voulu : l'appelant retombe alors sur la convention de Food, qui
        // est 100 g. Deviner « 2 biscuits » produirait un chiffre que personne n'a
        // donne, dans un journal qui fige ce qu'on lui donne.
        assertNull(gramsOfServing("2 biscuits"))
        assertNull(gramsOfServing("une portion"))
    }

    @Test
    fun `une portion nulle ne compte pas`() {
        // Elle existe dans la base, et l'accepter ouvrirait l'ecran de validation sur
        // une ligne a zero gramme -- donc a zero calorie, ce qui est faux.
        assertNull(gramsOfServing("0 g"))
    }

    @Test
    fun `un champ absent ou vide ne rend rien`() {
        assertNull(gramsOfServing(null))
        assertNull(gramsOfServing(""))
    }
}
