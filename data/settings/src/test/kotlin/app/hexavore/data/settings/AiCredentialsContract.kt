package app.hexavore.data.settings

import app.hexavore.core.testing.firstAfter
import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProviderCredentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ce que **les deux** implémentations des réglages d'IA doivent tenir.
 *
 * Le port naît avec deux implémentations — celle en mémoire, contre laquelle l'écran
 * est écrit, et celle qui chiffre — donc il rejoint un contrat **dès sa naissance**,
 * sans attendre qu'un défaut le rappelle ([D53][decisions]).
 *
 * **Ce qu'il ne prouve pas, et qui compte ici plus qu'ailleurs : le chiffrement.** Les
 * cas éprouvent le magasin, pas l'algorithme — la clé enregistrée se relit, un
 * effacement efface, un fournisseur actif le reste. Que la clé soit illisible sur le
 * disque et que le trousseau matériel la protège ne se vérifie que sur un appareil.
 * C'est la couture qu'ouvre `SecretCipher`, et elle est écrite plutôt que subie.
 *
 * [decisions]: docs/11-decisions.md
 */
abstract class AiCredentialsContract {
    /** Le magasin sous test, vide. Tout ce que les cas contiennent y entre par les ports. */
    protected abstract fun store(): AiCredentialsView

    @Test
    fun `au depart, aucun fournisseur n est configure`() = runBlocking {
        val magasin = store()

        assertNull("une configuration est apparue de nulle part", magasin.current())
        assertNull(magasin.observe().first().active)
        // La carte vide, et pas seulement l'absence d'actif : un magasin qui
        // fabriquerait une entree par fournisseur -- avec l'URL par defaut et une cle
        // vide -- afficherait « clé enregistrée » sur une installation neuve.
        assertEquals(emptyMap<AiProvider, ProviderCredentials>(), magasin.observe().first().credentials)
    }

    @Test
    fun `une cle enregistree se relit entiere`() = runBlocking {
        val magasin = store()

        magasin.save(AiProvider.ANTHROPIC, CLE)

        // L'objet entier, et non le seul champ qui nous interesse : ce qui se perd
        // dans un aller-retour se perd sur le champ qu'on n'a pas regarde.
        assertEquals(CLE, magasin.observe().first().credentials[AiProvider.ANTHROPIC])
    }

    @Test
    fun `enregistrer une cle rend son fournisseur actif`() = runBlocking {
        // Renseigner une cle sans s'en servir n'est jamais ce qu'on voulait faire, et
        // un ecran qui demanderait le geste en deux fois se ferait oublier au second.
        val magasin = store()

        magasin.save(AiProvider.ANTHROPIC, CLE)

        assertEquals(AiProvider.ANTHROPIC, magasin.observe().first().active)
        assertEquals(CLE.apiKey, magasin.current()?.apiKey)
    }

    @Test
    fun `la configuration active porte le fournisseur, le modele et l URL`() = runBlocking {
        val magasin = store()

        magasin.save(AiProvider.ANTHROPIC, CLE)

        val configuration = magasin.current()
        assertEquals(AiProvider.ANTHROPIC, configuration?.provider)
        assertEquals(CLE.model, configuration?.model)
        assertEquals(CLE.baseUrl, configuration?.baseUrl)
    }

    @Test
    fun `reenregistrer remplace au lieu d empiler`() = runBlocking {
        val magasin = store()
        magasin.save(AiProvider.ANTHROPIC, CLE)

        magasin.save(AiProvider.ANTHROPIC, CLE.copy(apiKey = ApiKey("sk-ant-corrigee")))

        assertEquals(ApiKey("sk-ant-corrigee"), magasin.current()?.apiKey)
        assertEquals(1, magasin.observe().first().credentials.size)
    }

    @Test
    fun `effacer une cle la retire, et retire ce qui s en servait`() = runBlocking {
        val magasin = store()
        magasin.save(AiProvider.ANTHROPIC, CLE)

        magasin.forget(AiProvider.ANTHROPIC)

        assertNull("une cle effacee ne doit plus servir", magasin.current())
        assertNull(magasin.observe().first().active)
        assertEquals(emptyMap<AiProvider, ProviderCredentials>(), magasin.observe().first().credentials)
    }

    @Test
    fun `on n active pas un fournisseur qu on n a pas renseigne`() = runBlocking {
        // On ne peut pas se servir d'une cle qui n'est pas la, et l'ecran qui
        // proposerait le choix promettrait un appel voue a l'echec.
        val magasin = store()

        magasin.activate(AiProvider.ANTHROPIC)

        assertNull(magasin.observe().first().active)
    }

    @Test
    fun `le flux reemet sur ecriture`() = runBlocking {
        // Une relecture apres coup passerait meme si le flux n'avait jamais reemis,
        // c'est-a-dire meme si le port etait reste une lecture unique.
        val magasin = store()

        val emis = magasin.observe().firstAfter(
            write = { magasin.save(AiProvider.ANTHROPIC, CLE) },
            matching = { it.active == AiProvider.ANTHROPIC },
        )

        assertEquals(CLE, emis.credentials[AiProvider.ANTHROPIC])
    }

    private companion object {
        val CLE = ProviderCredentials(
            apiKey = ApiKey("sk-ant-de-test"),
            model = "claude-opus-5",
            baseUrl = "https://api.anthropic.com/",
        )
    }
}

/**
 * Les deux ports vus ensemble, le temps d'un contrat.
 *
 * Les cas ont besoin d'écrire par l'un et de lire par l'autre : c'est précisément la
 * couture entre « ce que l'écran configure » et « ce que le résolveur utilise », et
 * elle n'est éprouvable qu'en tenant les deux. Les deux implémentations sont déjà une
 * seule classe ; cette interface le dit au contrat sans l'imposer au domaine.
 */
interface AiCredentialsView :
    AiCredentials,
    AiSettings
