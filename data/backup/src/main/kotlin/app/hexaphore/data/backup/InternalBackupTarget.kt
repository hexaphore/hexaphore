package app.hexaphore.data.backup

import android.content.Context
import app.hexaphore.domain.backup.BackupFile
import app.hexaphore.domain.backup.BackupFileId
import app.hexaphore.domain.backup.BackupTarget
import app.hexaphore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Des sauvegardes dans le stockage interne de l'application.
 *
 * **Ce n'est pas une sauvegarde au sens où l'utilisateur l'entend** : ces fichiers
 * meurent avec l'application, et ils ne protègent de rien si le téléphone est perdu.
 * Ils servent à autre chose — la copie de sécurité écrite juste avant qu'une
 * restauration n'écrase tout, celle qui permet de revenir en arrière quand on a choisi
 * le mauvais fichier ([docs/09][donnees]).
 *
 * C'est aussi la **première implémentation de [BackupTarget]**, celle contre laquelle
 * la rotation et l'ordre se vérifient sans OAuth ni réseau. Drive sera la seconde, et
 * le critère de fin de tranche est qu'elles soient interchangeables.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
class InternalBackupTarget(private val directory: File, private val dispatchers: DispatcherProvider) : BackupTarget {
    constructor(context: Context, dispatchers: DispatcherProvider, name: String) :
        this(File(context.filesDir, name), dispatchers)

    override suspend fun list(): List<BackupFile> = withContext(dispatchers.io) {
        directory
            .listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            .orEmpty()
            .map { it.toBackupFile() }
            // Du plus recent au plus ancien : c'est l'ordre que la rotation suppose,
            // et celui dans lequel un ecran de restauration les presente.
            .sortedByDescending { it.createdAt }
    }

    override suspend fun write(bytes: ByteArray, at: Instant): BackupFile = withContext(dispatchers.io) {
        directory.mkdirs()
        File(directory, fileName(at)).also { it.writeBytes(bytes) }.toBackupFile()
    }

    override suspend fun read(id: BackupFileId): ByteArray? = withContext(dispatchers.io) {
        File(directory, id.value).takeIf { it.isFile }?.readBytes()
    }

    override suspend fun delete(id: BackupFileId) {
        withContext(dispatchers.io) { File(directory, id.value).delete() }
    }

    /**
     * L'identifiant **est** le nom du fichier.
     *
     * Rien d'autre ne le distingue dans un répertoire, et inventer un identifiant à
     * côté demanderait un index à tenir à jour — un index qui se désynchroniserait le
     * jour où quelqu'un copie un fichier à la main.
     */
    private fun File.toBackupFile() = BackupFile(
        id = BackupFileId(name),
        name = name,
        createdAt = stampOf(name) ?: Instant.ofEpochMilli(lastModified()),
        sizeBytes = length(),
    )
}

/**
 * L'instant **écrit dans le nom**, et non la date du fichier.
 *
 * La campagne l'a montré : `lastModified()` a une granularité que deux écritures
 * rapprochées franchissent parfois ensemble, et la rotation triait alors dans l'ordre
 * d'arrivée du système de fichiers — c'est-à-dire qu'elle pouvait supprimer la
 * sauvegarde la plus récente en croyant retirer la plus vieille. Le nom, lui, porte
 * l'instant que l'application a choisi, et il survit à une copie.
 *
 * `null` pour un fichier déposé à la main sous un autre nom : sa date de modification
 * reste alors la seule chose qu'on sache de lui.
 */
private fun stampOf(name: String): Instant? = runCatching {
    STAMP.parse(name.removePrefix(PREFIX).removeSuffix(EXTENSION), Instant::from)
}.getOrNull()

/**
 * `hexaphore-{ISO8601}.json.gz`, comme [docs/09][donnees] le nomme.
 *
 * Les deux-points d'une heure ISO sont interdits dans un nom de fichier sur la plupart
 * des systèmes — et un fichier exporté finit sur une clé, un Nextcloud, un Windows. Ils
 * deviennent des tirets ; l'ordre lexicographique du nom reste l'ordre chronologique.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
private fun fileName(at: Instant): String = "$PREFIX${STAMP.format(at)}$EXTENSION"

private val STAMP: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd'T'HH-mm-ss")
    .withZone(java.time.ZoneOffset.UTC)

private const val PREFIX = "hexaphore-"
private const val EXTENSION = ".json.gz"
