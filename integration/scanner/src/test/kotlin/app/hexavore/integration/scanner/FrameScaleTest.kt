package app.hexavore.integration.scanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * La seconde règle du scanner qui s'éprouve sans caméra. Tout ce qui l'entoure — la
 * conversion YUV, la rotation, le liage — ne se vérifie que sur un appareil.
 */
class FrameScaleTest {
    private val bound = 720

    @Test
    fun `une trame plus petite que la borne n est pas agrandie`() {
        // La moitie utile de la fonction. CameraX rend une trame d'analyse de l'ordre
        // de 640x480 : l'agrandir n'ajouterait aucun detail et couterait la memoire.
        assertEquals(1f, frameScale(width = 640, height = 480, maxSide = bound))
    }

    @Test
    fun `le cote long est ramene a la borne`() {
        assertEquals(0.375f, frameScale(width = 1920, height = 1080, maxSide = bound))
    }

    @Test
    fun `le cote long est le plus grand des deux, pas la largeur`() {
        // Une trame debout se reduit sur sa hauteur. Mesurer la largeur laisserait
        // passer une image trois fois trop haute.
        assertEquals(0.375f, frameScale(width = 1080, height = 1920, maxSide = bound))
    }
}
