package app.hexaphore.data.backup

import app.hexaphore.domain.backup.SNAPSHOT_FORMAT_VERSION
import app.hexaphore.domain.backup.Snapshot
import app.hexaphore.domain.backup.SnapshotCodec
import app.hexaphore.domain.backup.SnapshotRead
import app.hexaphore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Du JSON, compressé en gzip.
 *
 * **Lisible, inspectable, réparable à la main** ([docs/09][donnees]) — pour un projet
 * libre qui héberge les données de santé de ses utilisateurs, cette propriété vaut les
 * quelques kilo-octets de plus face à un format binaire. Le gzip les reprend : un an de
 * journal descend sous les cent kilo-octets, et `gunzip` suffit à revenir au texte.
 *
 * **Indenté**, et c'est la même raison poussée d'un cran : « réparable à la main » veut
 * dire qu'on peut ouvrir le fichier décompressé dans un éditeur et y corriger une
 * valeur. Une seule ligne de deux cent mille caractères ne se répare pas. La
 * compression efface le coût.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
@Singleton
class JsonSnapshotCodec @Inject constructor(private val dispatchers: DispatcherProvider) : SnapshotCodec {
    private val json = Json {
        prettyPrint = true
        // Un fichier ecrit par une version plus recente peut porter des champs qu'on
        // ne connait pas. Les ignorer laisse entrer tout ce qu'on sait lire ; s'y
        // arreter perdrait trois ans de journal pour une nouveaute.
        ignoreUnknownKeys = true
        // Les valeurs par defaut sont ecrites : un fichier qu'on relit a l'oeil doit
        // montrer ce qu'il contient, y compris les zeros et les faux.
        encodeDefaults = true
    }

    override suspend fun encode(snapshot: Snapshot): ByteArray = withContext(dispatchers.io) {
        gzip(json.encodeToString(SnapshotDto.serializer(), snapshot.toDto()).toByteArray())
    }

    override suspend fun decode(bytes: ByteArray): SnapshotRead = withContext(dispatchers.io) {
        runCatching { read(gunzip(bytes)) }.getOrDefault(SnapshotRead.Unreadable)
    }

    /**
     * La version d'abord, le contenu ensuite.
     *
     * Lire `formatVersion` avant de désérialiser est ce qui permet de **refuser** un
     * fichier trop récent au lieu d'en importer la moitié : `ignoreUnknownKeys` en
     * accepterait joyeusement les champs connus et laisserait tomber le reste en
     * silence, ce que [docs/09][donnees] interdit nommément.
     *
     * [donnees]: docs/09-donnees-et-sauvegarde.md
     */
    private fun read(text: String): SnapshotRead {
        val version = (json.parseToJsonElement(text) as JsonObject)
            .getValue(FORMAT_VERSION)
            .jsonPrimitive
            .int

        return if (version > SNAPSHOT_FORMAT_VERSION) {
            SnapshotRead.TooRecent(version)
        } else {
            SnapshotRead.Readable(json.decodeFromString(SnapshotDto.serializer(), migrate(text, version)).toDomain())
        }
    }
}

/**
 * La chaîne de migrations `v1 → v2 → v3`, exactement comme Room.
 *
 * Elle est vide, et c'est normal : il n'existe qu'un format. Elle existe **avant** d'en
 * avoir besoin parce que la première migration s'écrit toujours dans l'urgence d'un
 * changement, et que l'endroit où la mettre doit déjà exister — sans quoi elle finit
 * dans un `if` du lecteur, puis un second, puis on ne sait plus dans quel ordre ils
 * s'appliquent.
 *
 * Chaque étape prend le texte d'une version et rend celui de la suivante.
 */
private fun migrate(text: String, from: Int): String =
    (from until SNAPSHOT_FORMAT_VERSION).fold(text) { current, version -> MIGRATIONS.getValue(version)(current) }

private val MIGRATIONS: Map<Int, (String) -> String> = emptyMap()

private const val FORMAT_VERSION = "formatVersion"

private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
    GZIPOutputStream(out).use { it.write(bytes) }
}.toByteArray()

private fun gunzip(bytes: ByteArray): String = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    .toString(Charsets.UTF_8)
