package app.hexavore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.food.ContributionSettings
import app.hexavore.domain.food.ContributionSetup
import app.hexavore.domain.food.OffAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Le compte Open Food Facts et la cible d'envoi, rangés sur l'appareil.
 *
 * **Seul le mot de passe passe par le chiffrement**, comme seule la clé d'API y passe
 * pour l'IA. L'identifiant est public sur le site d'Open Food Facts et le drapeau du
 * bac à sable n'est pas un secret ; les chiffrer coûterait deux déchiffrements par
 * lecture pour protéger ce que n'importe qui peut lire.
 *
 * **Son propre fichier de préférences**, distinct de celui des clés d'IA. Effacer ses
 * réglages d'IA ne doit pas déconnecter le compte de contribution : ce sont deux
 * services sans rapport, et les mêler ferait perdre l'un en voulant nettoyer l'autre
 * — c'est le raisonnement de [D86][decisions] sur le compteur de plats.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/04-sources-de-donnees.md
 */
internal class StoredContributionSettings(
    private val preferences: SharedPreferences,
    private val cipher: SecretCipher,
    private val dispatchers: DispatcherProvider,
) : ContributionSettings {
    private val setup = MutableStateFlow(preferences.readContribution(cipher))

    override fun observe(): Flow<ContributionSetup> = setup

    override suspend fun save(account: OffAccount) = write {
        preferences.edit {
            putString(USER_ID, account.userId.trim())
            putString(PASSWORD, cipher.encrypt(account.password))
        }
    }

    override suspend fun forget() = write {
        // Les deux ensemble : un identifiant sans mot de passe n'ouvre rien, et le
        // laisser trainer donnerait a l'ecran de quoi croire qu'un compte est la.
        preferences.edit {
            remove(USER_ID)
            remove(PASSWORD)
        }
    }

    override suspend fun useSandbox(sandbox: Boolean) = write {
        preferences.edit { putBoolean(SANDBOX, sandbox) }
    }

    /** Écrit hors du fil principal, puis **relit** : l'affichage montre le disque. */
    private suspend fun write(block: () -> Unit) = withContext(dispatchers.io) {
        block()
        setup.value = preferences.readContribution(cipher)
    }
}

/**
 * L'état complet, relu depuis les préférences.
 *
 * **Un mot de passe qui ne se déchiffre pas vaut aucun compte**, et non un compte au
 * mot de passe vide. Le trousseau peut avoir perdu sa clé — restauration sur un autre
 * appareil, verrou d'écran retiré — et la seule lecture honnête de ce chiffré est
 * « il n'y a rien ici ». Une chaîne vide ferait partir une écriture vouée au refus,
 * sur une base publique.
 */
private fun SharedPreferences.readContribution(cipher: SecretCipher): ContributionSetup {
    val userId = getString(USER_ID, null)
    val password = getString(PASSWORD, null)?.let(cipher::decrypt)

    return ContributionSetup(
        account = if (userId != null && password != null) OffAccount(userId, password) else null,
        sandbox = getBoolean(SANDBOX, false),
    )
}

private const val USER_ID = "off.user_id"
private const val PASSWORD = "off.password"
private const val SANDBOX = "off.sandbox"
