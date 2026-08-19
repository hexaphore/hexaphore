package app.hexaphore.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexaphore.domain.ai.PhotoConsent
import app.hexaphore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Le consentement photo, rangé à côté des clés.
 *
 * **Le même fichier de préférences**, et ce n'est pas de la paresse : effacer ses
 * réglages d'IA doit effacer le consentement avec, parce que quelqu'un qui repart de
 * zéro n'a rien accepté. Un second fichier aurait laissé survivre l'accord à
 * l'effacement de ce à quoi il se rapporte.
 *
 * **Non chiffré**, à la différence des clés : ce n'est pas un secret, c'est une trace
 * de décision. Le chiffrer coûterait une lecture de Keystore à chaque ouverture de la
 * modale photo pour protéger un booléen que personne ne convoite.
 *
 * La lecture passe par le dispatcher d'entrées-sorties : `SharedPreferences` lit un
 * fichier au premier accès, et le fil principal n'a pas à l'attendre.
 */
internal class StoredPhotoConsent(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : PhotoConsent {
    override suspend fun accepted(): Boolean = withContext(dispatchers.io) {
        preferences.getBoolean(PHOTO_CONSENT, false)
    }

    override suspend fun accept() = withContext(dispatchers.io) {
        preferences.edit { putBoolean(PHOTO_CONSENT, true) }
    }
}

private const val PHOTO_CONSENT = "photo_consent"
