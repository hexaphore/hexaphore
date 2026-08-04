package app.hexaphore.gallery

import androidx.lifecycle.ViewModel
import app.hexaphore.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject

/**
 * Le point d'injection unique de l'itération 0.
 *
 * Sa seule raison d'être est de prouver que la chaîne Hilt tient de bout en bout :
 * `HexaphoreApplication` construit le graphe, `PlatformModule` lie le port à son
 * implémentation, et cette date affichée à l'écran ne peut venir que de là.
 *
 * Il expose une valeur simple plutôt qu'un `StateFlow<UiState>` : rien ici n'est
 * asynchrone et ne le sera. La forme décrite dans docs/06-architecture.md arrivera
 * avec le premier écran qui a un état, en tranche 1.
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(clock: Clock) : ViewModel() {
    /** Le jour courant, lu par le port et jamais par `LocalDate.now()`. */
    val today: LocalDate = clock.today()
}
