package app.hexaphore.feature.settings

import androidx.compose.runtime.Immutable
import app.hexaphore.domain.nutrition.Macro

/**
 * Ce que l'écran « Profil et objectifs » peut déclencher.
 *
 * **Une seule sortie pour les huit réponses du profil**, comme aux cinq questions : les
 * sections modifient l'objet de formulaire et le rendent. Les quatre autres portent une
 * décision — fixer un compteur, le rendre au calcul, corriger sa valeur, enregistrer —
 * et méritent donc chacune leur nom.
 */
@Immutable
internal data class ProfileActions(
    val onForm: (ProfileForm) -> Unit,
    val onLock: (Macro) -> Unit,
    val onRelease: (Macro) -> Unit,
    val onCounterChange: (Macro, Double?) -> Unit,
    val onSave: () -> Unit,
    val onClose: () -> Unit,
)
