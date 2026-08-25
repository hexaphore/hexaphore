package app.hexavore.feature.settings

import app.hexavore.core.designsystem.component.messageRes
import app.hexavore.core.testing.InMemoryAiCredentials
import app.hexavore.core.testing.InMemoryAiUsage
import app.hexavore.core.testing.InMemoryDebugSettings
import app.hexavore.core.testing.InMemoryKeyRejection
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiProbe
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProbeOutcome
import app.hexavore.domain.ai.ProviderCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    private val rejection = InMemoryKeyRejection()
    private val debug = InMemoryDebugSettings()
    private val usage = InMemoryAiUsage()
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
    fun `le mode debug s allume et se lit dans l etat`() = runTest {
        // L'ecran montre la porte vers les echanges quand il est allume : sans ce
        // reflet dans l'etat, l'interrupteur bougerait sans que rien ne suive.
        val viewModel = viewModel()

        viewModel.onDebug(enabled = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.debug)
    }

    @Test
    fun `enregistrer une cle oublie le refus precedent`() = runTest {
        // La cle qui avait ete refusee n'est plus celle-la : la pastille n'a plus rien
        // a designer. Elle se rallumera d'elle-meme si la neuve est refusee aussi.
        rejection.note()
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onSave()
        advanceUntilIdle()

        assertFalse(rejection.noted)
    }

    @Test
    fun `utiliser ecrit, active, et le dit sur le bouton`() = runTest {
        // **Le formulaire reste ouvert**, contrairement a avant : le bouton passe de
        // « Utiliser » a « Utilise », et une confirmation doit se lire la ou le doigt a
        // appuye. Une carte qui se replie emporte sa reponse avec elle.
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(CLE.apiKey, credentials.current()?.apiKey)
        assertEquals(AiProvider.ANTHROPIC, viewModel.uiState.value.open, "la carte reste ouverte")
        assertTrue(viewModel.uiState.value.inUse, "le bouton dit « Utilise »")
    }

    @Test
    fun `modifier la cle apres coup redonne le bouton`() = runTest {
        // Sans cela, « Utilise » resterait affiche sous une cle qu'on vient de changer
        // et qui n'est enregistree nulle part -- exactement le contraire de la verite.
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onKey("sk-ant-une-autre")

        assertFalse(viewModel.uiState.value.inUse, "le bouton redevient « Utiliser »")
    }

    @Test
    fun `afficher la cle ne redonne pas le bouton`() = runTest {
        // Montrer sa cle ne la modifie pas : la comparaison porte sur les trois valeurs
        // saisies, jamais sur la revelation.
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onReveal()

        assertTrue(viewModel.uiState.value.inUse, "l oeil n est pas une modification")
    }

    @Test
    fun `un fournisseur qui ne sert pas n a pas le bouton utilise`() = runTest {
        // Deux conditions, pas une : enregistre ne suffit pas, il faut que ce soit
        // celui-la qui analyse.
        val viewModel = viewModel()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onOpen(AiProvider.GEMINI)

        assertFalse(viewModel.uiState.value.inUse, "Gemini n est pas celui qui sert")
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

        assertEquals(ProbeState.Failed(AiError.InvalidKey.messageRes), viewModel.uiState.value.probe)
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

    private fun viewModel() = AiSettingsViewModel(credentials, rejection, debug, probe, usage)

    private companion object {
        val CLE = ProviderCredentials(
            apiKey = ApiKey("sk-ant-de-test"),
            model = "claude-opus-5",
            baseUrl = "https://api.anthropic.com/",
        )
    }
}
