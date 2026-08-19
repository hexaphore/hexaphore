package app.hexaphore.feature.settings

import androidx.annotation.StringRes
import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.AiProvider

/**
 * Ce que l'écran des fournisseurs d'IA montre.
 *
 * **Une liste, alors qu'il n'y a qu'un fournisseur.** L'écran est écrit contre
 * l'énumération et non contre Anthropic : le jour où le deuxième arrive, il apparaît
 * sans qu'une ligne d'affichage bouge. Un écran écrit pour un seul aurait demandé
 * d'être généralisé, et [docs/12][plan] rappelle que ce genre de généralisation se
 * fait trois fois.
 *
 * **Un seul formulaire ouvert à la fois** ([open]). Six formulaires dépliés feraient
 * un écran où l'on ne trouve plus le sien, et l'état « lequel est ouvert » est une
 * information de moins à tenir que six états d'ouverture.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
data class AiSettingsUiState(
    val rows: List<ProviderRow> = emptyList(),
    val open: AiProvider? = null,
    val form: ProviderForm = ProviderForm(),
    val probe: ProbeState = ProbeState.Idle,
)

/** Une ligne de la liste : qui, et où il en est. */
data class ProviderRow(val provider: AiProvider, val configured: Boolean, val active: Boolean)

/**
 * Le formulaire ouvert.
 *
 * [revealed] tient la révélation temporaire que [docs/05][ia] demande : le champ est
 * masqué, et un œil le découvre le temps de vérifier une clé qu'on vient de coller.
 * Il retombe à `false` dès qu'on change de fournisseur — une clé révélée qui le reste
 * finirait par l'être devant quelqu'un.
 *
 * [ia]: docs/05-ia.md
 */
data class ProviderForm(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val revealed: Boolean = false,
) {
    /**
     * Rien d'incomplet ne part au réseau.
     *
     * Le modèle et l'URL comptent autant que la clé : un modèle vide rend un `404`
     * que l'utilisateur lirait comme une clé refusée.
     */
    val complete: Boolean get() = apiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()
}

/** Où en est le bouton « Tester ». */
sealed interface ProbeState {
    data object Idle : ProbeState

    /** L'appel est parti. Le bouton ne se rappuie pas, et l'écran le montre. */
    data object Running : ProbeState

    data class Succeeded(val vision: Boolean) : ProbeState

    /**
     * @param detail ce que le fournisseur a répondu, quand l'issue ne suffit pas à le
     *   diagnostiquer. Affiché **ici et nulle part ailleurs** : le bouton « Tester »
     *   est un instrument de diagnostic, et « le service est indisponible » n'y aide
     *   personne — ni celui qui configure, ni celui qui doit corriger.
     */
    data class Failed(@StringRes val messageRes: Int, val detail: String? = null) : ProbeState
}

/**
 * Ce qu'on montre en plus du message, et seulement quand le message ne suffit pas.
 *
 * Une clé refusée n'a pas besoin d'être suivie de la phrase anglaise du fournisseur :
 * « vérifiez-la » dit déjà quoi faire. `Server` est le fourre-tout, et c'est le seul
 * cas où le message de l'application n'apprend rien de plus que « ça n'a pas marché ».
 */
internal val AiError.diagnostic: String?
    get() = (this as? AiError.Server)?.let { listOfNotNull("HTTP ${it.status}", it.detail).joinToString(" · ") }

/**
 * Le message d'une issue, **jamais son code**.
 *
 * Un `401` n'est pas un message ; « votre clé a été refusée » en est un. La traduction
 * vit ici, dans la couche qui a des chaînes, et non dans le domaine qui n'en a pas
 * ([docs/05][ia] § Erreurs).
 *
 * [ia]: docs/05-ia.md
 */
@get:StringRes
internal val AiError.messageRes: Int
    get() = when (this) {
        AiError.InvalidKey -> R.string.ai_error_invalid_key
        AiError.QuotaExceeded -> R.string.ai_error_quota
        AiError.NoNetwork -> R.string.ai_error_network
        AiError.Timeout -> R.string.ai_error_timeout
        AiError.VisionUnsupported -> R.string.ai_error_vision
        AiError.NoProviderConfigured -> R.string.ai_error_not_configured
        AiError.Unparseable, AiError.NothingRecognized -> R.string.ai_error_unreadable
        is AiError.Server -> R.string.ai_error_server
    }

/** Les gestes de l'écran, rassemblés pour que la composable n'en prenne qu'un. */
data class AiSettingsActions(
    val onOpen: (AiProvider) -> Unit,
    val onKey: (String) -> Unit,
    val onModel: (String) -> Unit,
    val onBaseUrl: (String) -> Unit,
    val onReveal: () -> Unit,
    val onTest: () -> Unit,
    val onSave: () -> Unit,
    val onForget: () -> Unit,
    val onClose: () -> Unit,
)
