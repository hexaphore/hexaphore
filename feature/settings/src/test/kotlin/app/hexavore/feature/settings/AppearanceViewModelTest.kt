package app.hexavore.feature.settings

import app.hexavore.core.testing.InMemoryAppearanceSettings
import app.hexavore.domain.appearance.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Le thème, lu et écrit.
 *
 * **Le défaut est « suivre le système »**, et c'est la règle qui casserait le plus
 * discrètement : quelqu'un qui installe la mise à jour ne doit pas voir son application
 * changer de couleur parce qu'un réglage neuf a pris une valeur arbitraire.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AppearanceViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = InMemoryAppearanceSettings()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `sans rien de choisi, l ecran suit le systeme`() = runTest(dispatcher) {
        assertEquals(ThemeMode.SYSTEM, modele().uiState.value.theme)
    }

    @Test
    fun `choisir un theme l enregistre et le montre`() = runTest(dispatcher) {
        val modele = modele()

        modele.onTheme(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, settings.current, "le magasin garde le choix")
        assertEquals(ThemeMode.DARK, modele.uiState.value.theme, "l ecran le montre")
    }

    @Test
    fun `revenir au systeme est un choix comme un autre`() = runTest(dispatcher) {
        // Et non un effacement : « suivre le systeme » se range explicitement, sans
        // quoi il faudrait savoir ce qu'un vide veut dire pour le relire.
        val modele = modele()

        modele.onTheme(ThemeMode.LIGHT)
        modele.onTheme(ThemeMode.SYSTEM)
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, settings.current)
    }

    @Test
    fun `un magasin d un autre theme s affiche tel quel`() = runTest(dispatcher) {
        val modele = AppearanceViewModel(InMemoryAppearanceSettings(initial = ThemeMode.LIGHT))
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, modele.uiState.value.theme)
    }

    @Test
    fun `un magasin illisible laisse l ecran ouvert, sur le systeme`() = runTest(dispatcher) {
        // L apparence est un confort : rien n y justifie de refuser l ecran a quelqu un
        // dont le fichier de preferences a ete abime. C est ce repli qui donne son sens
        // au defaut de l etat -- sans lui, il ne serait jamais observe.
        val modele = AppearanceViewModel(InMemoryAppearanceSettings(initial = ThemeMode.DARK, failure = true))
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, modele.uiState.value.theme)
    }

    private fun modele() = AppearanceViewModel(settings)
}
