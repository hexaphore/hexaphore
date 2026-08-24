package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.EraseEverything
import app.hexavore.domain.usecase.ExportBackup
import app.hexavore.domain.usecase.RestoreBackup
import app.hexavore.domain.usecase.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exporter, restaurer, tout effacer.
 *
 * **Les octets traversent, les fichiers non.** Le `ViewModel` ne connaît ni `Uri` ni
 * `ContentResolver` : l'écran lit et écrit le document que le système lui a donné, et
 * ne fait passer ici qu'un tableau d'octets. C'est ce qui permet à ces trois gestes de
 * se tester sans Android.
 *
 * **L'export se fait en deux temps, et l'ordre compte.** Les octets sont produits
 * *avant* que le sélecteur de document s'ouvre : sans cela, ce qu'on écrirait dans le
 * fichier décrirait l'état de l'application au moment où l'utilisateur a fini de
 * parcourir ses dossiers, et non celui où il a demandé l'export. La différence est
 * invisible en démonstration et réelle si une saisie arrive entre les deux.
 */
@HiltViewModel
internal class BackupViewModel @Inject constructor(
    private val exportBackup: ExportBackup,
    private val restoreBackup: RestoreBackup,
    private val eraseEverything: EraseEverything,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = state.asStateFlow()

    /** Le nom proposé au sélecteur, calculé sur l'horloge du domaine et non sur `now()`. */
    fun proposedName(): String = backupFileName(clock.today())

    /**
     * Prépare les octets, puis les remet à [write] — qui est l'écran, avec son document.
     *
     * Un rappel plutôt qu'un état intermédiaire : garder les octets dans le `ViewModel`
     * entre la préparation et l'écriture ferait vivre tout le journal en mémoire
     * jusqu'à ce que l'utilisateur veuille bien choisir un dossier, ou l'abandonne.
     */
    fun onExport(write: (ByteArray) -> Boolean) = working {
        val bytes = exportBackup()
        if (write(bytes)) BackupMessage.Exported(bytes.size) else BackupMessage.ExportFailed
    }

    fun onImport(bytes: ByteArray?) = working {
        when (val outcome = bytes?.let { restoreBackup(it) }) {
            // Le document n'a pas pu etre lu : c'est indiscernable d'un fichier
            // illisible pour qui regarde l'ecran, et les deux se rattrapent pareil --
            // en choisissant un autre fichier.
            null -> BackupMessage.Unreadable
            is RestoreOutcome.Restored -> BackupMessage.Restored(outcome.entryCount)
            is RestoreOutcome.TooRecent -> BackupMessage.TooRecent(outcome.formatVersion)
            RestoreOutcome.Unreadable -> BackupMessage.Unreadable
            RestoreOutcome.Failed -> BackupMessage.RestoreFailed
        }
    }

    fun onErase() = working {
        eraseEverything()
        BackupMessage.Erased
    }

    /** Le compte rendu a été lu ; il ne doit pas réapparaître à la rotation de l'écran. */
    fun onMessageShown() = state.update { it.copy(message = null) }

    /**
     * **Un seul travail à la fois**, et le geste refusé ne dit rien.
     *
     * Refuser en silence plutôt qu'afficher « patientez » : les trois boutons sont
     * désactivés pendant le travail, donc un second geste ne peut venir que d'une
     * course, et une course n'a pas de message à donner à qui que ce soit.
     */
    private fun working(block: suspend () -> BackupMessage) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = block()
            state.update { it.copy(busy = false, message = message) }
        }
    }
}
