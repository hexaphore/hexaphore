package app.hexaphore.feature.settings

import app.hexaphore.core.testing.InMemoryAiCredentials
import app.hexaphore.domain.ai.AiConfiguration
import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.AiProbe
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.ApiKey
import app.hexaphore.domain.ai.ProbeOutcome
import app.hexaphore.domain.ai.ProviderCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * L'écran des fournisseurs : ce qu'il préremplit, ce qu'il éprouve, ce qu'il écrit.
 *
 * L'affichage n'est pas éprouvé ici — il n'y a pas d'émulateur. Ce qui l'est, c'est la
 * séparation qui structure l'écran et qui casserait en silence : **le formulaire n'est
 * pas les réglages enregistrés**. Tester porte sur ce qu'on tape, enregistrer sur ce
 * qu'on garde, et confondre les deux ferait éprouver une clé pendant qu'on en
 * enregistre une autre.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AiSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val credentials = InMemoryAiCredentials()
    private var probed: AiConfiguration? = null
    private var outcome: ProbeOutcome = ProbeOutcome.Reachable(vision = true)

    private val probe = AiProbe { configuration ->
        probed = configuration
        outcome
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `ouvrir un fournisseur vierge propose son URL et son premier modele`() = runTest {
        // Un champ obligatoire laisse vide est une enigme : celui qui monte un relais
        // saura remplacer l'URL, celui qui colle une cle ne devrait pas avoir a la
        // chercher.
        val viewModel = viewModel()

        viewModel.onOpen(AiProvider.ANTHROPIC)
        advanceUntilIdle()

        assertEquals(AiProvider.ANTHROPIC.defaultBaseUrl, viewModel.uiState.value.form.baseUrl)
        assertEquals(AiProvider.ANTHROPIC.suggestedModels.first(), viewModel.uiState.value.form.model)
        assertEquals("", viewModel.uiState.value.form.apiKey)
    }

    @Test
    fun `ouvrir un fournisseur configure relit ce qui est enregistre`() = runTest {
        credentials.save(AiProvider.ANTHROPIC, CLE)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onOpen(AiProvider.ANTHROPIC)
        advanceUntilIdle()

        // La cle se relit : docs/05 veut un champ masque avec revelation temporaire,
        // pas un champ qu'on ne peut que reecrire.
        assertEquals(CLE.apiKey.value, viewModel.uiState.value.form.apiKey)
        assertEquals(CLE.model, viewModel.uiState.value.form.model)
    }

    @Test
    fun `rouvrir le meme fournisseur le referme`() = runTest {
        val viewModel = viewModel()

        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onOpen(AiProvider.ANTHROPIC)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.open)
    }

    @Test
    fun `tester eprouve ce qui est dans le formulaire, pas ce qui est enregistre`() = runTest {
        // C'est la regle qui justifie que le formulaire existe. Eprouver l'enregistre
        // obligerait a ecrire une cle fausse pour decouvrir qu'elle est fausse.
        credentials.save(AiProvider.ANTHROPIC, CLE)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)

        viewModel.onKey("sk-ant-tapee-a-l-instant")
        viewModel.onTest()
        advanceUntilIdle()

        assertEquals(ApiKey("sk-ant-tapee-a-l-instant"), probed?.apiKey)
    }

    @Test
    fun `tester n enregistre rien`() = runTest {
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onTest()
        advanceUntilIdle()

        assertNull(credentials.current(), "un essai ne doit pas ecrire")
    }

    @Test
    fun `un formulaire incomplet ne part pas sur le reseau`() = runTest {
        // Un modele vide rend un 404 que l'utilisateur lirait comme une cle refusee.
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onModel("")

        viewModel.onTest()
        advanceUntilIdle()

        assertNull(probed, "rien d incomplet ne se paie")
    }

    @Test
    fun `enregistrer ecrit, active et referme`() = runTest {
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(CLE.apiKey, credentials.current()?.apiKey)
        assertNull(viewModel.uiState.value.open, "le formulaire se referme sur ce qui vient d etre ecrit")
    }

    @Test
    fun `toute frappe efface l issue du dernier essai`() = runTest {
        // Un « cle valide » sous une cle qu'on vient de modifier est la pire forme
        // d'information perimee : elle a l'air fraiche.
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)
        viewModel.onTest()
        advanceUntilIdle()

        viewModel.onKey("sk-ant-autre-chose")
        advanceUntilIdle()

        assertEquals(ProbeState.Idle, viewModel.uiState.value.probe)
    }

    @Test
    fun `une cle refusee se dit avec un message, jamais avec un code`() = runTest {
        outcome = ProbeOutcome.Failed(AiError.InvalidKey)
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onTest()
        advanceUntilIdle()

        assertEquals(ProbeState.Failed(R.string.ai_error_invalid_key), viewModel.uiState.value.probe)
    }

    @Test
    fun `effacer retire la cle et ce qui s en servait`() = runTest {
        credentials.save(AiProvider.ANTHROPIC, CLE)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)

        viewModel.onForget()
        advanceUntilIdle()

        assertNull(credentials.current())
    }

    @Test
    fun `la liste montre chaque fournisseur, configure ou non`() = runTest {
        // L'ecran est ecrit contre l'enumeration : le deuxieme fournisseur apparaitra
        // sans qu'une ligne d'affichage bouge.
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(AiProvider.entries.size, viewModel.uiState.value.rows.size)
        assertNotNull(viewModel.uiState.value.rows.firstOrNull { it.provider == AiProvider.ANTHROPIC })
    }

    private fun viewModel() = AiSettingsViewModel(credentials, probe)

    private companion object {
        val CLE = ProviderCredentials(
            apiKey = ApiKey("sk-ant-de-test"),
            model = "claude-opus-5",
            baseUrl = "https://api.anthropic.com/",
        )
    }
}
