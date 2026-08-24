package app.hexavore.core.testing

import app.hexavore.domain.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Une horloge qu'on positionne où l'on veut.
 *
 * C'est elle qui rend testables les règles dont dépend la moitié de l'application :
 * la frontière de minuit, les fenêtres de sept jours, les moyennes mobiles. Sans
 * elle, ces règles ne se vérifieraient qu'en attendant l'heure qu'elles décrivent.
 *
 * [instant] est modifiable pour qu'un test puisse faire passer minuit sans
 * reconstruire tout son décor.
 */
class FixedClock(var instant: Instant, private val zone: ZoneId = ZoneId.of("Europe/Paris")) : Clock {
    override fun now(): Instant = instant

    override fun today(): LocalDate = instant.atZone(zone).toLocalDate()

    override fun zone(): ZoneId = zone

    companion object {
        /**
         * Une horloge calée sur une date locale, à midi.
         *
         * Midi et non minuit : une horloge posée sur une frontière de journée
         * transforme le moindre décalage de fuseau en test qui passe un jour sur
         * deux. Les tests de frontière posent leur instant eux-mêmes, exprès.
         */
        fun atNoon(date: LocalDate, zone: ZoneId = ZoneId.of("Europe/Paris")): FixedClock =
            FixedClock(date.atTime(NOON_HOUR, 0).atZone(zone).toInstant(), zone)

        private const val NOON_HOUR = 12
    }
}
