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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/**
 * Ce que le calendrier montre, et **seulement pour ce qu'on regarde**.
 *
 * **La plage lue suit l'affichage.** Charger douze mois d'un coup lirait des milliers
 * de lignes pour en montrer trente : la lecture porte sur le mois visible et ses deux
 * voisins, et se refait quand on feuillette. `flatMapLatest` abandonne la précédente,
 * donc un défilement rapide ne laisse pas dix lectures en vol.
 *
 * Un seul modèle pour la semaine et le mois : ils posent la même question à une plage
 * près, et deux modèles auraient fait deux lectures de la même table et deux occasions
 * de traiter différemment une journée vide.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class CalendarViewModel @Inject constructor(getCalendar: GetCalendar, private val clock: Clock) :
    ViewModel() {
    /**
     * Le mois autour duquel on lit.
     *
     * Poussé par l'écran quand le calendrier déplié change de mois visible. Le bandeau
     * ne le touche pas : il montre la semaine en cours, qui appartient au mois
     * d'aujourd'hui.
     */
    private val anchor = MutableStateFlow(YearMonth.from(clock.today()))

    // Pas de `distinctUntilChanged` : un `StateFlow` ne re-emet deja pas une valeur
    // egale a la precedente, et l'operateur y serait sans effet.
    val uiState: StateFlow<CalendarUiState> = anchor
        .flatMapLatest { shown -> getCalendar(shown.minusMonths(1).atDay(1), shown.plusMonths(1).atEndOfMonth()) }
        .map { days -> state(days.associateBy(CalendarDay::date)) }
        // Une lecture qui echoue ne doit pas emporter l'accueil : le calendrier se
        // dessine alors sans anneau, ce qu'il fait deja pour une journee sans saisie.
        .catch { emit(state(emptyMap())) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = state(emptyMap()),
        )

    /**
     * Le mois visible a changé : on relit autour de lui.
     *
     * Appelé à chaque défilement du calendrier déplié. Le `StateFlow` absorbe les
     * répétitions — il ne ré-émet pas une valeur égale — et `flatMapLatest` abandonne
     * la lecture précédente : c'est ce qui tient la promesse « les chargements se font
     * en arrière-plan » sans empiler les requêtes.
     */
    fun onVisibleMonth(month: YearMonth) {
        anchor.value = month
    }

    private fun state(days: Map<LocalDate, CalendarDay>) = CalendarUiState(today = clock.today(), days = days)

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
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
    val days: Map<LocalDate, CalendarDay> = emptyMap(),
    /**
     * Le premier jour de la semaine, **porté et non relu**.
     *
     * Il venait d'un `Locale.getDefault()` enfoui dans le getter, et la campagne de
     * défaite l'a montré : sur une machine française, remplacer la règle par « lundi
     * en dur » ne faisait rien tomber. Une lecture d'environnement cachée dans un
     * modèle n'est pas éprouvable — la sortir en paramètre suffit à la rendre visible.
     */
    val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
) {
    /**
     * La **semaine en cours**, du premier jour de semaine de la locale au dernier.
     *
     * Et non les sept derniers jours glissants, qui plaçaient aujourd'hui toujours à
     * droite : on ne lit pas « lundi, mardi… » mais « il y a six jours, il y a cinq
     * jours… », ce qui oblige à compter pour retrouver hier. Une semaine calendaire se
     * lit d'un coup d'œil, et sa géométrie ne bouge pas d'un jour à l'autre.
     */
    val week: List<LocalDate>
        get() {
            val back = ((today.dayOfWeek.value - firstDayOfWeek.value) + DAYS_PER_WEEK) % DAYS_PER_WEEK
            val first = today.minusDays(back.toLong())
            return (0 until DAYS_PER_WEEK).map { first.plusDays(it.toLong()) }
        }

    /**
     * `true` pour un jour qui n'est pas encore arrivé.
     *
     * Il s'affiche en retrait et ne s'ouvre pas : [docs/02][parcours] interdit la
     * saisie dans le futur, et un écran Journée d'un jour à venir ne pourrait rien
     * proposer d'utile tout en laissant croire le contraire.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    fun isFuture(date: LocalDate): Boolean = date.isAfter(today)
}

private const val DAYS_PER_WEEK = 7
