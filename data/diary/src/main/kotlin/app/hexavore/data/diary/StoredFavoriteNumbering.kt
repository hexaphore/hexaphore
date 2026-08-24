package app.hexavore.data.diary

import android.content.SharedPreferences
import androidx.core.content.edit
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.diary.FavoriteNumbering
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Le compteur des noms proposés, dans les préférences.
 *
 * **Pas dans la base**, et ce n'est pas de la paresse : ce n'est pas une donnée du
 * journal. C'est un état d'interface — le dernier numéro qu'on a proposé — qui ne se
 * lit jamais avec autre chose et n'a ni date ni relation. Une table lui aurait apporté
 * une migration et un schéma exporté pour un entier.
 *
 * **Il ne redescend jamais.** Supprimer « Plat 1 » ne fait pas réapparaître ce nom au
 * favori suivant : le compteur avance, il ne compte pas les favoris existants.
 *
 * Le verrou n'est pas décoratif : lire puis écrire n'est pas atomique, et deux
 * ouvertures rapprochées de la boîte de nommage rendraient le même numéro.
 */
class StoredFavoriteNumbering(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : FavoriteNumbering {
    private val lock = Mutex()

    override suspend fun next(): Int = withContext(dispatchers.io) {
        lock.withLock {
            val next = preferences.getInt(NEXT_NUMBER, FIRST_NUMBER)
            preferences.edit { putInt(NEXT_NUMBER, next + 1) }
            next
        }
    }
}

/** Le premier plat s'appelle « Plat 1 » : personne ne compte à partir de zéro. */
private const val FIRST_NUMBER = 1

private const val NEXT_NUMBER = "favorite_next_number"
