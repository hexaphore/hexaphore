package app.hexaphore.domain.usecase

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryAiCredentials
import app.hexaphore.core.testing.InMemoryBackupTarget
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.ai.ApiKey
import app.hexaphore.domain.ai.ProviderCredentials
import app.hexaphore.domain.backup.BACKUP_ROTATION
import app.hexaphore.domain.backup.Snapshot
import app.hexaphore.domain.backup.SnapshotCodec
import app.hexaphore.domain.backup.SnapshotRead
import app.hexaphore.domain.backup.SnapshotStore
import app.hexaphore.domain.food.ContributionSettings
import app.hexaphore.domain.food.ContributionSetup
import app.hexaphore.domain.food.OffAccount
import app.hexaphore.domain.profile.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * La sauvegarde vue du domaine : la rotation, la copie de sécurité, et l'effacement.
 *
 * Ce qui se juge ici est **l'ordre des gestes**, pas le format. Une rotation qui
 * supprime avant d'écrire, une copie de sécurité prise pour un fichier qu'on va
 * refuser, un effacement qui oublie les secrets : trois fautes qui ne se voient pas à
 * la lecture et qui coûtent des données.
 */
class BackupUseCasesTest {
    private val store = FakeSnapshotStore()
    private val codec = FakeCodec()
    private val cible = InMemoryBackupTarget()
    private val securite = InMemoryBackupTarget()

    // --- Ecrire ---------------------------------------------------------------------

    @Test
    fun `une sauvegarde ecrit un fichier`() = runTest {
        val ecrit = createBackup()(cible)

        assertTrue(ecrit.isSuccess)
        assertEquals(1, cible.names.size)
    }

    @Test
    fun `la rotation garde les cinq plus recents`() = runTest {
        repeat(7) { jour -> createBackup(LUNDI.plusDays(jour.toLong()))(cible) }

        assertEquals(BACKUP_ROTATION, cible.names.size)
        assertEquals(LUNDI.plusDays(6), cible.list().first().createdAt.jour())
        assertEquals(LUNDI.plusDays(2), cible.list().last().createdAt.jour())
    }

    @Test
    fun `la copie de securite n en garde qu une`() = runTest {
        repeat(3) { jour -> createBackup(LUNDI.plusDays(jour.toLong()))(securite, keep = 1) }

        assertEquals(1, securite.names.size)
        assertEquals(LUNDI.plusDays(2), securite.list().single().createdAt.jour())
    }

    @Test
    fun `une cible qui refuse ne fait pas tomber l appelant`() = runTest {
        cible.failing = true

        assertTrue(createBackup()(cible).isFailure, "une cible pleine ou absente est un resultat, pas un plantage")
    }

    @Test
    fun `une ecriture qui echoue ne supprime rien`() = runTest {
        // La rotation vient apres l'ecriture, jamais avant : supprimer d'abord et
        // echouer ensuite retirerait une copie saine sans en produire de neuve.
        repeat(BACKUP_ROTATION) { jour -> createBackup(LUNDI.plusDays(jour.toLong()))(cible) }
        cible.failing = true

        createBackup(LUNDI.plusDays(9))(cible)

        assertEquals(BACKUP_ROTATION, cible.names.size)
    }

    // --- Restaurer ---------------------------------------------------------------------

    @Test
    fun `restaurer remplace tout le contenu`() = runTest {
        val resultat = restoreBackup()(FICHIER)

        assertEquals(RestoreOutcome.Restored(0), resultat)
        assertEquals(RESTAURE, store.content)
    }

    @Test
    fun `restaurer prend une copie de securite avant d ecraser`() = runTest {
        restoreBackup()(FICHIER)

        assertEquals(1, securite.names.size)
    }

    @Test
    fun `un fichier trop recent ne touche a rien`() = runTest {
        codec.read = SnapshotRead.TooRecent(99)

        val resultat = restoreBackup()(FICHIER)

        assertEquals(RestoreOutcome.TooRecent(99), resultat)
        assertNull(store.content, "rien n'a ete ecrit")
        assertTrue(securite.names.isEmpty(), "et la copie de securite precedente n'a pas ete consommee")
    }

