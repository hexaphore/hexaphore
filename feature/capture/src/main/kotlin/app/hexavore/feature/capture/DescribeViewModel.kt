package app.hexavore.feature.capture

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.PendingRecognition
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.diary.EntrySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ce que la modale « Décrire » montre.
 *
 * **La description ne quitte jamais l'état**, y compris pendant l'analyse et après un
 * échec : [docs/02][parcours] veut qu'un échec réseau ne fasse jamais retaper une
 * phrase. C'est aussi ce qui permet de corriger un mot et de relancer, plutôt que de
 * repartir de la page blanche.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Immutable
internal data class DescribeUiState(
    val description: String = "",
    val analysing: Boolean = false,
    /** L'issue du dernier essai, quand il a échoué. Effacée dès qu'on relance. */
    val error: AiError? = null,
    /**
     * `true` quand la proposition est déposée et que l'écran doit céder la place.
     *
     * Un drapeau plutôt qu'un événement : la navigation est un effet, et l'écran le
     * consomme une fois. Ce que l'analyse a produit n'est pas ici — il attend dans le
     * dépôt, parce qu'une route ne porte pas cinq lignes.
     */
    val analysed: Boolean = false,
) {
    /** Une phrase vide n'a rien à analyser, et une analyse se paie. */
    val analysable: Boolean get() = description.isNotBlank() && !analysing
}

/**
 * La modale texte, entre le clavier et le dépôt des propositions.
 *
 * Elle ne connaît **ni fournisseur ni réseau** : elle appelle `FoodRecognizer`, qui
 * choisit seul le fournisseur configuré, et reçoit une issue plutôt qu'une exception.
 * C'est ce qui la rend vérifiable sur la JVM alors qu'elle déclenche un appel payant.
 *
 * Elle ne résout rien non plus. Ce qu'elle dépose est **la réponse du modèle**, et
 * c'est `OpenDraft` qui en fera un brouillon, avec les quatre autres origines : lui
 * donner le catalogue ferait de cet écran un second endroit qui sait fabriquer un plat.
 *
 * @see docs/05-ia.md
 */
@HiltViewModel
internal class DescribeViewModel @Inject constructor(
    private val recognizer: FoodRecognizer,
    private val pending: PendingRecognition,
) : ViewModel() {
    private val state = MutableStateFlow(DescribeUiState())
    val uiState: StateFlow<DescribeUiState> = state.asStateFlow()

    fun onDescription(text: String) {
        state.update { it.copy(description = text) }
    }

    /**
     * Lance l'analyse, **une seule à la fois**.
     *
     * La garde n'est pas une précaution d'affichage : chaque appel se paie, et un
     * double tap sur « Analyser » achèterait deux fois la même phrase. Le bouton est
     * déjà désactivé pendant l'attente ; cette garde-ci survit à un changement d'écran.
     */
    fun onAnalyse() {
        val description = state.value.description.trim()
        if (description.isEmpty() || state.value.analysing) return

        state.update { it.copy(analysing = true, error = null) }
        viewModelScope.launch {
            state.update { current ->
                when (val outcome = recognizer.recognize(RecognitionInput.Text(description))) {
                    is RecognitionOutcome.Recognized -> {
                        pending.offer(outcome.recognition, EntrySource.TEXT_AI)
                        current.copy(analysing = false, analysed = true)
                    }

                    is RecognitionOutcome.Failed -> current.copy(analysing = false, error = outcome.error)
                }
            }
        }
    }

    /**
     * Après que l'écran est parti vers la validation.
     *
     * Sans quoi revenir en arrière — le geste qui corrige une phrase mal comprise —
     * repartirait aussitôt vers une validation dont le dépôt est déjà vide.
     */
    fun onNavigated() {
        state.update { it.copy(analysed = false) }
    }
}
