package app.hexaphore.core.common.diary

import app.hexaphore.domain.diary.SelectedDay
import app.hexaphore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le jour regardé, en mémoire.
 *
 * **Aucun stockage**, et c'est la décision : rouvrir l'application montre aujourd'hui.
 * Retrouver un jour d'octobre parce qu'on l'y avait laissé trois semaines plus tôt
 * ferait noter un repas au mauvais endroit sans qu'aucun écran n'ait menti.
 *
 * Il vit ici, à côté de l'horloge et du générateur d'identifiants, pour la même raison
 * qu'eux : c'est un état d'application qui n'appartient à aucun domaine de données, et
 * un module `:data` sous-entendrait un rangement qui n'existe pas.
 */
@Singleton
class CurrentSelectedDay @Inject constructor(private val clock: Clock) : SelectedDay {
    private val day = MutableStateFlow<LocalDate?>(null)

    override fun observe(): Flow<LocalDate?> = day

    override fun current(): LocalDate? = day.value

    /**
     * **Aujourd'hui se range comme `null`**, et non comme sa date.
     *
     * La regle vit ici plutot que dans l'ecran qui appelle : sans elle, toucher la
     * pastille du jour figerait la date, et un ecran laisse ouvert pendant la nuit
     * continuerait d'afficher la veille. C'est une propriete du jour regarde, pas du
     * geste qui le change.
     */
    override fun select(date: LocalDate?) {
        day.value = date?.takeIf { it != clock.today() }
    }
}
