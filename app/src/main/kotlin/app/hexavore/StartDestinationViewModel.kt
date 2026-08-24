package app.hexavore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.goal.Goals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Par où l'application ouvre.
 *
 * `null` tant qu'on ne sait pas : l'écran n'affiche alors **rien**. C'est délibéré —
 * poser l'accueil comme départ puis sauter vers l'onboarding ferait clignoter un écran
 * de journal vide, et ce clignotement se voit à chaque lancement pendant toute la vie
 * de l'application.
 */
enum class StartDestination {
    ONBOARDING,
    HOME,
}

/**
 * Décide de la première destination, une seule fois.
 *
 * **La question est « un objectif court-il ? » et non « un profil existe-t-il ? ».**
 * C'est l'objectif dont l'accueil a besoin pour afficher ses six jauges ; un profil
 * sans objectif donnerait un accueil sans référence, c'est-à-dire l'état qu'on cherche
 * précisément à ne plus faire traverser.
 *
 * Une lecture qui échoue ouvre sur l'accueil : celui-ci sait dire qu'il n'a pas pu lire
 * ([D39][decisions]), là où l'onboarding proposerait de refaire un profil qui existe
 * peut-être déjà — et l'écraserait.
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
class StartDestinationViewModel @Inject constructor(goals: Goals) : ViewModel() {
    /**
     * **Lu une seule fois**, et non observé.
     *
     * L'objectif apparaît à la fin de l'onboarding, et un flux ferait alors basculer
     * cette valeur. `NavHost` reconstruit son graphe quand sa destination de départ
     * change — ce qui **vide la pile de navigation**. Deux mécanismes se
     * disputeraient alors la même transition, celui-ci et le `popUpTo` explicite de
     * la fin d'onboarding, avec un gagnant qui dépend de l'ordre de recomposition.
     *
     * Une décision de démarrage se prend au démarrage. La suite appartient à la
     * navigation.
     */
    val destination: StateFlow<StartDestination?> =
        flow { emit(goals.observeCurrent().first()) }
            .map { if (it == null) StartDestination.ONBOARDING else StartDestination.HOME }
            .catch { emit(StartDestination.HOME) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = null,
            )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
