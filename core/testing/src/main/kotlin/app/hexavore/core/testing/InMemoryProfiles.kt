package app.hexavore.core.testing

import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Le profil, en mémoire.
 *
 * Comme [InMemoryGoals], ce n'est pas une béquille de test : c'est la première
 * implémentation du port, celle contre laquelle l'onboarding a été écrit avant Room.
 *
 * **Le profil est unique.** [save] remplace, il n'empile pas — c'est la règle que
 * `ProfileEntity` tient par une clé primaire constante, et un faux qui garderait deux
 * profils laisserait passer un appelant qui en crée un second à chaque correction.
 */
class InMemoryProfiles(initial: UserProfile? = null) : Profiles {
    private val state = MutableStateFlow(initial)

    /** Ce qui est enregistré, pour qu'un test l'affirme sans passer par un flux. */
    val saved: UserProfile? get() = state.value

    override fun observeProfile(): Flow<UserProfile?> = state

    override suspend fun save(profile: UserProfile) {
        state.value = profile
    }
}
