package app.hexaphore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.CalendarDay
import app.hexaphore.domain.usecase.GetCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Ce que le bandeau et la vue mensuelle montrent.
 *
 * **Un seul modèle pour les deux**, parce qu'ils posent la même question à une plage
 * près : quels jours ont reçu quelque chose, et combien. Deux `ViewModel` auraient
 * fait deux lectures de la même table et deux occasions de traiter différemment une
 * journée vide — or c'est justement la distinction que la tranche existe pour tenir.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class CalendarViewModel @Inject constructor(private val getCalendar: GetCalendar, private val clock: Clock) :
    ViewModel() {
    /**
     * Le mois affiché par la vue mensuelle.
     *
     * Le bandeau, lui, ne s'en sert pas : il montre toujours les sept derniers jours
     * jusqu'à aujourd'hui. Les faire dépendre l'un de l'autre ferait bouger le bandeau
     * quand on feuillette les mois, alors qu'il sert de repère fixe.
     */
    private val month = MutableStateFlow(YearMonth.from(clock.today()))

    val uiState: StateFlow<CalendarUiState> = month
        .flatMapLatest { shown ->
            val today = clock.today()
            val from = minOf(shown.atDay(1), today.minusDays(STRIP_DAYS - 1))
            val to = maxOf(shown.atEndOfMonth(), today)

            combine(getCalendar(from, to), MutableStateFlow(shown)) { days, current ->
                CalendarUiState(
                    today = today,
                    month = current,
                    // Indexes par date : l'ecran demande « ce jour-la », et une
                    // recherche lineaire sur trente jours par pastille ferait neuf
                    // cents comparaisons pour un affichage.
                    days = days.associateBy(CalendarDay::date),
                )
            }
        }
        // Une lecture qui echoue ne doit pas emporter l'accueil avec elle : le
        // bandeau se dessine alors sans anneau, ce qui est exactement ce qu'il fait
        // pour une journee sans saisie.
        .catch { emit(CalendarUiState(today = clock.today(), month = month.value)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = CalendarUiState(today = clock.today(), month = month.value),
        )

    fun onMonthChange(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    internal companion object {
        /** Sept jours, aujourd'hui compris : une semaine glissante et non calendaire. */
        const val STRIP_DAYS = 7L

        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * L'état du calendrier.
 *
 * [days] ne contient **que** les jours qui ont reçu quelque chose. Un jour absent est
 * un jour sans saisie, et la pastille le dessine neutre — jamais comme une journée à
 * zéro, ce qu'un `getOrDefault(MacroTotals.Empty)` ferait sans qu'on s'en aperçoive.
 */
internal data class CalendarUiState(
    val today: LocalDate,
    val month: YearMonth,
    val days: Map<LocalDate, CalendarDay> = emptyMap(),
) {
    /** Les sept derniers jours, du plus ancien à aujourd'hui. */
    val strip: List<LocalDate>
        get() = (CalendarViewModel.STRIP_DAYS - 1 downTo 0).map { today.minusDays(it) }
}
