package app.hexaphore.feature.onboarding

import androidx.compose.runtime.Immutable
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import app.hexaphore.domain.usecase.GoalPlan
import java.time.LocalDate

/**
 * Les cinq étapes, dans l'ordre.
 *
 * Une énumération plutôt qu'un entier : « étape 3 » ne dit rien à la lecture, et un
 * décalage d'indice se corrigerait en silence sur le mauvais écran.
 */
enum class OnboardingStep {
    /** Nom, une phrase, un bouton, et l'avertissement à accepter. */
    WELCOME,

    /** Date de naissance, sexe, taille, poids actuel. */
    ABOUT_YOU,

    /** Cinq niveaux, chacun décrit par un exemple concret. */
    ACTIVITY,

    /** Perdre / Maintenir / Prendre, puis poids cible et échéance. */
    OBJECTIVE,

    /** Les six chiffres calculés, avec une phrase par compteur. */
    RESULT,
    ;

    val index: Int get() = ordinal

    companion object {
        val COUNT = entries.size
    }
}

/**
 * Ce que l'utilisateur a répondu jusqu'ici.
 *
 * **Tout est nullable, et rien n'est pré-rempli d'une valeur plausible.** Un poids par
 * défaut à 70 kg serait accepté par distraction, et l'objectif calculé serait celui de
 * quelqu'un d'autre. Les étapes sautées prennent leur valeur par défaut **au moment du
 * calcul**, pas dans le formulaire, et les réglages signalent ensuite lesquelles
 * ([docs/02][parcours]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Immutable
data class OnboardingAnswers(
    val disclaimerAccepted: Boolean = false,
    val birthDate: LocalDate? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    val activityLevel: ActivityLevel? = null,
    val strategy: GoalStrategy? = null,
    val targetWeightKg: Double? = null,
    val targetDate: LocalDate? = null,
) {
    /** Ce que l'étape « Vous » demande, et sans quoi aucun calcul n'est possible. */
    val identityComplete: Boolean
        get() = birthDate != null && sex != null && heightCm != null && currentWeightKg != null

    /**
     * Le maintien n'a ni poids cible ni échéance : il n'y a rien à atteindre.
     *
     * Les deux autres stratégies les demandent — sans quoi l'écart calorique vaudrait
     * zéro, et « perdre du poids » produirait un objectif de maintien portant une
     * étiquette qui ment.
     */
    val objectiveComplete: Boolean
        get() = when (strategy) {
            null -> false
            GoalStrategy.MAINTAIN -> true
            else -> targetWeightKg != null && targetDate != null
        }
}

/**
 * L'état de l'écran d'onboarding.
 *
 * [plan] n'est calculé qu'à la dernière étape, et il est recalculé à chaque
 * modification : c'est ce qui permet à l'aperçu de rythme — « ≈ 0,6 kg par semaine » —
 * de suivre les curseurs sans qu'un second calcul existe quelque part.
 */
@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val answers: OnboardingAnswers = OnboardingAnswers(),
    val plan: GoalPlan? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    /**
     * L'étape courante est-elle franchissable ?
     *
     * Seule la première est bloquante au sens strict — l'avertissement doit être
     * accepté. Les autres se sautent, et [docs/02][parcours] le demande explicitement :
     * un utilisateur pressé doit pouvoir arriver à l'accueil.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.WELCOME -> answers.disclaimerAccepted
            else -> true
        }
}
