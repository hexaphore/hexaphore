package app.hexaphore.core.testing

import app.hexaphore.domain.diary.SelectedDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * Le jour regarde, en memoire.
 *
 * Premiere implementation du port, comme [InMemoryGoals] et [InMemoryWeightLog]. Elle
 * est identique a celle de production, qui ne range rien non plus : le jour regarde
 * n'a pas de stockage, et un faux qui en aurait un serait plus indulgent que le vrai.
 */
class InMemorySelectedDay(initial: LocalDate? = null, private val today: LocalDate? = null) : SelectedDay {
    private val day = MutableStateFlow(initial)

    override fun observe(): Flow<LocalDate?> = day

    override fun current(): LocalDate? = day.value

    /** Aujourd'hui se range comme `null`, comme dans l'implementation reelle. */
    override fun select(date: LocalDate?) {
        day.value = date?.takeIf { it != today }
    }
}
