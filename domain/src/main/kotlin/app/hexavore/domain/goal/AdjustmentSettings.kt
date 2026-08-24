package app.hexavore.domain.goal

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Ce que l'utilisateur a répondu aux suggestions, et depuis quand.
 *
 * Trois faits que rien d'autre ne porte : l'adaptation est-elle encore la bienvenue,
 * quand un ajustement a-t-il été accepté, quand une suggestion a-t-elle été écartée.
 * Les déduire du journal des objectifs ne marcherait pas — un refus n'écrit rien, et
 * c'est précisément ce qu'il faut se rappeler.
 *
 * @see docs/03-nutrition-calculs.md
 */
interface AdjustmentSettings {
    /**
     * L'état courant, et ses changements.
     *
     * Un flux et non une lecture unique : la carte disparaît **au moment** où l'on
     * répond, sur les deux écrans qui la portent, sans que ni l'un ni l'autre ait à
     * relire.
     */
    fun observe(): Flow<AdjustmentSetup>

    /** Un ajustement vient d'être accepté. */
    suspend fun accepted(on: LocalDate)

    /** Une suggestion vient d'être écartée. Elle reviendra dans deux semaines. */
    suspend fun ignored(on: LocalDate)

    /** « Ne plus proposer ». Définitif jusqu'à ce que l'utilisateur revienne dessus. */
    suspend fun stop()

    /**
     * Repose l'état entier, tel qu'une sauvegarde le portait.
     *
     * **Il manquait, et l'instantané l'emportait pour rien.** `Snapshot` capture cet
     * état depuis toujours et l'écrit dans le fichier ; la restauration le laissait
     * tomber, faute de savoir où le remettre. Quelqu'un qui restaurait retrouvait donc
     * ses repas et ses objectifs, mais pas son « ne plus proposer » — et la carte
     * revenait le lendemain sans que rien ne l'explique.
     *
     * Un état entier et non trois appels : `accepted`, `ignored` et `stop` décrivent
     * des **réponses**, et rejouer une réponse qui n'a pas lieu n'est pas la même chose
     * que reposer ce qu'elle avait produit.
     */
    suspend fun restore(setup: AdjustmentSetup)
}

/**
 * L'état de l'adaptation hebdomadaire.
 *
 * [enabled] est **vrai par défaut** : l'adaptation est ce qui fait qu'un objectif reste
 * juste au bout de trois mois, et la désactiver d'office la réserverait à ceux qui
 * seraient allés la chercher.
 */
data class AdjustmentSetup(
    val enabled: Boolean = true,
    val lastAcceptedOn: LocalDate? = null,
    val lastIgnoredOn: LocalDate? = null,
) {
    /**
     * `true` quand une suggestion a le droit de paraître ce jour-là.
     *
     * **Accepter et ignorer imposent le même silence**, et pour deux raisons
     * différentes. Après un ajustement accepté, il faut deux semaines pour que la
     * moyenne mobile reflète le nouvel objectif — corriger avant reviendrait à corriger
     * une correction qu'on n'a pas encore mesurée. Après un refus, reproposer le
     * lendemain la même chose transformerait la carte en insistance.
     */
    fun openOn(day: LocalDate): Boolean = enabled && quietSince(lastAcceptedOn, day) && quietSince(lastIgnoredOn, day)

    private fun quietSince(response: LocalDate?, day: LocalDate): Boolean =
        response == null || ChronoUnit.DAYS.between(response, day) >= QUIET_DAYS_AFTER_RESPONSE
}
