package app.hexavore.domain.goal

import java.time.LocalDate

/** Identifiant d'un objectif. UUIDv4 généré côté application. */
@JvmInline
value class GoalId(val value: String)

/**
 * D'où viennent les six chiffres d'un objectif.
 *
 * **C'est un mode, pas une nuance** ([D60][decisions]). Un objectif est calculé depuis
 * le profil, le poids visé et l'échéance, ou bien il est saisi à la main — et alors
 * plus rien ne le recalcule. L'entre-deux, où certains compteurs suivaient le calcul et
 * d'autres non, a existé le temps d'une décision : il obligeait l'écran à expliquer un
 * troisième état, et le poids cible à piloter trois chiffres sur six.
 *
 * [decisions]: docs/11-decisions.md
 */
enum class GoalOrigin {
    /** Calculé depuis le profil et l'échéance. Un changement de profil le déplace. */
    CALCULATED,

    /** Saisi à la main. Aucun recalcul n'y touche, quoi qui change ailleurs. */
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
 * [origin] dit **d'où viennent les six chiffres**, et c'est ce qui décide si un recalcul
 * a le droit d'y toucher. C'est la seule marque : un objectif est calculé ou il est
 * manuel, il n'y a pas de compteur verrouillé à l'intérieur d'un objectif calculé
 * ([D60][decisions]).
 *
 * [targetWeightKg] et [targetDate] restent renseignés en mode manuel, mais **ils n'y
 * pilotent plus rien** : ils décrivent le cap annoncé, dont le journal de poids tire sa
 * trajectoire, et non l'origine des six chiffres.
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
     * Seuls l'identifiant et les deux dates de validité sont exclus : ce sont des faits
     * sur la **ligne**, pas sur le cap qu'elle décrit. Deux lignes écrites à six mois
     * d'écart peuvent viser exactement la même chose.
     *
     * [origin], lui, **compte** : deux objectifs qui portent les mêmes six chiffres ne
     * disent pas la même chose selon qu'ils sont calculés ou saisis à la main. Le
     * premier suivra la prochaine correction de profil, le second non — c'est un
     * changement de cap même quand aucun chiffre ne bouge ([D60][decisions]).
     *
     * Cette comparaison existe pour que [D04][decisions] garde son sens. Ouvrir les
     * réglages et appuyer sur « Enregistrer » sans avoir rien changé écrirait sinon une
     * version de plus à chaque visite, et l'historique des changements de cap — la
     * contrepartie qu'on paie en versionnant — cesserait d'en être un.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun sameAimAs(other: Goal): Boolean = origin == other.origin &&
        strategy == other.strategy &&
        targetWeightKg == other.targetWeightKg &&
        targetDate == other.targetDate &&
        daily == other.daily
}
