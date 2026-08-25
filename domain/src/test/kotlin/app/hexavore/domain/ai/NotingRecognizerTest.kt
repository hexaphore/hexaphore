package app.hexavore.domain.ai

import app.hexavore.core.testing.InMemoryKeyRejection
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que le décorateur retient d'une analyse, et ce qu'il laisse passer.
 *
 * **Le point délicat est ce qu'il ne fait pas.** Une panne de réseau ou un quota épuisé
 * ne disent rien de la clé : les traiter comme un succès effacerait une pastille juste
 * pour la mauvaise raison, et les traiter comme un refus en allumerait une qui ment.
 */
class NotingRecognizerTest {
    private val rejection = InMemoryKeyRejection()

    @Test
    fun `une cle refusee se retient`() = runTest {
        recognizer(RecognitionOutcome.Failed(AiError.InvalidKey)).recognize(ENTREE)

        assertTrue(rejection.noted)
    }

    @Test
    fun `une analyse qui aboutit oublie le refus precedent`() = runTest {
        rejection.note()

        recognizer(RecognitionOutcome.Recognized(RECONNU)).recognize(ENTREE)

        assertFalse(rejection.noted)
    }

    @Test
    fun `une panne de reseau ne dit rien de la cle`() = runTest {
        rejection.note()

        recognizer(RecognitionOutcome.Failed(AiError.NoNetwork)).recognize(ENTREE)

        assertTrue(rejection.noted, "un timeout n'innocente pas une cle refusee")
    }

    @Test
    fun `un quota epuise n accuse pas la cle`() = runTest {
        // La cle est bonne, l'appel ne passe pas maintenant : allumer la pastille
        // enverrait l'utilisateur corriger ce qui n'est pas en cause.
        recognizer(RecognitionOutcome.Failed(AiError.QuotaExceeded)).recognize(ENTREE)

        assertFalse(rejection.noted)
    }

    @Test
    fun `l issue traverse sans etre modifiee`() = runTest {
        // Le decorateur observe ; s'il transformait quoi que ce soit, les deux ecrans
        // d'IA verraient autre chose que ce que le fournisseur a repondu.
        val issue = RecognitionOutcome.Failed(AiError.InvalidKey)

        assertSame(issue, recognizer(issue).recognize(ENTREE))
    }

    private fun recognizer(outcome: RecognitionOutcome) = NotingRecognizer(FixedRecognizer(outcome), rejection)

    private class FixedRecognizer(private val outcome: RecognitionOutcome) : FoodRecognizer {
        override suspend fun recognize(input: RecognitionInput): RecognitionOutcome = outcome
    }

    private companion object {
        val ENTREE = RecognitionInput.Text("deux oeufs")
        val RECONNU = Recognition(items = emptyList())
    }
}
