package app.hexavore.feature.settings

import androidx.compose.runtime.Immutable
import app.hexavore.domain.nutrition.Macro

/**
 * Ce que l'écran « Profil et objectifs » peut déclencher.
 *
 * **Une seule sortie pour les huit réponses du profil**, comme aux cinq questions : les
 * sections modifient l'objet de formulaire et le rendent. Les autres portent chacune
 * une décision — ouvrir l'édition, basculer de mode, corriger un compteur, demander à
 * enregistrer, confirmer, renoncer — et méritent donc leur nom.
 */
@Immutable
internal data class ProfileActions(
    /** Le crayon. Seule porte vers la modification, switch de mode compris ([D60][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val onEdit: () -> Unit,
    val onForm: (ProfileForm) -> Unit,
    val onManual: (Boolean) -> Unit,
    val onMacroChange: (Macro, Double?) -> Unit,
    val onSave: () -> Unit,
    val onConfirm: () -> Unit,
    val onDismissConfirmation: () -> Unit,
    val onClose: () -> Unit,
)
