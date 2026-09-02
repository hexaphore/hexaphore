package app.hexavore.domain.appearance

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que « suivre le système » veut dire, éprouvé sans écran.
 *
 * La règle vit dans le domaine et prend le réglage d'Android en paramètre : c'est ce qui
 * permet de l'affirmer ici plutôt que dans une composition, et ce qui garantit qu'il n'y
 * a qu'un endroit qui la connaisse — la racine de l'application ne fait que fournir la
 * valeur du système.
 */
class ThemeModeTest {
    @Test
    fun `un theme choisi ne depend pas du systeme`() {
        // C'est tout l'objet du reglage : forcer, et ne plus suivre.
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = true), "clair reste clair, meme la nuit")
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false), "sombre reste sombre, meme le jour")
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = true))
    }

    @Test
    fun `suivre le systeme rend ce que le systeme dit`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = false))
    }
}
