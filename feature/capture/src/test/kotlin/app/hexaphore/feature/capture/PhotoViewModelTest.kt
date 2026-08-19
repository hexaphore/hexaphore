package app.hexaphore.feature.capture

import app.hexaphore.domain.ai.AiConfiguration
import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.AiSettings
import app.hexaphore.domain.ai.ApiKey
import app.hexaphore.domain.ai.EstimatedUnit
import app.hexaphore.domain.ai.FoodRecognizer
import app.hexaphore.domain.ai.InMemoryPendingRecognition
import app.hexaphore.domain.ai.PhotoConsent
import app.hexaphore.domain.ai.Recognition
import app.hexaphore.domain.ai.RecognitionInput
import app.hexaphore.domain.ai.RecognitionOutcome
import app.hexaphore.domain.ai.RecognizedItem
import app.hexaphore.domain.diary.EntrySource
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
 * La modale photo, sans appareil.
 *
 * Elle ne voit ni caméra, ni galerie, ni `Uri` : l'écran lui remet un JPEG déjà réduit.
 * C'est ce qui rend éprouvable **tout ce qui coûte de l'argent ou expose une donnée** —
 * le consentement, l'annulation, ce qui est déposé et ce qui survit à un échec — alors
 * que la prise de vue elle-même ne s'éprouve qu'en tenant le téléphone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PhotoViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val pending = InMemoryPendingRecognition()
    private val consent = RecordingConsent()
    private val sent = mutableListOf<RecognitionInput.Photo>()
    private var outcome: RecognitionOutcome = RecognitionOutcome.Recognized(Recognition(listOf(RIZ)))

    private val recognizer = FoodRecognizer { input ->
        sent += input as RecognitionInput.Photo
        outcome
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `rien ne part avant que l avertissement soit accepte`() = runTest {
        // La regle de docs/05 : le mode photo envoie une image de son repas a un tiers,
        // et ca se dit avant, une fois. Un envoi qui precede l'accord rendrait
        // l'avertissement decoratif.
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.consentNeeded)
        assertEquals(emptyList<RecognitionInput.Photo>(), sent)
        assertNull(pending.take())
    }

    @Test
    fun `l avertissement nomme le fournisseur`() = runTest {
        // « votre photo part chez Anthropic » se verifie ; « chez votre fournisseur »
        // ne se verifie pas.
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals("Anthropic", viewModel.uiState.value.provider)
    }

    @Test
    fun `accepter enregistre l accord et envoie dans la foulee`() = runTest {
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onAnalyse()
        advanceUntilIdle()

        viewModel.onConsent()
        advanceUntilIdle()

        assertTrue(consent.given, "l accord doit survivre a la fermeture de l ecran")
        assertTrue(viewModel.uiState.value.analysed)
        assertEquals(EntrySource.PHOTO_AI, pending.take()?.source)
    }

    @Test
    fun `un accord deja donne ne se redemande pas`() = runTest {
        consent.given = true
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.consentNeeded, "un avertissement repete n est plus lu")
        assertEquals(1, sent.size)
    }

    @Test
    fun `refuser garde la photo et n envoie rien`() = runTest {
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onAnalyse()
        advanceUntilIdle()

        viewModel.onConsentDeclined()

        assertFalse(viewModel.uiState.value.consentNeeded)
        assertEquals(emptyList<RecognitionInput.Photo>(), sent)
        assertTrue(viewModel.uiState.value.photo != null, "changer d avis ne doit pas reprendre la photo")
    }

    @Test
    fun `la note accompagne la photo, debarrassee de ses espaces`() = runTest {
        consent.given = true
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onNote("  l assiette fait 24 cm  ")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals("l assiette fait 24 cm", sent.single().note)
    }

    @Test
    fun `une note vide n est pas une note`() = runTest {
        // Une chaine vide jointe au prompt ferait deux lignes vides dans la demande,
        // pour dire qu'il n'y a rien a dire.
        consent.given = true
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onNote("   ")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertNull(sent.single().note)
    }

    @Test
    fun `un echec garde la photo`() = runTest {
        // docs/02 : la photo est conservee le temps de proposer Reessayer. Sans ca, un
        // reseau absent obligerait a ressortir le telephone au-dessus d'une assiette
        // qu'on est peut-etre en train de manger.
        consent.given = true
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals(AiError.NoNetwork, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.photo != null)
        assertNull(pending.take(), "un echec ne depose rien")
    }

    @Test
    fun `une nouvelle photo efface l echec de la precedente`() = runTest {
        consent.given = true
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onAnalyse()
        advanceUntilIdle()

        viewModel.onPhoto(byteArrayOf(9, 9, 9))

        assertNull(viewModel.uiState.value.error, "ce qui s affichait ne se rapporte plus a ce qu on regarde")
    }

    @Test
    fun `annuler coupe l appel en vol`() = runTest {
        // docs/02 l'ecrit noir sur blanc, et c'est une question d'argent : une requete
        // abandonnee qu'on laisse courir se paie quand meme.
        // Ce qui se mesure est **l'appel**, et non l'etat du bouton : un ecran qui
        // cesse d'afficher « analyse en cours » pendant que la requete continue a
        // exactement la meme apparence, et se paie pareil.
        consent.given = true
        var coupe = false
        val viewModel = PhotoViewModel(
            recognizer = {
                try {
                    awaitCancellation()
                } finally {
                    coupe = true
                }
            },
            pending = pending,
            consent = consent,
            settings = SETTINGS,
        )
        viewModel.onPhoto(JPEG)
        viewModel.onAnalyse()

        viewModel.onCancel()

        assertTrue(coupe, "la requete en vol doit etre coupee, pas seulement masquee")
        assertFalse(viewModel.uiState.value.analysing)
    }

    @Test
    fun `sans photo, rien ne s analyse`() = runTest {
        consent.given = true
        val viewModel = viewModel()

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals(emptyList<RecognitionInput.Photo>(), sent)
    }

    private fun viewModel() = PhotoViewModel(
        recognizer = recognizer,
        pending = pending,
        consent = consent,
        settings = SETTINGS,
    )

    /**
     * Un consentement qui se souvient, comme le vrai — sans fichier.
     *
     * Le champ ne s'appelle pas `accepted` : Kotlin distingue la propriété de la
     * fonction du même nom, mais un lecteur, non.
     */
    private class RecordingConsent(var given: Boolean = false) : PhotoConsent {
        override suspend fun accepted(): Boolean = given

        override suspend fun accept() {
            given = true
        }
    }

    private companion object {
        val JPEG = byteArrayOf(1, 2, 3)
        val RIZ = RecognizedItem(label = "riz", quantity = 1.0, unit = EstimatedUnit.BOWL, confidence = 0.9f)

        val SETTINGS = AiSettings {
            AiConfiguration(
                provider = AiProvider.ANTHROPIC,
                apiKey = ApiKey("sk-ant-de-test"),
                model = "claude-opus-5",
                baseUrl = "https://api.anthropic.com/",
            )
        }
    }
}
