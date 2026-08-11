package app.hexaphore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.profile.Profiles
import app.hexaphore.domain.profile.WeightLog
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.CalculateDailyGoal
import app.hexaphore.domain.usecase.GoalRevision
import app.hexaphore.domain.usecase.ReviseGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Relire son profil, le corriger, et voir l'objectif suivre.
 *
 * **Le profil, la pesée et l'objectif sont lus une seule fois**, à l'ouverture, et non
 * observés. Un flux ferait revenir l'état enregistré par-dessus une saisie en cours :
 * il suffirait que l'écriture aboutisse pendant qu'on tape pour que le champ reparte en
 * arrière. C'est le même raisonnement qu'en [D56][decisions] pour la destination de
 * départ — ce qui se décide à l'ouverture se lit à l'ouverture.
 *
 * **Le plan est recalculé à chaque correction**, comme aux cinq questions, et c'est ce
 * qui garantit qu'il n'existe qu'**un** calcul. Le bouton « Recalculer mes objectifs »
 * que [docs/02][parcours] prévoyait disparaît donc : il ne recalculerait rien que
 * l'écran n'ait déjà fait, et deux chemins de calcul finissent par annoncer deux
 * chiffres ([D59][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val profiles: Profiles,
    private val weights: WeightLog,
    private val goals: Goals,
    private val calculate: CalculateDailyGoal,
    private val reviseGoal: ReviseGoal,
    clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(ProfileUiState(today = clock.today()))
    val uiState: StateFlow<ProfileUiState> = state

    init {
        viewModelScope.launch {
            val outcome = runCatching { read() }
            state.update { current ->
                outcome.fold(
                    onSuccess = { current.copy(loaded = true, form = it).recalculated() },
                    // Un echec de lecture se dit (D39). Presenter un formulaire vide
                    // laisserait croire qu'aucun profil n'existe, et l'enregistrer
                    // ecraserait celui qui est en base.
                    onFailure = { current.copy(loaded = true, unreadable = true) },
                )
            }
        }
    }

    /**
     * Les réponses, remplacées en bloc.
     *
     * Choisir « Maintenir » efface le poids cible et l'échéance, comme aux cinq
     * questions : les garder ferait calculer un écart calorique pour une stratégie qui
     * n'en veut pas, et l'utilisateur verrait un déficit sous une étiquette « maintien ».
     */
    fun onForm(form: ProfileForm) {
        val cleaned = if (form.strategy == GoalStrategy.MAINTAIN) {
            form.copy(targetWeightKg = null, targetDate = null)
        } else {
            form
        }
        state.update { it.copy(form = cleaned).recalculated() }
    }

    /**
     * Fixe un compteur à la valeur que le calcul propose aujourd'hui.
     *
     * Il part de cette valeur-là et non d'un champ vide : ce qu'on veut corriger est
     * presque toujours le chiffre affiché, de quelques grammes.
     */
    fun onLock(macro: Macro) {
        val proposed = state.value.plan?.goal?.get(macro) ?: return
        state.update { it.withManual(macro, proposed) }
    }

    /** Rend un compteur au calcul. C'est la « confirmation explicite » de docs/02. */
    fun onRelease(macro: Macro) {
        state.update { it.copy(form = it.form.copy(manual = it.form.manual - macro)) }
    }

    fun onCounterChange(macro: Macro, value: Double?) {
        state.update { it.withManual(macro, value) }
    }

    /**
     * Écrit le profil corrigé, la pesée s'il y a lieu, et un **nouvel** objectif.
     *
     * Rien n'est écrit tant que le formulaire est incomplet : le bouton refuse et la
     * barre dit quoi ([D28][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    fun onSave(onDone: () -> Unit) {
        val revision = state.value.toRevision()?.takeIf { state.value.canSave } ?: return
        state.update { it.copy(saving = true, failed = false) }

        viewModelScope.launch {
            val outcome = runCatching { reviseGoal(revision) }
            state.update { it.copy(saving = false, failed = outcome.isFailure) }
            if (outcome.isSuccess) onDone()
        }
    }

    private suspend fun read(): ProfileForm {
        val profile = profiles.observeProfile().first()
        val weight = weights.observeLatest().first()
        val goal = goals.observeCurrent().first()

        return ProfileForm(
            birthDate = profile?.birthDate,
            sex = profile?.sex,
            heightCm = profile?.heightCm,
            // Le poids vient du journal de pesees et non du profil : c'est la
            // derniere mesure connue qui entre dans le calcul.
            currentWeightKg = weight?.weightKg,
            activityLevel = profile?.activityLevel,
            strategy = goal?.strategy,
            targetWeightKg = goal?.targetWeightKg,
            targetDate = goal?.targetDate,
            manual = goal?.manualFields?.associateWith { goal.daily[it] }.orEmpty(),
        )
    }

    private fun ProfileUiState.recalculated(): ProfileUiState {
        val profile = form.toProfile()
        val request = form.toRequest()
        return copy(plan = if (profile != null && request != null) calculate(profile, request) else null)
    }

    private fun ProfileUiState.withManual(macro: Macro, value: Double?): ProfileUiState =
        copy(form = form.copy(manual = form.manual + (macro to value)))

    private fun ProfileUiState.toRevision(): GoalRevision? {
        val profile = form.toProfile()
        val request = form.toRequest()
        val computed = plan
        return if (profile != null && request != null && computed != null) {
            GoalRevision(
                profile = profile,
                request = request,
                // Ce que l'ecran affiche a cet instant, et non un recalcul fait au
                // moment d'ecrire : ce qui est montre est ce qui est ecrit.
                calculated = computed.goal,
                manual = form.manualValues(),
            )
        } else {
            null
        }
    }
}
