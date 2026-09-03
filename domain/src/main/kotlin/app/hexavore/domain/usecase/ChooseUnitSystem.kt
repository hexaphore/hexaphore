package app.hexavore.domain.usecase

import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.UnitSystem
import kotlinx.coroutines.flow.first

/**
 * Change le système d'unités, sans rien toucher d'autre au profil.
 *
 * **Rien n'est converti dans la base.** Un poids reste en kilogrammes, une taille en
 * centimètres, une ligne de journal garde l'unité dans laquelle elle a été saisie : ce
 * réglage décide de ce qu'on **montre** et de ce qu'on propose de **saisir**, jamais de
 * ce qui est enregistré. C'est ce qui permet de basculer, de regarder, et de revenir
 * sans que le moindre chiffre ait bougé.
 *
 * **Sans profil, rien ne se passe.** Le réglage vit sur le profil, et l'écran Apparence
 * est atteignable avant l'onboarding — sur une installation neuve, il n'y a pas encore
 * de personne à qui attribuer un choix. Créer un profil vide pour y ranger une
 * préférence en inventerait une, avec un poids et une taille que personne n'a donnés.
 */
class ChooseUnitSystem(private val profiles: Profiles) {
    /** @return `false` quand il n'y a pas encore de profil. */
    suspend operator fun invoke(system: UnitSystem): Boolean {
        val profile = profiles.observeProfile().first() ?: return false

        profiles.save(profile.copy(unitSystem = system))
        return true
    }
}
