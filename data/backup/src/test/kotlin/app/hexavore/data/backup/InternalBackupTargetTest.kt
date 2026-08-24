package app.hexavore.data.backup

import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.backup.BackupFileId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

/**
 * La première implémentation de `BackupTarget`, sur de vrais fichiers.
 *
 * **L'ordre est ce qui compte le plus ici.** La rotation supprime « ce qui dépasse »
 * après avoir listé, et une liste rendue dans l'ordre d'écriture — ou dans celui du
 * système de fichiers, qui n'en promet aucun — ferait supprimer les sauvegardes les
 * plus récentes en croyant retirer les plus vieilles.
 */
class InternalBackupTargetTest {
    @TempDir
    lateinit var repertoire: File

    private val cible by lazy { InternalBackupTarget(repertoire, TestDispatchers(Dispatchers.IO)) }

    @Test
    fun `un repertoire absent n est pas une erreur`() = runBlocking {
        assertEquals(emptyList<Any>(), InternalBackupTarget(File(repertoire, "jamais"), dispatchers).list())
    }

    @Test
    fun `ce qui est ecrit se relit octet pour octet`() = runBlocking {
        val ecrit = cible.write(CONTENU, LUNDI)

        assertArrayEquals(CONTENU, cible.read(ecrit.id))
    }

    @Test
    fun `un fichier disparu se lit comme absent`() = runBlocking {
        assertNull(cible.read(BackupFileId("hexavore-jamais-ecrit.json.gz")))
    }

    @Test
    fun `les fichiers sortent du plus recent au plus ancien`() = runBlocking {
        cible.write(CONTENU, LUNDI)
        cible.write(CONTENU, LUNDI.plusSeconds(JOUR))
        cible.write(CONTENU, LUNDI.minusSeconds(JOUR))

        val dates = cible.list().map { it.createdAt }

        assertEquals(dates.sortedDescending(), dates, "c'est l'ordre que la rotation suppose")
    }

    @Test
    fun `supprimer retire vraiment le fichier`() = runBlocking {
        val ecrit = cible.write(CONTENU, LUNDI)

        cible.delete(ecrit.id)

        assertEquals(emptyList<Any>(), cible.list())
    }

    @Test
    fun `le nom porte la date, sans deux-points`() = runBlocking {
        // Un fichier exporte finit sur une cle, un Nextcloud, un Windows -- et les
        // deux-points d'une heure ISO y sont interdits.
        val ecrit = cible.write(CONTENU, LUNDI)

        assertEquals("hexavore-2026-08-17T10-00-00.json.gz", ecrit.name)
        assertTrue(':' !in ecrit.name)
    }

    @Test
    fun `l ordre des noms est l ordre du temps`() = runBlocking {
        // C'est ce qui permet de relire un repertoire a l'oeil, et de reconnaitre la
        // derniere sauvegarde sans lire les dates de fichier.
        cible.write(CONTENU, LUNDI)
        cible.write(CONTENU, LUNDI.plusSeconds(JOUR))

        val noms = cible.list().map { it.name }

        assertEquals(noms.sortedDescending(), noms)
    }

    @Test
    fun `la taille annoncee est celle du fichier`() = runBlocking {
        assertEquals(CONTENU.size.toLong(), cible.write(CONTENU, LUNDI).sizeBytes)
    }

    private val dispatchers = TestDispatchers(Dispatchers.IO)

    private companion object {
        val LUNDI: Instant = Instant.parse("2026-08-17T10:00:00Z")
        const val JOUR = 86_400L
        val CONTENU = byteArrayOf(1, 2, 3, 4, 5)
    }
}
