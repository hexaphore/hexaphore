package app.hexaphore.domain.goal

import app.hexaphore.domain.nutrition.Macro
import java.time.LocalDate

/** Identifiant d'un objectif. UUIDv4 généré côté application. */
@JvmInline
value class GoalId(val value: String)

/** D'où vient un objectif. Trois provenances, trois comportements de recalcul. */
enum class GoalOrigin {
    /** Calculé depuis le profil et l'échéance. */
    CALCULATED,

    /** Édité à la main, en tout ou partie. */
    MANUAL,

    /** Issu d'une suggestion d'adaptation hebdomadaire, acceptée. */
    ADJUSTMENT,
}

/**
 * Un objectif, **daté**.
 *
 * Un objectif n'est jamais modifié en place : toute modification crée une nouvelle
 * ligne, l'ancienne recevant [endedAt] ([D04][decisions]). Une journée est donc
 * toujours comparée à l'objectif qui était le sien, et non à celui d'aujourd'hui —
 * sans quoi le calendrier se repeindrait entièrement à chaque changement de cap.
 *
 * **Invariant** : au plus un objectif avec `endedAt == null`. Il est tenu par un index
 * unique partiel côté base, pas par une convention.
 *
 * [manualFields] protège le travail de l'utilisateur : un recalcul ne réécrit pas un
 * compteur qu'il a fixé lui-même. Un `Set<Macro>` et non une chaîne, parce que c'est
 * la forme dans laquelle la règle se lit — « les protéines ont été fixées à la main »
 * — et que la sérialisation appartient à l'adaptateur.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/07-modele-de-donnees.md
 */
data class Goal(
    val id: GoalId,
    val startedAt: LocalDate,
    val endedAt: LocalDate? = null,
    val origin: GoalOrigin,
    val strategy: GoalStrategy,
    val targetWeightKg: Double? = null,
    val targetDate: LocalDate? = null,
    val daily: DailyGoal,
    val manualFields: Set<Macro> = emptySet(),
) {
    /** Celui qui court. Il n'y en a qu'un. */
    val active: Boolean get() = endedAt == null

    /**
     * Vaut-il pour cette journée ?
     *
     * Borne de début **incluse**, borne de fin **exclue** : le jour où un objectif est
     * remplacé appartient au nouveau. Sans cette convention, une journée relèverait de
     * deux objectifs, et le résumé du jour dépendrait de l'ordre de lecture.
     */
    fun coversOn(date: LocalDate): Boolean = !date.isBefore(startedAt) && (endedAt == null || date.isBefore(endedAt))

    /**
     * Le même cap que [other] ?
     *
     * L'identifiant, les deux dates de validité et la provenance sont exclus : ce sont
     * des faits sur la **ligne**, pas sur le cap qu'elle décrit. Deux lignes écrites à
     * six mois d'écart peuvent viser exactement la même chose.
     *
     * Cette comparaison existe pour que [D04][decisions] garde son sens. Ouvrir les
     * réglages et appuyer sur « Enregistrer » sans avoir rien changé écrirait sinon une
     * version de plus à chaque visite, et l'historique des changements de cap — la
     * contrepartie qu'on paie en versionnant — cesserait d'en être un.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun sameAimAs(other: Goal): Boolean = strategy == other.strategy &&
        targetWeightKg == other.targetWeightKg &&
        targetDate == other.targetDate &&
        daily == other.daily &&
        manualFields == other.manualFields
}
