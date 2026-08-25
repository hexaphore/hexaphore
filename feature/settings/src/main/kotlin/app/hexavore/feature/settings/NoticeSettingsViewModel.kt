package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.notice.NoticeSettings
import app.hexavore.domain.usecase.ObserveNotices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Les quatre interrupteurs, et ce qu'ils allument en ce moment.
 *
 * **Les deux ensemble et non l'un sans l'autre.** Un interrupteur qui dit seulement
 * « allumee » laisse celui qui l'a mise en route se demander si elle sert : l'ecran
 * montre aussi laquelle est **active maintenant**, c'est-a-dire ce qu'il se passerait
 * si l'on retournait a l'accueil. C'est ce qui permet de comprendre ce qu'une pastille
 * surveille sans lire de documentation.
 */
@HiltViewModel
internal class NoticeSettingsViewModel @Inject constructor(
    private val settings: NoticeSettings,
    observeNotices: ObserveNotices,
) : ViewModel() {
    val uiState: StateFlow<NoticeUiState> = combine(settings.observe(), observeNotices()) { allumees, actives ->
        NoticeUiState(enabled = allumees, active = actives)
    }
        .catch { emit(NoticeUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), NoticeUiState())

    fun onToggle(notice: Notice, enabled: Boolean) {
        viewModelScope.launch { settings.setEnabled(notice, enabled) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Ce que l'ecran des notifications montre.
 *
 * [active] est **contenu dans** [enabled] par construction : une pastille eteinte n'est
 * jamais active, puisque le cas d'usage filtre avant d'evaluer. L'ecran s'en sert pour
 * dire « en ce moment » a cote de l'interrupteur.
 */
internal data class NoticeUiState(
    val enabled: Set<Notice> = Notice.entries.toSet(),
    val active: Set<Notice> = emptySet(),
)
