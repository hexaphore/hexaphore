package app.hexaphore.domain.goal

import java.time.LocalDate

/**
 * Les échéances que l'application propose, en mois.
 *
 * **Trois pastilles plutôt qu'un calendrier.** Une date d'échéance exacte n'a aucune
 * valeur en soi : ce qui compte est le rythme, et « six mois » l'exprime aussi bien
 * qu'un 14 février choisi au hasard. La « date libre » que [docs/02][parcours]
 * prévoyait a disparu en [D56][decisions] ; ce qui la remplace est la **date
 * atteignable** que [GoalSafetyPolicy] calcule, proposée en pastille supplémentaire
 * quand l'échéance demandée est intenable.
 *
 * Ici et non dans un module d'écran, parce que **deux écrans les proposent** — les cinq
 * questions et les réglages profil. Deux listes d'horizons finiraient par ne plus dire
 * la même chose, et c'est le genre d'écart qu'aucun test ne signale : les deux écrans
 * seraient verts, simplement pas d'accord.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
enum class GoalHorizon(val months: Long) {
    THREE_MONTHS(THREE),
    SIX_MONTHS(SIX),
    TWELVE_MONTHS(TWELVE),
    ;

    fun dateFrom(today: LocalDate): LocalDate = today.plusMonths(months)

    companion object {
        /** L'horizon que porte une date, s'il correspond exactement à l'une des trois. */
        fun of(today: LocalDate, date: LocalDate?): GoalHorizon? =
            date?.let { chosen -> entries.firstOrNull { it.dateFrom(today) == chosen } }
    }
}

// Trois, six, douze : le trimestre, le semestre et l'annee. Ecrits ici plutot que
// poses dans le constructeur, parce que ce sont les seules valeurs de ce fichier
// qu'un changement d'avis ferait bouger.
private const val THREE = 3L
private const val SIX = 6L
private const val TWELVE = 12L
