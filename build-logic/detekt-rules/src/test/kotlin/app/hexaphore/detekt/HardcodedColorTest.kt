package app.hexaphore.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HardcodedColorTest {
    @Test
    fun `signale une couleur construite a la main`() {
        val findings =
            HardcodedColor().lint(
                """
                val cyan = Color(0xFF00E5FF)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `signale une couleur predefinie de Compose`() {
        val findings =
            HardcodedColor().lint(
                """
                val fond = Color.Black
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `laisse passer un jeton du design system`() {
        val findings =
            HardcodedColor().lint(
                """
                val teinte = NeonTheme.macros[Macro.FAT].base
                val fond = MaterialTheme.colorScheme.background
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }
}
