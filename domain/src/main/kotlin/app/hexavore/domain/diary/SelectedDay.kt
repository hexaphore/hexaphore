package app.hexavore.domain.diary

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Le jour que l'utilisateur regarde.
 *
 * **`null` veut dire aujourd'hui, et ce n'est pas la même chose que la date du jour.**
 * Une application laissée ouverte pendant la nuit doit basculer sur la journée neuve ;
 * si l'on rangeait ici la date d'aujourd'hui, elle continuerait d'afficher la veille
 * jusqu'à ce que quelqu'un touche une pastille. C'est la même distinction que
 * `HomeViewModel` tenait déjà pour l'argument de route qu'il remplace.
 *
 * **Un port et non un argument de navigation.** Le jour regardé doit atteindre l'écran
 * de validation, qui n'est ouvert ni par l'accueil ni directement : entre les deux il y
 * a la recherche, le scan, ou une modale d'IA. Le faire voyager demanderait de le
 * déclarer sur quatre routes, dont aucune ne s'en sert. C'est le choix qui avait déjà
 * été fait pour la proposition d'un modèle — elle attend dans un dépôt plutôt que de
 * traverser le graphe ([D80][decisions]).
 *
 * **Rien ne le persiste**, et c'est voulu : rouvrir l'application montre aujourd'hui.
 * Retrouver un jour d'octobre parce qu'on l'y avait laissé trois semaines plus tôt
 * ferait noter un repas au mauvais endroit sans qu'aucun écran n'ait menti.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/02-parcours-et-ecrans.md
 */
interface SelectedDay {
    fun observe(): Flow<LocalDate?>

    /**
     * Le jour regardé, **sans suspendre**.
     *
     * Un brouillon se fabrique dans la foulée d'un geste, sans point de suspension à
     * offrir. La valeur est de toute façon locale et immédiate — il n'y a rien à lire
     * sur un disque.
     */
    fun current(): LocalDate?

    /** `null` pour revenir à aujourd'hui. */
    fun select(date: LocalDate?)
}
