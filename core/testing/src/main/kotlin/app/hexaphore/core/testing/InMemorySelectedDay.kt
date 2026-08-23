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
 *
 * **[today] n'a pas de valeur par defaut**, et c'est delibere : avec un `null`, un
 * appelant qui ne s'en souciait pas rangeait la date d'aujourd'hui la ou le vrai range
 * `null`. C'est exactement la forme de defaut que [D53][decisions] decrit -- un faux
 * plus indulgent que le vrai -- et un parametre obligatoire la rend impossible.
 *
 * Les deux implementations sont eprouvees ensemble par `SelectedDayContract`.
 *
 * [decisions]: docs/11-decisions.md
 */
class InMemorySelectedDay(private val today: LocalDate, initial: LocalDate? = null) : SelectedDay {
    private val day = MutableStateFlow(initial)

    override fun observe(): Flow<LocalDate?> = day

    override fun current(): LocalDate? = day.value

    /** Aujourd'hui se range comme `null`, comme dans l'implementation reelle. */
    override fun select(date: LocalDate?) {
        day.value = date?.takeIf { it != today }
    }
}
