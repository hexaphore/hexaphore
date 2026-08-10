package app.hexaphore.domain.profile

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Le profil, unique.
 *
 * `null` tant que l'onboarding n'a pas eu lieu — c'est ce qui décide de l'écran
 * d'ouverture, et c'est une information, pas un cas d'erreur.
 */
interface Profiles {
    fun observeProfile(): Flow<UserProfile?>

    suspend fun save(profile: UserProfile)
}

/** Une pesée. Une par jour : la dernière du jour remplace la précédente. */
data class WeightEntry(val date: LocalDate, val weightKg: Double)

/**
 * Le journal de poids.
 *
 * **Une pesée par jour, et la dernière gagne.** Se peser trois fois un matin est
 * courant ; en garder trois fausserait la moyenne mobile en donnant trois fois plus de
 * poids à ce jour-là qu'aux autres.
 *
 * Le poids brut est inexploitable — ±2 kg d'un jour à l'autre selon l'hydratation, le
 * sel, le transit — mais c'est lui qu'on stocke : le lissage est un calcul, et un
 * calcul se refait quand sa règle change ([docs/03][calculs]).
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
interface WeightLog {
    /** Les pesées les plus récentes d'abord. */
    fun observeRecent(limit: Int): Flow<List<WeightEntry>>

    /** La dernière pesée connue, celle qui sert au calcul de l'objectif. */
    fun observeLatest(): Flow<WeightEntry?>

    suspend fun record(entry: WeightEntry)
}
