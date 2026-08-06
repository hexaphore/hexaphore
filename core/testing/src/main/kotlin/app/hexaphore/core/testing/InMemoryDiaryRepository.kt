package app.hexaphore.core.testing

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.diary.EntryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Le journal, en mémoire.
 *
 * Première implémentation de [DiaryRepository], pas une béquille : l'accueil est
 * écrit contre elle avant que Room n'existe, ce qui confronte les contrats du
 * domaine à un écran réel pendant qu'ils sont encore faciles à corriger. Room la
 * remplace en changeant une ligne du module Hilt, et aucun appelant ne s'en aperçoit
 * — c'est précisément la propriété que cette classe sert à démontrer.
 *
 * Elle survit à ce remplacement : les tests d'écran continuent de s'en servir.
 *
 * @see docs/12-plan-de-developpement.md
 */
class InMemoryDiaryRepository(initial: List<Dish> = emptyList()) : DiaryRepository {
    private val state = MutableStateFlow(initial)

    /** Le contenu courant, pour qu'un test vérifie ce qui a été écrit. */
    val dishes: List<Dish> get() = state.value

    /**
     * L'échec que la prochaine opération doit produire, ou `null` pour fonctionner.
     *
     * Room peut échouer — disque plein, base illisible — et un écran qui ne sait
     * pas le dire affiche une journée vide à la place. Ce champ existe pour que ce
     * cas soit **testable** : sans lui, la seule façon de l'éprouver serait de
     * corrompre une vraie base.
     *
     * Il est lu à chaque appel et à chaque abonnement, et non une fois pour toutes,
     * pour qu'un test puisse rétablir la lecture et vérifier qu'une nouvelle
     * tentative aboutit.
     */
    var failure: Throwable? = null

    override fun observeDay(date: LocalDate): Flow<List<Dish>> {
        failure?.let { cause -> return flow { throw cause } }
        return state.asStateFlow().map { dishes ->
            dishes
                .filter { it.date == date }
                .sortedBy { it.loggedAt }
        }
    }

    override suspend fun dish(id: DishId): Dish? {
        failure?.let { throw it }
        return state.value.firstOrNull { it.id == id }
    }

    override suspend fun save(dish: Dish) {
        failure?.let { throw it }
        state.update { dishes -> dishes.filterNot { it.id == dish.id } + dish }
    }

    override suspend fun deleteEntry(id: EntryId) {
        failure?.let { throw it }
        state.update { dishes ->
            dishes.map { dish -> dish.copy(entries = dish.entries.filterNot { it.id == id }) }
        }
    }

    override suspend fun deleteDish(id: DishId) {
        failure?.let { throw it }
        state.update { dishes -> dishes.filterNot { it.id == id } }
    }

    /** Remplace tout le contenu. Les observateurs reçoivent immédiatement la suite. */
    fun setContent(dishes: List<Dish>) {
        state.value = dishes
    }
}
