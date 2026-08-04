package app.hexaphore.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidImportInDomainTest {
    @Test
    fun `signale un import android`() {
        val findings =
            AndroidImportInDomain().lint(
                """
                import android.os.Build

                val version = Build.VERSION.SDK_INT
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `signale un import androidx`() {
        val findings =
            AndroidImportInDomain().lint(
                """
                import androidx.room.Entity
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `le message explique pourquoi et ou mettre le code`() {
        val message = AndroidImportInDomain().lint("import android.os.Build").single().message

        assertTrue(message.contains("adaptateur"), "le message doit indiquer ou va le code Android")
        assertTrue(message.contains("docs/06-architecture.md"), "le message doit renvoyer a la doc")
    }

    @Test
    fun `laisse passer les imports du domaine`() {
        val findings =
            AndroidImportInDomain().lint(
                """
                import java.time.LocalDate
                import kotlinx.coroutines.CoroutineDispatcher
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }
}
