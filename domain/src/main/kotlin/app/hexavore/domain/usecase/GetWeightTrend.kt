package app.hexavore.domain.usecase

import app.hexavore.domain.goal.Goals
import app.hexavore.domain.profile.WeightAim
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.profile.declaredAim
import app.hexavore.domain.profile.movingAverageOn
import app.hexavore.domain.profile.weeklySlopeOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * La courbe de poids : les points bruts, leur lissage, et le cap annoncé.
 *
 * **Deux tracés et non un** ([docs/02][parcours]) : le poids brut varie de deux kilos
 * avec l'hydratation, le sel et le transit, et décourage sans raison. Il reste visible
 * — c'est la mesure, et la cacher reviendrait à décider à la place de l'utilisateur ce
 * qu'il a le droit de voir — mais discret, sous une moyenne mobile qui, elle, porte
 * l'information.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
class GetWeightTrend(private val weights: WeightLog, private val goals: Goals) {
    operator fun invoke(): Flow<WeightTrend> = combine(weights.observeHistory(), goals.observeCurrent()) { log, goal ->
        WeightTrend(
            points = log.map { TrendPoint(it.date, it.weightKg, log.movingAverageOn(it.date)) },
            aim = goal?.declaredAim(log),
        )
    }
}

/**
 * Un point de la courbe : une pesée, et le lissage au même jour.
 *
 * [averageKg] manque tant que la fenêtre de sept jours ne porte pas assez de pesées.
 * C'est un trou dans le tracé lissé, pas un zéro et pas une interpolation : relier deux
 * moyennes séparées par trois semaines de silence dessinerait une progression que
 * personne n'a mesurée.
 */
data class TrendPoint(val date: LocalDate, val weightKg: Double, val averageKg: Double?)

/**
 * La courbe entière, telle que l'écran la dessine.
 *
 * [points] est vide avant la première pesée — pas d'axe, pas de tracé, un texte qui
 * invite à se peser.
 */
data class WeightTrend(val points: List<TrendPoint>, val aim: WeightAim? = null) {
    /** La dernière pesée connue, celle que l'écran affiche en grand. */
    val latest: WeightEntry?
        get() = points.lastOrNull()?.let { WeightEntry(it.date, it.weightKg) }

    /**
     * La pente réelle au jour dit, en kilogrammes par semaine.
     *
     * `null` tant que les deux fenêtres ne sont pas assez fournies — c'est le silence
     * que [docs/03][calculs] préfère à un conseil fondé sur une seule pesée.
     *
     * [calculs]: docs/03-nutrition-calculs.md
     */
    fun weeklySlopeOn(day: LocalDate): Double? = points.map { WeightEntry(it.date, it.weightKg) }.weeklySlopeOn(day)
}
