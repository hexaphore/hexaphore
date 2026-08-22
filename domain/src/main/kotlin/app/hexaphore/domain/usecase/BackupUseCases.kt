package app.hexaphore.domain.usecase

import app.hexaphore.domain.ai.AiCredentials
import app.hexaphore.domain.ai.AiProvider
import app.hexaphore.domain.backup.BACKUP_ROTATION
import app.hexaphore.domain.backup.BackupFile
import app.hexaphore.domain.backup.BackupTarget
import app.hexaphore.domain.backup.SnapshotCodec
import app.hexaphore.domain.backup.SnapshotRead
import app.hexaphore.domain.backup.SnapshotStore
import app.hexaphore.domain.food.ContributionSettings
import app.hexaphore.domain.time.Clock

/**
 * Écrire un instantané dans une cible, et n'y garder que les plus récents.
 *
 * **La rotation vit ici et non dans la cible**, parce que ce n'est pas une propriété du
 * rangement : c'est le nombre de retours en arrière qu'on veut se garder. La copie de
 * sécurité d'avant restauration n'en veut qu'un, Drive en veut cinq, et les deux
 * s'écrivent au même endroit.
 */
class CreateBackup(private val store: SnapshotStore, private val codec: SnapshotCodec, private val clock: Clock) {
    /**
     * @param keep le nombre de fichiers conservés, le plus ancien partant d'abord.
     * @return le fichier écrit, ou l'échec — une cible peut être pleine, absente ou
     *   refusée, et l'appelant doit pouvoir le dire.
     */
    suspend operator fun invoke(target: BackupTarget, keep: Int = BACKUP_ROTATION): Result<BackupFile> = runCatching {
        val written = target.write(codec.encode(store.capture()), clock.now())
        // La rotation apres l'ecriture, jamais avant : supprimer d'abord et echouer
        // ensuite retirerait une copie saine sans en produire de neuve.
        target.list().drop(keep).forEach { target.delete(it.id) }
        written
    }
}

/**
 * Remplacer tout le contenu de l'application par celui d'un fichier.
 *
 * **Une copie de sécurité part d'abord**, dans la cible qu'on lui donne, et elle n'en
 * garde qu'une ([docs/09][donnees]) : c'est ce qui permet de revenir en arrière quand
 * quelqu'un restaure le mauvais fichier. Elle est écrite **après** la lecture du
 * fichier entrant — sauvegarder pour un import qui va être refusé ferait perdre la
 * copie de sécurité précédente sans rien restaurer.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
class RestoreBackup(
    private val store: SnapshotStore,
    private val codec: SnapshotCodec,
    private val createBackup: CreateBackup,
    private val safety: BackupTarget,
) {
    suspend operator fun invoke(bytes: ByteArray): RestoreOutcome = when (val read = codec.decode(bytes)) {
        is SnapshotRead.Readable -> replace(read)
        is SnapshotRead.TooRecent -> RestoreOutcome.TooRecent(read.formatVersion)
        SnapshotRead.Unreadable -> RestoreOutcome.Unreadable
    }

    private suspend fun replace(read: SnapshotRead.Readable): RestoreOutcome = runCatching {
        createBackup(safety, keep = 1)
        store.replace(read.snapshot)
        RestoreOutcome.Restored(read.snapshot.entryCount)
    }.getOrElse { RestoreOutcome.Failed }
}

/** Ce qu'une restauration a donné. */
sealed interface RestoreOutcome {
    data class Restored(val entryCount: Int) : RestoreOutcome

    /** Le fichier vient d'une version plus récente. Rien n'a été touché. */
    data class TooRecent(val formatVersion: Int) : RestoreOutcome

    /** Ni du JSON, ni du gzip, ni la bonne forme. Rien n'a été touché. */
    data object Unreadable : RestoreOutcome

    /** L'écriture a échoué. La copie de sécurité, elle, est là. */
    data object Failed : RestoreOutcome
}

/**
 * Tout effacer : le journal, le profil, les objectifs, les clés, les comptes.
 *
 * **Les secrets partent avec le reste**, et c'est le seul endroit du projet qui les
 * touche tous. [docs/09][donnees] le veut ainsi : quelqu'un qui efface ses données ne
 * doit pas retrouver sa clé d'API et son compte Open Food Facts au prochain lancement.
 *
 * Les sauvegardes ne sont pas effacées ici. Ce sont des fichiers que l'utilisateur a
 * rangés ailleurs — dans son Drive, sur une clé — et les supprimer sans le lui demander
 * détruirait la seule chose qui restait.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
class EraseEverything(
    private val store: SnapshotStore,
    private val credentials: AiCredentials,
    private val contribution: ContributionSettings,
) {
    suspend operator fun invoke() {
        store.erase()
        AiProvider.entries.forEach { credentials.forget(it) }
        contribution.forget()
    }
}
