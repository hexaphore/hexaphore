package app.hexavore.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'outil de vérification, vérifié.
 *
 * `firstAfter` garde une propriété que trois jeux de tests de contrat éprouvent — un
 * port observe vraiment, il ne rend pas un instantané. S'il rendait la valeur courante
 * sans attendre, ces six cas passeraient **en ne mesurant rien**, et c'est exactement
 * le genre de vert que ce projet a déjà payé deux fois.
 *
 * Le cas qui compte est le second : un flux qui ne ré-émet pas doit faire **échouer**
 * l'attente, pas la faire aboutir.
 */
class FlowEmissionsTest {
    @Test
    fun `rend la valeur emise apres l ecriture`() = runBlocking {
        val flux = MutableStateFlow("avant")

        val apres = flux.firstAfter(write = { flux.value = "apres" }, matching = { it == "apres" })

        assertEquals("apres", apres)
    }

    @Test
    fun `echoue quand le flux ne re-emet pas`() {
        // La propriete dont tout depend. Sans elle, l'outil dirait « observe » d'un
        // port qui rend une lecture unique.
        val fige = flowOf("unique")

        val echec = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fige.firstAfter(write = { }, matching = { true }, timeoutMillis = DELAI_COURT)
            }
        }

        assertTrue(
            echec.message.orEmpty().contains("lecture unique"),
            "le message doit nommer la cause probable, pas seulement l expiration",
        )
    }

    @Test
    fun `ignore les emissions intermediaires qui ne conviennent pas`() {
        // Une ecriture peut en produire plusieurs -- Room invalide, puis relit. Seul
        // l'etat stable se decrit, sinon le test dependrait du nombre d'invalidations.
        val flux = MutableStateFlow(0)

        val apres = runBlocking {
            flux.firstAfter(
                write = {
                    flux.value = 1
                    flux.value = 2
                    flux.value = 3
                },
                matching = { it == 3 },
            )
        }

        assertEquals(3, apres)
    }

    private companion object {
        /** Assez court pour que l'echec attendu ne fasse pas attendre cinq secondes. */
        const val DELAI_COURT = 200L
    }
}
