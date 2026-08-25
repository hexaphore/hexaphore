package app.hexavore.core.testing

import app.hexavore.domain.notice.KeyRejection
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.notice.NoticeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Les pastilles allumees, en memoire.
 *
 * **Toutes par defaut**, comme le vrai : un faux qui partirait de l'ensemble vide
 * ferait passer des cas ou aucune pastille n'apparait, pour la mauvaise raison.
 */
class InMemoryNoticeSettings(initial: Set<Notice> = Notice.entries.toSet()) : NoticeSettings {
    private val enabled = MutableStateFlow(initial)

    override fun observe(): Flow<Set<Notice>> = enabled

    override suspend fun setEnabled(notice: Notice, enabled: Boolean) {
        this.enabled.value = if (enabled) this.enabled.value + notice else this.enabled.value - notice
    }
}

/** Le souvenir d'une cle refusee, en memoire. */
class InMemoryKeyRejection(initial: Boolean = false) : KeyRejection {
    private val rejected = MutableStateFlow(initial)

    /** L'etat courant, pour qu'un cas l'affirme sans passer par un flux. */
    val noted: Boolean get() = rejected.value

    override fun observe(): Flow<Boolean> = rejected

    override suspend fun note() {
        rejected.value = true
    }

    override suspend fun clear() {
        rejected.value = false
    }
}
