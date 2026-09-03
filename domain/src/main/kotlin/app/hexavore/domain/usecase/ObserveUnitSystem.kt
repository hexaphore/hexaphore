package app.hexavore.domain.usecase

import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Le système d'unités que l'utilisateur a choisi.
 *
 * **Un cas d'usage et non `Profiles` injecté partout.** Le sélecteur d'unités d'une
 * ligne, le journal de poids et l'écran de profil posent tous la même question ; chacun
 * lisant le profil lui-même, chacun aurait décidé pour son compte ce que vaut un profil
 * absent — et l'un d'eux aurait fini par décider autrement.
 *
 * **Sans profil, le métrique.** C'est l'état d'une installation neuve, avant
 * l'onboarding, et c'est aussi ce que porte un profil qui n'a jamais rien choisi.
 *
 * Un magasin illisible rend la même chose plutôt qu'une erreur : ne pas savoir quelles
 * unités afficher n'est pas une raison de refuser un écran.
 */
class ObserveUnitSystem(private val profiles: Profiles) {
    operator fun invoke(): Flow<UnitSystem> = profiles
        .observeProfile()
        .map { it?.unitSystem ?: UnitSystem.METRIC }
        .catch { emit(UnitSystem.METRIC) }
}
