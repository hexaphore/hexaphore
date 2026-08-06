package app.hexaphore.gallery

import androidx.lifecycle.ViewModel
import app.hexaphore.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject

/**
 * L'horloge de la galerie.
 *
 * Elle a d'abord servi à prouver que la chaîne Hilt tenait de bout en bout, quand
 * la galerie était le seul écran. Elle reste utile pour une raison différente : la
 * date affichée ne peut venir que du port, donc une injection cassée se voit à
 * l'ouverture de l'écran plutôt qu'au premier plantage.
 *
 * Il expose une valeur simple plutôt qu'un `StateFlow<UiState>` : rien ici n'est
 * asynchrone et ne le sera. La forme décrite dans docs/06-architecture.md est celle
 * des écrans réels, qui ont un état ; celui-ci n'en a pas.
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(clock: Clock) : ViewModel() {
    /** Le jour courant, lu par le port et jamais par `LocalDate.now()`. */
    val today: LocalDate = clock.today()
}
