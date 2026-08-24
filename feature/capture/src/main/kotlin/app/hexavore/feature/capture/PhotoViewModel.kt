package app.hexavore.feature.capture

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.PendingRecognition
import app.hexavore.domain.ai.PhotoConsent
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.diary.EntrySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * La photo réduite, dans un porteur à identité.
 *
 * **Pas un `ByteArray` nu dans l'état, et pas une `data class` autour.** Une
 * `data class` qui porte un tableau fabrique une égalité fausse — elle compare les
 * références —, donc un état qui ne se compare plus correctement et une recomposition
 * à chaque image. L'identité est justement ce qu'on veut ici : deux photos sont la
 * même quand c'est le même tableau. C'est le raisonnement de `RecognitionInput.Photo`
 * ([docs/05][ia]), appliqué à l'écran qui la produit.
 *
 * [ia]: docs/05-ia.md
 */
@Stable
internal class ReducedPhoto(val jpeg: ByteArray)

/**
 * Ce que la modale photo montre.
 *
 * **La photo survit à l'échec**, et c'est ce que [docs/02][parcours] demande : une
 * clé refusée ou un réseau absent ne doit jamais obliger à ressortir le téléphone
 * au-dessus d'une assiette qu'on est peut-être en train de manger.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Immutable
internal data class PhotoUiState(
    val photo: ReducedPhoto? = null,
    val note: String = "",
    val analysing: Boolean = false,
    val error: AiError? = null,
    /**
     * `true` quand l'avertissement doit être montré avant d'envoyer quoi que ce soit.
     *
     * Distinct de [provider], qui n'est que le nom à écrire dedans : l'un dit s'il
     * faut demander, l'autre à qui la photo partira. Les fondre en une chaîne nulle
     * ferait porter deux sens au même champ, dont l'un — la chaîne vide — se lirait
     * mal.
     */
    val consentNeeded: Boolean = false,
    /**
     * Le fournisseur actif, tel que l'avertissement le nomme.
     *
     * Le nommer en fait une phrase **vérifiable** — « votre photo part chez Mistral »
     * se contredit tout seul si ce n'est pas vrai, là où « chez votre fournisseur » ne
     * se vérifie pas. Vide si rien n'est configuré, cas où l'écran dit autrement.
     */
    val provider: String = "",
    val analysed: Boolean = false,
) {
    val analysable: Boolean get() = photo != null && !analysing
}

/**
 * La modale photo : de l'image réduite au dépôt des propositions.
 *
 * **Elle ne voit ni caméra, ni galerie, ni `Uri`.** L'écran lui remet un JPEG déjà
 * réduit ; d'où viennent ces octets ne la regarde pas, et c'est ce qui la garde
 * vérifiable sur la JVM alors que tout ce qui l'entoure demande un appareil. C'est la
 * division de [D66][decisions] pour le scan, appliquée telle quelle.
 *
 * **Annuler coupe vraiment.** [docs/02][parcours] l'écrit noir sur blanc, et c'est une
 * question d'argent autant que de patience : une requête abandonnée qu'on laisse
 * courir se paie quand même.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class PhotoViewModel @Inject constructor(
    private val recognizer: FoodRecognizer,
    private val pending: PendingRecognition,
    private val consent: PhotoConsent,
    private val settings: AiSettings,
) : ViewModel() {
    private val state = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = state.asStateFlow()

    /** L'analyse en vol, gardée pour pouvoir la couper. */
    private var analysis: Job? = null

    fun onPhoto(jpeg: ByteArray) {
        // Une nouvelle photo efface l'echec de la precedente : ce qui s'affichait ne
        // se rapporte plus a ce qu'on regarde.
        state.update { it.copy(photo = ReducedPhoto(jpeg), error = null) }
    }

    fun onNote(text: String) {
        state.update { it.copy(note = text) }
    }

    /**
     * Lance l'analyse, ou demande d'abord l'accord.
     *
     * L'accord est vérifié **ici** et non à l'ouverture de l'écran : c'est l'envoi qui
     * expose la photo, pas le fait de la prendre. Quelqu'un qui cadre, réfléchit et
     * referme n'a rien envoyé et n'avait donc rien à accepter.
     */
    fun onAnalyse() {
        val photo = state.value.photo ?: return
        if (state.value.analysing) return

        analysis = viewModelScope.launch {
            if (!consent.accepted()) {
                state.update { it.copy(consentNeeded = true, provider = providerName()) }
                return@launch
            }
            analyse(photo)
        }
    }

    /** L'avertissement accepté : on enregistre, et on envoie dans la foulée. */
    fun onConsent() {
        val photo = state.value.photo ?: return

        state.update { it.copy(consentNeeded = false) }
        analysis = viewModelScope.launch {
            consent.accept()
            analyse(photo)
        }
    }

    /** L'avertissement refusé : rien ne part, et la photo reste là. */
    fun onConsentDeclined() {
        state.update { it.copy(consentNeeded = false) }
    }

    /**
     * Coupe l'appel en vol.
     *
     * L'annulation traverse le `withContext` de l'adaptateur et ferme la connexion :
     * ce qui n'est pas parti n'est pas facturé, et ce qui est parti ne sera pas attendu.
     */
    fun onCancel() {
        analysis?.cancel()
        analysis = null
        state.update { it.copy(analysing = false) }
    }

    /**
     * Après que l'écran est parti vers la validation.
     *
     * Sans quoi revenir en arrière — le geste qui reprend une photo mal comprise —
     * repartirait aussitôt vers une validation dont le dépôt est déjà vide.
     */
    fun onNavigated() {
        state.update { it.copy(analysed = false) }
    }

    private suspend fun analyse(photo: ReducedPhoto) {
        state.update { it.copy(analysing = true, error = null) }

        // Rien n'entoure cet appel : une annulation doit traverser. `onCancel` a deja
        // remis l'ecran en etat, et l'attraper ici pour ecrire un echec ferait revivre
        // un etat que l'utilisateur vient de quitter.
        val outcome = recognizer.recognize(RecognitionInput.Photo(photo.jpeg, state.value.note.trim().ifBlank { null }))

        state.update { current ->
            when (outcome) {
                is RecognitionOutcome.Recognized -> {
                    pending.offer(outcome.recognition, EntrySource.PHOTO_AI)
                    current.copy(analysing = false, analysed = true)
                }

                is RecognitionOutcome.Failed -> current.copy(analysing = false, error = outcome.error)
            }
        }
    }

    /** Le fournisseur actif, pour que l'avertissement le nomme. */
    private suspend fun providerName(): String =
        runCatching { settings.current() }.getOrNull()?.provider?.displayName.orEmpty()
}
