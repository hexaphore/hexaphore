package app.hexaphore.feature.settings

import androidx.compose.runtime.Immutable
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import app.hexaphore.domain.profile.UserProfile
import app.hexaphore.domain.usecase.GoalPlan
import app.hexaphore.domain.usecase.GoalRequest
import java.time.LocalDate

/**
 * Ce que l'écran affiche et laisse corriger.
 *
 * Tout est nullable **bien que rien ne le soit au chargement** : un champ se vide, et
 * un formulaire qui refuserait de représenter cet état obligerait à réécrire dedans un
 * chiffre que l'utilisateur vient d'effacer.
 *
 * [manual] porte les compteurs fixés à la main. **La présence de la clé est le
 * verrou**, la valeur n'est que le chiffre : un compteur verrouillé dont le champ vient
 * d'être vidé garde donc sa clé, avec `null` pour valeur, et c'est ce qui permet de le
 * signaler au lieu de le déverrouiller en douce.
 */
@Immutable
internal data class ProfileForm(
    val birthDate: LocalDate? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    val activityLevel: ActivityLevel? = null,
    val strategy: GoalStrategy? = null,
    val targetWeightKg: Double? = null,
    val targetDate: LocalDate? = null,
    val manual: Map<Macro, Double?> = emptyMap(),
) {
    /**
     * Ce sans quoi aucun calcul n'est possible.
     *
     * Même règle que la deuxième des cinq questions, réécrite ici plutôt que partagée :
     * l'onboarding la porte sur son propre type de réponses, qui compte en plus un
     * avertissement à accepter et une étape courante. Les mettre en commun demanderait
     * de remanier ce type-là, ce dont cette tranche n'a pas besoin — c'est une
     * duplication choisie, et elle est écrite en [D59][decisions].
     *
     * [decisions]: docs/11-decisions.md
     */
    val identityComplete: Boolean
        get() = birthDate != null && sex != null && heightCm != null && currentWeightKg != null

    /** Le maintien n'a ni poids cible ni échéance : il n'y a rien à atteindre. */
    val objectiveComplete: Boolean
        get() = when (strategy) {
            null -> false
            GoalStrategy.MAINTAIN -> true
            else -> targetWeightKg != null && targetDate != null
        }

    /** Ce compteur est-il fixé à la main ? La clé est le verrou, pas la valeur. */
    fun locked(macro: Macro): Boolean = macro in manual
}

/** Ce qui empêche d'enregistrer. L'écran le traduit en phrase, l'état ne connaît pas les ressources. */
internal enum class ProfileBlocker {
    IDENTITY,
    ACTIVITY,
    OBJECTIVE,
    EMPTY_COUNTER,
}

/**
 * L'état de l'écran « Profil et objectifs ».
 *
 * [plan] est recalculé à chaque correction, et c'est ce qui fait de cet écran un seul
 * calcul plutôt que deux. Il vaut `null` tant qu'un champ manque : afficher six chiffres
 * dérivés d'un formulaire incomplet reviendrait à montrer l'objectif de quelqu'un
 * d'autre, exactement ce que [D56][decisions] a retiré de l'onboarding.
 *
 * [decisions]: docs/11-decisions.md
 */
@Immutable
internal data class ProfileUiState(
    /** La journée de l'horloge, portée par l'état plutôt que relue par l'écran. */
    val today: LocalDate,
    val loaded: Boolean = false,
    /** La lecture du profil ou de l'objectif a échoué ([D39][decisions]). */
    val unreadable: Boolean = false,
    val form: ProfileForm = ProfileForm(),
    val plan: GoalPlan? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    /**
     * Ce qui manque, ou `null` si l'écran peut enregistrer.
     *
     * Une seule règle, lue à deux endroits : le `ViewModel` refuse d'écrire, l'écran
     * s'en sert pour dire quoi. Les deux interrogent cette propriété, donc ils ne
     * peuvent pas diverger.
     */
    val blocker: ProfileBlocker?
        get() = when {
            !form.identityComplete -> ProfileBlocker.IDENTITY
            form.activityLevel == null -> ProfileBlocker.ACTIVITY
            !form.objectiveComplete -> ProfileBlocker.OBJECTIVE
            form.manual.values.any { it == null } -> ProfileBlocker.EMPTY_COUNTER
            else -> null
        }

    val canSave: Boolean get() = blocker == null && !saving

    /**
     * Ce qu'un compteur vaudra une fois enregistré.
     *
     * Le chiffre fixé à la main quand il y en a un, celui du calcul sinon. C'est la
     * même règle que [app.hexaphore.domain.goal.DailyGoal.overriddenBy], appliquée à
     * l'affichage — et c'est délibérément la seule chose que l'écran en redit : la
     * valeur écrite, elle, vient du domaine.
     */
    fun shown(macro: Macro): Double? = if (form.locked(macro)) form.manual[macro] else plan?.goal?.get(macro)
}

/**
 * `null` tant qu'une réponse manque : le calcul ne s'invente pas de valeurs par défaut.
 *
 * Quatre absences, en deux marches plutôt qu'en une conjonction : c'est le seuil de
 * complexité de condition de detekt, et la réponse est de le respecter plutôt que de le
 * relever ([docs/10][qualite]).
 *
 * [qualite]: docs/10-qualite-et-livraison.md
 */
internal fun ProfileForm.toProfile(): UserProfile? = when {
    birthDate == null || sex == null -> null
    heightCm == null || activityLevel == null -> null
    else -> UserProfile(
        birthDate = birthDate,
        sex = sex,
        heightCm = heightCm,
        activityLevel = activityLevel,
    )
}

internal fun ProfileForm.toRequest(): GoalRequest? =
    if (currentWeightKg != null && strategy != null && objectiveComplete) {
        GoalRequest(
            strategy = strategy,
            currentWeightKg = currentWeightKg,
            targetWeightKg = targetWeightKg,
            targetDate = targetDate,
        )
    } else {
        null
    }

/** Les compteurs fixés **et renseignés**. Un champ vidé bloque l'enregistrement, il n'écrit pas `null`. */
internal fun ProfileForm.manualValues(): Map<Macro, Double> =
    manual.mapNotNull { (macro, value) -> value?.let { macro to it } }.toMap()
