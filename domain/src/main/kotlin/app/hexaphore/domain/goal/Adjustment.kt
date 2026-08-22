package app.hexaphore.domain.goal

import app.hexaphore.domain.profile.TREND_WINDOW_DAYS
import app.hexaphore.domain.profile.WeightEntry
import app.hexaphore.domain.profile.weeklySlopeOn
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * L'écart hebdomadaire au-delà duquel un objectif mérite d'être corrigé.
 *
 * En deçà, l'écart est du bruit : le poids brut varie de deux kilos avec l'hydratation,
 * et même lissé sur sept jours, cent grammes par semaine ne se distinguent pas de rien.
 */
const val SIGNIFICANT_GAP_KG_PER_WEEK = 0.15

/**
 * La correction maximale d'un seul ajustement, en kcal par jour.
 *
 * Plus brutal, le système surcorrige : l'utilisateur voit son objectif osciller et perd
 * confiance ([docs/03][calculs]).
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
const val MAX_ADJUSTMENT_KCAL = 150.0

/** La fenêtre sur laquelle l'adhérence se mesure. */
const val ADHERENCE_WINDOW_DAYS = 14

/**
 * Le nombre de jours notés qu'il faut dans cette fenêtre — 70 %.
 *
 * **Un objectif ne se corrige pas sur la base d'un journal troué.** On ne saurait pas
 * si l'écart vient du métabolisme ou de la saisie, et corriger le premier pour un
 * défaut du second éloigne du but au lieu d'en rapprocher.
 */
const val MIN_DAYS_WITH_ENTRY = 10

/** Le silence après une réponse — acceptée ou ignorée. Deux semaines. */
const val QUIET_DAYS_AFTER_RESPONSE = 14

private const val DAYS_PER_WEEK = 7.0

/**
 * L'écart **persistant** entre le rythme annoncé et le rythme réel, en kg par semaine.
 *
 * Positif quand on va moins vite qu'annoncé — la convention de [docs/03][calculs] :
 * `écart = pente visée − pente réelle`.
 *
 * **Deux semaines consécutives, et dans le même sens.** Une seule semaine ne prouve
 * rien : la moyenne mobile atténue le bruit, elle ne l'efface pas. Et deux écarts de
 * signes opposés ne sont pas une persistance mais une oscillation, que corriger
 * amplifierait.
 *
 * `null` dès qu'une des deux pentes manque — sous trois pesées par fenêtre, il n'y en a
 * pas — ou dès que l'écart n'est pas significatif deux fois de suite.
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
fun List<WeightEntry>.persistentGap(aimedWeeklyKg: Double, on: LocalDate): Double? {
    val now = gapOn(aimedWeeklyKg, on)
    val before = gapOn(aimedWeeklyKg, on.minusDays(TREND_WINDOW_DAYS.toLong()))
    return now?.takeIf { before != null && it.persistsFrom(before) }
}

private fun List<WeightEntry>.gapOn(aimed: Double, on: LocalDate) = weeklySlopeOn(on)?.let { aimed - it }

private fun Double.persistsFrom(before: Double) = significant() && before.significant() && sameSignAs(before)

private fun Double.significant(): Boolean = abs(this) > SIGNIFICANT_GAP_KG_PER_WEEK

/** Deux écarts de signes opposés sont une oscillation, pas une persistance. */
private fun Double.sameSignAs(other: Double): Boolean = (this > 0) == (other > 0)

/**
 * `true` si au moins [MIN_DAYS_WITH_ENTRY] des [ADHERENCE_WINDOW_DAYS] derniers jours
 * portent une saisie.
 *
 * Les jours **notés** et non les plats : trois plats le même jour ne font qu'un jour
 * d'adhérence, sans quoi une seule journée bien remplie tiendrait lieu de quinzaine.
 */
fun Set<LocalDate>.adherentOn(day: LocalDate): Boolean {
    val floor = day.minusDays(ADHERENCE_WINDOW_DAYS - 1L)
    return count { !it.isBefore(floor) && !it.isAfter(day) } >= MIN_DAYS_WITH_ENTRY
}

/**
 * La correction que cet écart appelle, en kcal par jour, **bornée**.
 *
 * `Δkcal = écart_kg_sem × 7700 / 7`, puis ramenée dans ±[MAX_ADJUSTMENT_KCAL].
 */
fun correctionKcal(gapKgPerWeek: Double): Double =
    (gapKgPerWeek * KCAL_PER_KILOGRAM / DAYS_PER_WEEK).coerceIn(-MAX_ADJUSTMENT_KCAL, MAX_ADJUSTMENT_KCAL)

/**
 * Une correction proposée, **jamais appliquée toute seule**.
 *
 * [proposed] est l'objectif complet tel qu'il serait écrit : la correction y est déjà
 * passée par les garde-fous, et les six chiffres sont redistribués. C'est la règle de
 * [ReviseGoal][revise] — ce qui est montré est ce qui est écrit — et elle compte
 * doublement ici : la carte annonce un nombre de kilocalories, et accepter doit donner
 * exactement celui-là. Un garde-fou qui mordrait après coup ferait mentir la carte.
 *
 * [revise]: app.hexaphore.domain.usecase.ReviseGoal
 */
data class AdjustmentSuggestion(
    val actualWeeklyKg: Double,
    val aimedWeeklyKg: Double,
    val current: DailyGoal,
    val proposed: DailyGoal,
) {
    /**
     * Ce que la carte annonce : la correction **retenue**, garde-fous compris.
     *
     * Elle peut donc être plus petite que ce que l'écart demandait, et c'est voulu :
     * un plancher calorique ou un plafond de vitesse a le dernier mot.
     */
    val deltaKcal: Int get() = (proposed.kcal - current.kcal).roundToInt()
}
