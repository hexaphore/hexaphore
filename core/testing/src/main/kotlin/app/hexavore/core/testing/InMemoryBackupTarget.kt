package app.hexavore.core.testing

import app.hexavore.domain.backup.BackupFile
import app.hexavore.domain.backup.BackupFileId
import app.hexavore.domain.backup.BackupTarget
import java.time.Instant

/**
 * Un endroit où des sauvegardes s'empilent, en mémoire.
 *
 * **Il rend les fichiers du plus récent au plus ancien**, comme le port l'exige et
 * comme le stockage interne le fait. Un faux qui rendrait l'ordre d'écriture aurait
 * laissé passer une rotation qui supprime les mauvais fichiers : c'est exactement la
 * forme de défaut que [D53][decisions] a nommée, le faux plus indulgent que le vrai.
 *
 * [decisions]: docs/11-decisions.md
 */
class InMemoryBackupTarget(var failing: Boolean = false) : BackupTarget {
    private val files = mutableMapOf<String, Pair<BackupFile, ByteArray>>()

    /** Ce que la cible contient, pour qu'un test l'affirme. */
    val names: List<String> get() = files.values.map { it.first.name }

    override suspend fun list() = files.values.map { it.first }.sortedByDescending { it.createdAt }

    override suspend fun write(bytes: ByteArray, at: Instant): BackupFile {
        check(!failing) { "cette cible refuse d'ecrire" }

        val file = BackupFile(
            id = BackupFileId(at.toString()),
            name = "hexavore-$at.json.gz",
            createdAt = at,
            sizeBytes = bytes.size.toLong(),
        )
        files[file.id.value] = file to bytes
        return file
    }

    override suspend fun read(id: BackupFileId): ByteArray? = files[id.value]?.second

    override suspend fun delete(id: BackupFileId) {
        files.remove(id.value)
    }
}
