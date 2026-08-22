package app.hexaphore.core.testing

import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.goal.GoalId
import app.hexaphore.domain.goal.GoalOrigin
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.goal.Goals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * L'historique des objectifs, en mémoire.
 *
 * Comme [InMemoryFoodCatalog], ce n'est pas une béquille : c'est la première
 * implémentation du port, celle contre laquelle les écrans sont écrits avant Room.
 *
 * **[replace] clôt vraiment l'objectif courant**, plutôt que d'empiler des lignes
 * actives. C'est l'invariant du port — au plus un objectif sans date de fin — et un
 * faux qui l'ignorerait laisserait passer un adaptateur qui l'ignore aussi : le
 * résumé d'une journée dépendrait alors de l'ordre de lecture.
 */
class InMemoryGoals(initial: List<Goal> = emptyList(), var failure: Boolean = false) : Goals {
    private val goals = MutableStateFlow(initial)

    /** Ce que l'historique contient, pour qu'un test l'affirme sans passer par un flux. */
    val all: List<Goal> get() = goals.value

    override fun observeGoalOn(date: LocalDate): Flow<Goal?> = goals.map { history ->
        failIfBroken()
        // Le plus recent d'abord : deux objectifs ne peuvent pas couvrir la meme
        // journee, mais si l'invariant etait casse, mieux vaut rendre le dernier
        // que le premier trouve.
        history.filter { it.coversOn(date) }.maxByOrNull { it.startedAt }
    }

    override fun observeCurrent(): Flow<Goal?> = goals.map { history ->
        failIfBroken()
        history.firstOrNull { it.active }
    }

    /** Toutes les versions, la plus recente d'abord -- l'ordre que le vrai rend. */
    override fun observeAll(): Flow<List<Goal>> = goals.map { history ->
        failIfBroken()
        history.sortedByDescending { it.startedAt }
    }

    override suspend fun replace(goal: Goal) {
        failIfBroken()
        goals.value = goals.value.map { if (it.active) it.copy(endedAt = goal.startedAt) else it } + goal
    }

    private fun failIfBroken() {
        if (failure) error("Objectifs illisibles")
    }

    companion object {
        /**
         * Un objectif de maintien, pour les tests qui ont besoin d'en avoir un sans
         * que ses chiffres comptent.
         *
         * Les valeurs sont celles de l'ancien `DailyGoal.Placeholder` — 2 000 kcal en
         * maintien sur un poids de référence de 70 kg — parce qu'elles suivent les
         * règles de `docs/03` et que leur contrôle de cohérence retombe à 1 kcal près.
         * Ce qui a disparu est le fait qu'un **écran** s'en serve ([D30][decisions]).
         *
         * [decisions]: docs/11-decisions.md
         */
        fun maintenance(from: LocalDate): Goal = Goal(
            id = GoalId("goal-maintien"),
            startedAt = from,
            origin = GoalOrigin.CALCULATED,
            strategy = GoalStrategy.MAINTAIN,
            daily = DailyGoal(
                kcal = 2000.0,
                protein = 112.0,
                carbs = 223.0,
                sugars = 50.0,
                fat = 67.0,
                fiber = 28.0,
            ),
        )
    }
}
