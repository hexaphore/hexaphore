package app.hexaphore.domain.backup

import java.time.Instant

/**
 * Ce que l'application sait d'elle-même, et comment on le remplace.
 *
 * **Le remplacement est complet, jamais une fusion** ([docs/09][donnees]). Fusionner
 * demanderait une résolution de conflits par entité — même identifiant, contenus
 * différents, dates proches — qui produit des données corrompues en silence. Tant
 * qu'il n'y a pas de scénario multi-appareils réel à servir, la complexité n'est pas
 * justifiée.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
interface SnapshotStore {
    /** Tout ce que l'utilisateur a écrit, maintenant. */
    suspend fun capture(): Snapshot

    /**
     * Vide tout, puis écrit [snapshot]. **En une transaction.**
     *
     * Entre les deux, la base serait vide : une lecture concurrente y verrait un
     * journal effacé, et une interruption y laisserait l'application sans rien.
     */
    suspend fun replace(snapshot: Snapshot)

    /**
     * Vide tout, et n'écrit rien.
     *
     * Le bouton « Effacer toutes mes données » de [docs/09][donnees]. Il ne touche pas
     * aux secrets — ce n'est pas ce port qui les range.
     *
     * [donnees]: docs/09-donnees-et-sauvegarde.md
     */
    suspend fun erase()
}

/**
 * Le passage entre un [Snapshot] et les octets d'un fichier.
 *
 * Un port et non une fonction du domaine : le format est du JSON compressé, et le
 * domaine n'a pas à connaître l'un ni l'autre. Ce qu'il connaît est la **règle** —
 * [SNAPSHOT_FORMAT_VERSION], la chaîne de migrations, et le refus d'un fichier plus
 * récent que l'application.
 */
interface SnapshotCodec {
    suspend fun encode(snapshot: Snapshot): ByteArray

    /** Ne lance pas : un fichier illisible est un résultat, pas un accident. */
    suspend fun decode(bytes: ByteArray): SnapshotRead
}

/** Ce qu'on a réussi à lire d'un fichier. */
sealed interface SnapshotRead {
    data class Readable(val snapshot: Snapshot) : SnapshotRead

    /**
     * Le fichier vient d'une version plus récente de l'application.
     *
     * **Refusé, jamais importé partiellement** ([docs/09][donnees]) : les champs qu'on
     * ne sait pas lire seraient silencieusement perdus, et l'utilisateur croirait avoir
     * restauré.
     *
     * [donnees]: docs/09-donnees-et-sauvegarde.md
     */
    data class TooRecent(val formatVersion: Int) : SnapshotRead

    /** Ni du JSON, ni du gzip, ni la bonne forme. */
    data object Unreadable : SnapshotRead
}

/**
 * Un endroit où des sauvegardes s'empilent, et se remplacent.
 *
 * **Deux implémentations interchangeables**, c'est le critère de fin de la tranche 8 :
 * le stockage interne et Google Drive. Ce que ce port ne couvre **pas** est l'export
 * par le Storage Access Framework — l'utilisateur y désigne un document à chaque fois,
 * il n'y a rien à lister ni à faire tourner, et un port qui rendrait `emptyList()` pour
 * la moitié de ses implémentations ne serait pas un port.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
interface BackupTarget {
    /** Les fichiers présents, du plus récent au plus ancien. */
    suspend fun list(): List<BackupFile>

    suspend fun write(bytes: ByteArray, at: Instant): BackupFile

    /** `null` si le fichier a disparu entre la liste et la lecture. */
    suspend fun read(id: BackupFileId): ByteArray?

    suspend fun delete(id: BackupFileId)
}

@JvmInline
value class BackupFileId(val value: String)

/**
 * Un fichier de sauvegarde, tel que l'écran de restauration le présente.
 *
 * Date et taille suffisent à reconnaître le bon sans l'ouvrir — c'est ce que
 * [docs/09][donnees] demande.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
data class BackupFile(val id: BackupFileId, val name: String, val createdAt: Instant, val sizeBytes: Long)

/**
 * Le nombre de sauvegardes conservées dans une cible qui en empile.
 *
 * Cinq et non une : une corruption locale sauvegardée écraserait la seule copie saine.
 * Cinq et non vingt : au-delà, l'utilisateur ne sait plus laquelle choisir.
 */
const val BACKUP_ROTATION = 5
