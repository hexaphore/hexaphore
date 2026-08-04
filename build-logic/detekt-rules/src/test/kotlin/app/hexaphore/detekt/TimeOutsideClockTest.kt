package app.hexaphore.detekt

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimeOutsideClockTest {
    @Test
    fun `signale System currentTimeMillis`() {
        val findings =
            TimeOutsideClock().lint(
                """
                val maintenant = System.currentTimeMillis()
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `signale LocalDate now`() {
        val findings =
            TimeOutsideClock().lint(
                """
                val jour = LocalDate.now()
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `signale Instant now`() {
        val findings =
            TimeOutsideClock().lint(
                """
                val instant = Instant.now()
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `laisse passer une lecture par le port Clock`() {
        val findings =
            TimeOutsideClock().lint(
                """
                class GetDaySummary(private val clock: Clock) {
                    fun jour() = clock.today()
                    fun instant() = clock.now()
                }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `laisse passer les fichiers declares comme implementations de Clock`() {
        val config = TestConfig("allowedFileNames" to listOf("SystemClock.kt"))
        val code = "val maintenant = System.currentTimeMillis()"

        // Les trois formes que peut prendre KtFile.name selon la facon dont detekt
        // a charge le fichier. La regression corrigee ici : seule la premiere
        // fonctionnait, donc l'implementation de Clock se signalait elle-meme.
        val nomNu = compileContentForTest(code, "SystemClock.kt")
        val cheminWindows = compileContentForTest(code, "C:\\projet\\app\\SystemClock.kt")
        val cheminPosix = compileContentForTest(code, "/home/projet/app/SystemClock.kt")

        assertEquals(0, TimeOutsideClock(config).lint(nomNu).size)
        assertEquals(0, TimeOutsideClock(config).lint(cheminWindows).size)
        assertEquals(0, TimeOutsideClock(config).lint(cheminPosix).size)
    }

    @Test
    fun `un fichier non autorise reste signale, meme designe par son chemin`() {
        val config = TestConfig("allowedFileNames" to listOf("SystemClock.kt"))
        val autreFichier =
            compileContentForTest(
                "val maintenant = System.currentTimeMillis()",
                "C:\\projet\\app\\GetDaySummary.kt",
            )

        assertEquals(1, TimeOutsideClock(config).lint(autreFichier).size)
    }
}
