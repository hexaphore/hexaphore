package app.hexavore.feature.settings

import android.content.Context
import android.net.Uri
import java.time.LocalDate

/**
 * Le passage entre un document choisi par l'utilisateur et des octets.
 *
 * **Le Storage Access Framework, et aucune permission de stockage.** L'utilisateur
 * désigne lui-même un document — stockage local, Nextcloud, clé USB, peu importe — et
 * l'application n'obtient l'accès qu'à celui-là ([docs/09][donnees]). C'est aussi
 * pourquoi ce chemin n'est **pas** un `BackupTarget` : il n'y a rien à lister et rien à
 * faire tourner, seulement un document par geste.
 *
 * Des fonctions de fichier plutôt qu'une classe injectée, comme `PhotoFiles` : ce qui
 * traverse ensuite est un tableau d'octets, et rien ici ne mérite d'être un port du
 * domaine.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
internal fun readDocument(context: Context, uri: Uri): ByteArray? =
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

/**
 * Écrit les octets dans le document, et dit si cela a tenu.
 *
 * **`truncate` et non `write`.** Un document réutilisé peut être plus grand que ce
 * qu'on y écrit, et le mode par défaut ne raccourcit pas : la queue de l'ancien fichier
 * survivrait derrière le nouveau JSON compressé, produisant une sauvegarde illisible
 * que rien n'aurait signalée avant le jour où l'on en a besoin.
 */
internal fun writeDocument(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return false
    stream.use { it.write(bytes) }
    true
}.getOrDefault(false)

/**
 * Le nom proposé au sélecteur de document.
 *
 * La date et non l'horodatage complet : ce nom sert à reconnaître un fichier dans une
 * liste, et deux exports du même jour se distinguent par ce que le système ajoute
 * lui-même — « (1) », « (2) ». Une heure à la seconde ne rendrait pas le choix plus
 * facile, elle rendrait le nom plus long.
 */
internal fun backupFileName(today: LocalDate): String = "hexavore-$today.json.gz"
