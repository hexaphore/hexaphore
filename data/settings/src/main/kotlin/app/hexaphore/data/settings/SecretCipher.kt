package app.hexaphore.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffrer et déchiffrer un secret, sans dire avec quoi.
 *
 * **Une couture, et elle est là pour le contrat.** Ce que le stockage doit garantir —
 * qu'une clé enregistrée se relit, qu'un effacement efface, qu'un fournisseur actif
 * reste actif — ne dépend pas de l'algorithme. Séparer les deux permet d'éprouver ces
 * règles-là sur les deux implémentations, et de laisser le chiffrement à ce qu'il est
 * : du code que seul un appareil vérifie vraiment.
 */
internal interface SecretCipher {
    fun encrypt(plain: String): String

    /** `null` quand le texte ne se déchiffre pas — voir [KeystoreCipher.decrypt]. */
    fun decrypt(encoded: String): String?
}

/**
 * Le chiffrement adossé au Keystore d'Android, **sans `EncryptedSharedPreferences`**.
 *
 * [docs/05][ia] prescrivait `EncryptedSharedPreferences`. La bibliothèque qui la porte
 * — `androidx.security:security-crypto` — est **dépréciée** depuis juin 2025, et son
 * avis de dépréciation renvoie explicitement à l'usage direct du Keystore. L'adopter
 * aujourd'hui reviendrait à prendre une dette sur la donnée qu'on a le moins envie de
 * migrer deux fois ([D77][decisions]).
 *
 * Ce qu'elle faisait tient en peu de choses : une clé AES-256 en mode GCM, générée
 * dans le Keystore, qui ne quitte jamais le matériel sécurisé quand l'appareil en a
 * un. Le vecteur d'initialisation est préfixé au chiffré — il n'est pas secret, et le
 * ranger à part aurait fait deux entrées à garder cohérentes.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
internal class KeystoreCipher(private val alias: String = KEY_ALIAS) : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()))
    }

    /**
     * **`null` plutôt qu'une exception, et ce n'est pas de la complaisance.**
     *
     * La clé du Keystore disparaît pour des raisons qui n'ont rien d'exceptionnel :
     * une sauvegarde restaurée sur un autre appareil, un verrouillage d'écran retiré,
     * des données d'application effacées à moitié. Le chiffré survit alors à la clé
     * qui l'ouvrait, et il n'y a rien à en tirer.
     *
     * La bonne lecture est « aucune clé enregistrée » — l'utilisateur recolle la
     * sienne —, pas un plantage à l'ouverture des réglages.
     */
    override fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
    }.getOrNull()

    /**
     * La clé du trousseau, créée au premier usage.
     *
     * Pas de `setUserAuthenticationRequired` : il exigerait un déverrouillage à chaque
     * analyse, sur une application qu'on ouvre pour noter un repas. [docs/05][ia] ne
     * le demande pas, et le refus d'accès serait indiscernable d'une clé invalide.
     *
     * [ia]: docs/05-ia.md
     */
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec
                    .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
        }.generateKey()
    }
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "hexaphore.ai.secrets"
private const val TRANSFORMATION = "AES/GCM/NoPadding"

/** Longueur du vecteur d'initialisation de GCM, en octets. Douze, par convention. */
private const val IV_BYTES = 12
private const val TAG_BITS = 128
private const val KEY_BITS = 256
