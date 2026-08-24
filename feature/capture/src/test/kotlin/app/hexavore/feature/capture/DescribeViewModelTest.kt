package app.hexavore.feature.capture

import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.InMemoryPendingRecognition
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.RecognizedItem
import app.hexavore.domain.diary.EntrySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * La modale texte : ce qu'elle envoie, ce qu'elle garde, et ce qu'elle dépose.
 *
 * Ce qui se juge ici n'est pas la reconnaissance — elle a ses cas dans
 * `:integration:ai`, sur un vrai serveur — mais **ce que l'écran fait de l'issue**. Et
 * la règle la plus chère est celle qui ne se voit pas : chaque analyse se paie, donc
 * une phrase vide et un double appui ne doivent pas partir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DescribeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val pending = InMemoryPendingRecognition()
    private val sent = mutableListOf<String>()
    private var outcome: RecognitionOutcome = RecognitionOutcome.Recognized(Recognition(listOf(RIZ)))

    private val recognizer = FoodRecognizer { input ->
        sent += (input as RecognitionInput.Text).description
        outcome
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `une analyse reussie depose la proposition et cede la place`() = runTest {
        val viewModel = viewModel()
        viewModel.onDescription("un bol de riz")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.analysed)
        val proposal = requireNotNull(pending.take())
        assertEquals(listOf(RIZ), proposal.recognition.items)
        assertEquals(EntrySource.TEXT_AI, proposal.source)
    }

    @Test
    fun `la description part debarrassee de ses espaces`() = runTest {
        val viewModel = viewModel()
        viewModel.onDescription("  un bol de riz \n")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals(listOf("un bol de riz"), sent)
    }

    @Test
    fun `un echec garde la phrase et dit ce qui s est passe`() = runTest {
        // docs/02 : un echec reseau ne doit jamais faire retaper une phrase. C'est
        // aussi ce qui permet de corriger un mot et de relancer.
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)
        val viewModel = viewModel()
        viewModel.onDescription("un bol de riz")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals("un bol de riz", viewModel.uiState.value.description)
        assertEquals(AiError.NoNetwork, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.analysed)
        assertNull(pending.take(), "un echec ne depose rien")
    }

    @Test
    fun `une phrase vide n atteint aucun fournisseur`() = runTest {
        // Une analyse se paie. Le bouton est deja grise, mais la garde du ViewModel
        // survit a un changement d'ecran, ce que l'etat du bouton ne fait pas.
        val viewModel = viewModel()
        viewModel.onDescription("   ")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `un second appui pendant l analyse ne repaie pas la meme phrase`() = runTest {
        // Ce qui se compte est le **nombre d'appels**, et non l'etat du bouton : un
        // ecran qui affiche « analyse en cours » pendant qu'un second appel part
        // aurait exactement la meme apparence, et se paierait deux fois.
        var appels = 0
        val muet = DescribeViewModel(
            recognizer = {
                appels += 1
                awaitCancellation()
            },
            pending = pending,
        )
        muet.onDescription("un bol de riz")

        muet.onAnalyse()
        muet.onAnalyse()

        assertEquals(1, appels)
        assertTrue(muet.uiState.value.analysing)
    }

    @Test
    fun `relancer efface l erreur precedente`() = runTest {
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)
        val viewModel = viewModel()
        viewModel.onDescription("un bol de riz")
        viewModel.onAnalyse()
        advanceUntilIdle()

        outcome = RecognitionOutcome.Recognized(Recognition(listOf(RIZ)))
        viewModel.onAnalyse()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.analysed)
    }

    @Test
    fun `revenir sur la modale ne repart pas vers une validation vide`() = runTest {
        val viewModel = viewModel()
        viewModel.onDescription("un bol de riz")
        viewModel.onAnalyse()
        advanceUntilIdle()

        viewModel.onNavigated()

        assertFalse(viewModel.uiState.value.analysed)
    }

    private fun viewModel() = DescribeViewModel(recognizer = recognizer, pending = pending)

    private companion object {
        val RIZ = RecognizedItem(label = "riz", quantity = 1.0, unit = EstimatedUnit.BOWL, confidence = 0.9f)
    }
}
