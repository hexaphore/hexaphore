package app.hexavore.feature.settings

import androidx.annotation.StringRes
import app.hexavore.domain.ai.AiProvider

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
    /**
     * Ce que les analyses ont consommé, regroupé par fournisseur.
     *
     * En bas de l'écran, comme [docs/02][parcours] le prévoit, et **sous** la liste
     * des clés : on vient ici pour brancher un fournisseur, on repart en sachant ce
     * qu'il a coûté.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    val usage: List<UsageRow> = emptyList(),
)

/**
 * Une ligne de compteur : un modèle, ce qu'il a consommé, ce que ça a coûté.
 *
 * [cost] est `null` quand le modèle n'a pas de tarif connu — un modèle saisi à la
 * main, ou n'importe lequel derrière le fournisseur « compatible ». L'écran affiche
 * alors les jetons seuls, plutôt qu'un montant inventé.
 */
data class UsageRow(val provider: AiProvider, val model: String, val calls: Int, val tokens: Int, val cost: Double?)

/** Une ligne de la liste : qui, et où il en est. */
data class ProviderRow(
    val provider: AiProvider,
    val configured: Boolean,
    val active: Boolean,
    /**
     * `true` quand ce fournisseur est écrit mais pas encore éprouvé sur un vrai compte.
     *
     * Sa carte s'affiche quand même, en retrait et sans formulaire : le cacher
     * laisserait croire qu'il n'existe pas, et le proposer ferait payer à quelqu'un la
     * découverte d'un défaut que personne n'a cherché.
     */
    val suspended: Boolean = false,
)

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
