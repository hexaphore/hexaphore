package app.hexavore.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Accès au temps présent.
 *
 * Tout le code de l'application lit l'heure ici, jamais par `LocalDate.now()` ni
 * `System.currentTimeMillis()`. La raison n'est pas la pureté : c'est que la moitié
 * de cette application est une fonction du temps. Une entrée saisie à 23 h 59
 * appartient à un jour précis, y compris au changement d'heure ; une moyenne mobile
 * porte sur sept jours ; une suggestion d'ajustement compare deux fenêtres espacées
 * de quatorze jours. Sans horloge injectée, ces règles ne se testent qu'en attendant
 * qu'il soit minuit.
 *
 * C'est aussi l'une des quatre décisions que le plan de développement désigne comme
 * non rattrapables : l'introduire après coup, c'est rouvrir chaque fichier.
 *
 * Les implémentations sont interchangeables sans que l'appelant s'en aperçoive :
 * une horloge figée dans un test se comporte comme l'horloge du système, elle
 * répond simplement toujours la même chose.
 *
 * @see docs/06-architecture.md
 */
interface Clock {
    /** Instant courant, en UTC. Base de tout horodatage stocké. */
    fun now(): Instant

    /**
     * Jour courant dans le fuseau de l'utilisateur.
     *
     * Distinct de [now] à dessein : un journal alimentaire raisonne en journées
     * locales, pas en instants. Dériver l'un de l'autre au cas par cas est
     * exactement la façon dont une entrée de 23 h 59 finit dans le mauvais jour.
     */
    fun today(): LocalDate

    /**
     * Fuseau de référence pour convertir un instant en journée.
     *
     * Exposé plutôt que supposé : un utilisateur qui traverse un fuseau ne doit pas
     * voir son journal se réorganiser, et un test doit pouvoir jouer le passage à
     * l'heure d'hiver sans changer les réglages de la machine.
     */
    fun zone(): ZoneId
}
