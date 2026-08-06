package app.hexaphore.core.testing

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.diary.Dish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
 * Elle survivra à ce remplacement : les tests d'écran continueront de s'en servir.
 *
 * @see docs/12-plan-de-developpement.md
 */
class InMemoryDiaryRepository(initial: List<Dish> = emptyList()) : DiaryRepository {
    private val state = MutableStateFlow(initial)

    /**
     * L'échec que la prochaine lecture doit produire, ou `null` pour lire normalement.
     *
     * Room peut échouer — disque plein, base illisible — et un écran qui ne sait
     * pas le dire affiche une journée vide à la place. Ce champ existe pour que ce
     * cas soit **testable** : sans lui, la seule façon de l'éprouver serait de
     * corrompre une vraie base.
     *
     * Il est lu à chaque abonnement et non une fois pour toutes, pour qu'un test
     * puisse rétablir la lecture et vérifier qu'une nouvelle tentative aboutit.
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

    /** Remplace tout le contenu. Les observateurs reçoivent immédiatement la suite. */
    fun setContent(dishes: List<Dish>) {
        state.value = dishes
    }
}
