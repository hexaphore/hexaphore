package app.hexaphore.feature.onboarding

import androidx.compose.runtime.Immutable

/**
 * Ce que l'onboarding peut déclencher.
 *
 * **Une seule sortie pour les neuf réponses**, et non une par champ : les étapes
 * modifient l'objet de réponses et le rendent. C'est le hissage d'état habituel de
 * Compose, et il évite au `ViewModel` d'être une liste de neuf mutateurs qui ne
 * décident de rien — ce que le seuil de fonctions du projet signalait à juste titre.
 *
 * Les quatre autres sont des gestes, pas des champs : elles portent une décision
 * (avancer, reculer, se caler sur la date atteignable, enregistrer) et méritent donc
 * chacune leur nom.
 */
@Immutable
internal data class OnboardingActions(
    val onAnswers: (OnboardingAnswers) -> Unit,
    val onUseReachableDate: () -> Unit,
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onFinish: () -> Unit,
)
