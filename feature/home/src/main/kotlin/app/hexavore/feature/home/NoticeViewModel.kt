package app.hexavore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.usecase.ObserveNotices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Ce qui merite une pastille sur l'accueil.
 *
 * **Un modele a part et non trois parametres de plus sur `HomeViewModel`.** Les
 * pastilles ne sont pas le resume de la journee : elles lisent des cles, des pesees et
 * la veille, c'est-a-dire cinq sources dont aucune ne concerne les six compteurs. Les
 * fondre aurait fait relire tout le journal chaque fois qu'une cle change.
 *
 * **Un flux qui echoue ne fait pas tomber l'accueil.** Une pastille absente est une
 * gene ; un ecran qui refuse de s'afficher parce qu'un fichier de preferences est
 * illisible en est une autre, sans commune mesure.
 */
@HiltViewModel
internal class NoticeViewModel @Inject constructor(observeNotices: ObserveNotices) : ViewModel() {
    val notices: StateFlow<Set<Notice>> = observeNotices()
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), emptySet())

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