    @Test
    fun `un fichier illisible ne touche a rien`() = runTest {
        codec.read = SnapshotRead.Unreadable

        assertEquals(RestoreOutcome.Unreadable, restoreBackup()(FICHIER))
        assertNull(store.content)
        assertTrue(securite.names.isEmpty())
    }

    @Test
    fun `une ecriture qui echoue laisse la copie de securite`() = runTest {
        store.failing = true

        assertEquals(RestoreOutcome.Failed, restoreBackup()(FICHIER))
        assertEquals(1, securite.names.size, "c'est elle qui permet de revenir en arriere")
    }

    // --- Effacer ---------------------------------------------------------------------

    @Test
    fun `effacer emporte le contenu et les secrets`() = runTest {
        val cles = InMemoryAiCredentials()
        val contribution = FakeContributionSettings()
        cles.save(
            AiProvider.ANTHROPIC,
            ProviderCredentials(ApiKey("sk-ant-de-test"), model = "claude-opus-5", baseUrl = "https://exemple/"),
        )
        store.content = RESTAURE

        EraseEverything(store, cles, contribution)()

        assertNull(store.content)
        assertNull(cles.observe().first().credentials[AiProvider.ANTHROPIC], "une cle oubliee ne revient pas")
        assertTrue(contribution.forgotten, "le compte Open Food Facts part avec")
    }

    @Test
    fun `effacer ne touche pas aux sauvegardes`() = runTest {
        // Ce sont des fichiers ranges ailleurs -- un Drive, une cle. Les supprimer
        // sans le demander detruirait la seule chose qui restait.
        createBackup()(cible)

        EraseEverything(store, InMemoryAiCredentials(), FakeContributionSettings())()

        assertEquals(1, cible.names.size)
    }

    private fun createBackup(jour: LocalDate = LUNDI) = CreateBackup(store, codec, FixedClock.atNoon(jour))

    private fun restoreBackup() = RestoreBackup(store, codec, createBackup(), securite)

    private fun Instant.jour(): LocalDate = atZone(java.time.ZoneId.of("Europe/Paris")).toLocalDate()

    /** Un magasin qui retient ce qu'on lui écrit, et qui sait refuser. */
    private class FakeSnapshotStore(var failing: Boolean = false) : SnapshotStore {
        var content: Snapshot? = null

        override suspend fun capture(): Snapshot = content ?: VIDE

        override suspend fun replace(snapshot: Snapshot) {
            check(!failing) { "l'ecriture a echoue" }
            content = snapshot
        }

        override suspend fun erase() {
            content = null
        }
    }

    /** Un codec qui ne code rien : ce qui se juge ici est l'ordre des gestes. */
    private class FakeCodec(var read: SnapshotRead = SnapshotRead.Readable(RESTAURE)) : SnapshotCodec {
        override suspend fun encode(snapshot: Snapshot): ByteArray = FICHIER

        override suspend fun decode(bytes: ByteArray): SnapshotRead = read
    }

    private class FakeContributionSettings : ContributionSettings {
        var forgotten = false

        override fun observe(): Flow<ContributionSetup> = MutableStateFlow(ContributionSetup())

        override suspend fun save(account: OffAccount) = Unit

        override suspend fun forget() {
            forgotten = true
        }

        override suspend fun useSandbox(sandbox: Boolean) = Unit
    }

    private companion object {
        val LUNDI: LocalDate = LocalDate.of(2026, 8, 17)
        val MAINTENANT: Instant = Instant.parse("2026-08-17T10:00:00Z")
        val FICHIER = "peu importe".toByteArray()

        val VIDE = Snapshot(exportedAt = MAINTENANT, appVersion = "0.4")
        val RESTAURE = Snapshot(
            exportedAt = MAINTENANT,
            appVersion = "0.4",
            weights = listOf(WeightEntry(LUNDI, 88.4)),
        )
    }
}
