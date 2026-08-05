package app.hexaphore.core.common.time

import app.hexaphore.domain.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * L'implémentation qui lit réellement l'horloge de l'appareil.
 *
 * C'est le **seul** fichier du projet autorisé à le faire, et la règle detekt
 * `TimeOutsideClock` le vérifie à partir de son nom. Déplacer ou renommer ce
 * fichier suppose de mettre à jour `allowedFileNames` dans
 * `config/detekt/detekt.yml` : la liste est courte à dessein, pour que l'ajout
 * d'une entrée soit une décision visible en revue.
 */
class SystemClock @Inject constructor() : Clock {
    override fun now(): Instant = Instant.now()

    override fun today(): LocalDate = LocalDate.now(zone())

    /**
     * Fuseau courant du système, relu à chaque appel.
     *
     * Le mettre en cache ferait qu'un utilisateur qui change de fuseau en voyage
     * continuerait à classer ses repas selon son fuseau de départ, jusqu'au
     * prochain redémarrage du processus.
     */
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
