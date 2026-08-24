package app.hexavore.domain.goal

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Les objectifs, et leur histoire.
 *
 * **Aucune méthode de mise à jour**, et c'est délibéré : [replace] est le seul chemin
 * d'écriture, et il clôt l'ancien objectif en même temps qu'il en ouvre un nouveau.
 * Un `update(goal)` dans ce contrat aurait été l'endroit exact par lequel [D04][decisions]
 * se serait perdue — « c'est plus simple » — et le calendrier se serait repeint.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/03-nutrition-calculs.md
 */
interface Goals {
    /**
     * L'objectif qui vaut pour cette journée-là.
     *
     * `null` tant qu'aucun objectif n'a été posé — avant l'onboarding — et `null`
     * aussi pour une journée antérieure au premier objectif. Ce n'est pas une erreur :
     * une journée notée avant qu'un objectif existe n'a rien à quoi se comparer, et
     * lui appliquer l'objectif d'aujourd'hui serait la juger sur une règle qu'elle
     * n'avait pas.
     */
    fun observeGoalOn(date: LocalDate): Flow<Goal?>

    /** L'objectif courant, celui dont la date de fin est nulle. */
    fun observeCurrent(): Flow<Goal?>

    /**
     * Toutes les versions, la plus recente d'abord.
     *
     * **Pour lire une plage de jours d'un coup.** Un calendrier de trente jours
     * appellerait sinon [observeGoalOn] trente fois, c'est-a-dire trente flux et
     * trente requetes pour choisir parmi quelques versions. La liste est courte par
     * nature : un objectif se revise, il ne se cree pas tous les jours.
     */
    fun observeAll(): Flow<List<Goal>>

    /**
     * Clôt l'objectif courant à la date de début du nouveau, et écrit celui-ci.
     *
     * Les deux écritures sont une seule opération : entre les deux, il y aurait soit
     * deux objectifs actifs, soit aucun.
     */
    suspend fun replace(goal: Goal)
}

/**
 * L'objectif qui valait ce jour-la, parmi ces versions.
 *
 * La regle est celle de [Goal.coversOn] -- debut inclus, fin exclue -- et elle vit
 * ici plutot que d'etre recopiee : deux ecritures de la meme convention divergent le
 * jour ou l'une apprend quelque chose que l'autre ignore.
 *
 * `null` pour une journee anterieure au premier objectif. Ce n'est pas une erreur :
 * une telle journee n'a rien a quoi se comparer.
 */
fun List<Goal>.activeOn(date: LocalDate): Goal? = firstOrNull { it.coversOn(date) }
