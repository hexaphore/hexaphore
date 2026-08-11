package app.hexaphore.feature.settings

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryGoals
import app.hexaphore.core.testing.InMemoryProfiles
import app.hexaphore.core.testing.InMemoryWeightLog
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.goal.GoalId
import app.hexaphore.domain.goal.GoalOrigin
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import app.hexaphore.domain.profile.UserProfile
import app.hexaphore.domain.profile.WeightEntry
import app.hexaphore.domain.usecase.CalculateDailyGoal
import app.hexaphore.domain.usecase.GoalRequest
import app.hexaphore.domain.usecase.ReviseGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * L'écran des réglages profil : ce qu'il relit, ce qu'il recalcule, ce qu'il écrit.
 *
 * L'écran lui-même n'est pas éprouvé ici — il n'y a pas d'émulateur. Ce qui l'est,
 * c'est ce dont il dépend et qui casserait en silence : le formulaire ouvre sur ce qui
 * est **enregistré** et non sur des valeurs par défaut, un compteur fixé reste fixé
 * pendant qu'on corrige le reste, et un refus d'enregistrer dit lequel des quatre
 * manques il oppose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = FixedClock.atNoon(AUJOURD_HUI)
    private val profiles = InMemoryProfiles(PROFIL)
    private val weights = InMemoryWeightLog(listOf(WeightEntry(AUJOURD_HUI.minusDays(3), POIDS)))
    private val calculate = CalculateDailyGoal(clock)
    private var goals = InMemoryGoals()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `l ecran ouvre sur ce qui est enregistre`() = runTest(dispatcher) {
        // Et non sur des valeurs par defaut : c'est un ecran de correction, pas de
        // creation, et un champ pre-rempli d'un chiffre plausible serait accepte par
        // distraction (D56).
        val viewModel = viewModel(objectifCourant())
        val form = viewModel.uiState.value.form

        assertTrue(viewModel.uiState.value.loaded)
        assertEquals(PROFIL.birthDate, form.birthDate)
        assertEquals(Sex.MALE, form.sex)
        assertEquals(182.0, form.heightCm)
        assertEquals(POIDS, form.currentWeightKg, "le poids vient du journal de pesees, pas du profil")
        assertEquals(ActivityLevel.MODERATE, form.activityLevel)
        assertEquals(GoalStrategy.LOSE, form.strategy)
        assertEquals(2_525.0, viewModel.uiState.value.plan?.goal?.kcal, "l exemple de reference de docs/03")
    }

    @Test
    fun `un compteur deja fixe rouvre verrouille, avec sa valeur`() = runTest(dispatcher) {
        // Le verrou est en base depuis la tranche 4 (`goal.manual_fields`) et rien ne
        // l'ecrivait. C'est ce test qui dit qu'il fait desormais l'aller-retour.
        val viewModel = viewModel(objectifCourant(manual = mapOf(Macro.PROTEIN to 150.0)))
        val state = viewModel.uiState.value

        assertTrue(state.form.locked(Macro.PROTEIN))
        assertEquals(150.0, state.shown(Macro.PROTEIN), "le chiffre affiche est celui qui a ete fixe")
        assertFalse(state.form.locked(Macro.CARBS))
    }

    @Test
    fun `corriger le profil deplace les compteurs libres et laisse le verrouille`() = runTest(dispatcher) {
        // Le coeur de l'ecran, vu depuis l'ecran : c'est la meme regle que
        // ReviseGoalTest eprouve a l'ecriture, ici sur ce qui s'affiche pendant
        // qu'on tape. Les deux doivent dire la meme chose, sinon on enregistre
        // autre chose que ce qu'on a lu.
        val viewModel = viewModel(objectifCourant(manual = mapOf(Macro.PROTEIN to 150.0)))
        val avant = viewModel.uiState.value

        viewModel.onForm(avant.form.copy(heightCm = 178.0))

        val apres = viewModel.uiState.value
        assertEquals(150.0, apres.shown(Macro.PROTEIN), "un compteur fixe ne bouge pas")
        assertNotEquals(
            avant.shown(Macro.CALORIES),
            apres.shown(Macro.CALORIES),
            "les compteurs libres, eux, suivent le profil",
        )
    }

    @Test
    fun `fixer un compteur part du chiffre propose, et le rendre au calcul le libere`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifCourant())
        val propose = viewModel.uiState.value.plan?.goal?.protein

        viewModel.onLock(Macro.PROTEIN)

        assertTrue(viewModel.uiState.value.form.locked(Macro.PROTEIN))
        assertEquals(propose, viewModel.uiState.value.form.manual[Macro.PROTEIN], "on corrige de quelques grammes")

        viewModel.onRelease(Macro.PROTEIN)

        assertFalse(viewModel.uiState.value.form.locked(Macro.PROTEIN))
        assertEquals(propose, viewModel.uiState.value.shown(Macro.PROTEIN))
    }

    @Test
    fun `un compteur fixe mais vide refuse l enregistrement, et dit lequel`() = runTest(dispatcher) {
        // Vider le champ ne doit pas deverrouiller en douce : le verrou reste, et
        // c'est le bouton qui refuse en disant quoi (D28).
        val viewModel = viewModel(objectifCourant())
        viewModel.onLock(Macro.PROTEIN)

        viewModel.onCounterChange(Macro.PROTEIN, null)

        assertTrue(viewModel.uiState.value.form.locked(Macro.PROTEIN), "le verrou tient")
        assertEquals(ProfileBlocker.EMPTY_COUNTER, viewModel.uiState.value.blocker)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `un champ d identite vide refuse l enregistrement`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifCourant())

        viewModel.onForm(viewModel.uiState.value.form.copy(heightCm = null))

        assertEquals(ProfileBlocker.IDENTITY, viewModel.uiState.value.blocker)
        assertNull(viewModel.uiState.value.plan, "sans taille, aucun chiffre ne s affiche")
    }

    @Test
    fun `choisir le maintien efface le poids cible et l echeance`() = runTest(dispatcher) {
        // Les garder ferait calculer un ecart calorique pour une strategie qui n'en
        // veut pas : l'utilisateur verrait un deficit sous une etiquette « maintien ».
        val viewModel = viewModel(objectifCourant())

        viewModel.onForm(viewModel.uiState.value.form.copy(strategy = GoalStrategy.MAINTAIN))

        val form = viewModel.uiState.value.form
        assertNull(form.targetWeightKg)
        assertNull(form.targetDate)
        assertNull(viewModel.uiState.value.blocker, "un maintien n exige ni cible ni echeance")
    }

    @Test
    fun `enregistrer ouvre une nouvelle version et referme l ecran`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifCourant())
        viewModel.onForm(viewModel.uiState.value.form.copy(heightCm = 178.0))
        viewModel.onLock(Macro.PROTEIN)
        var referme = false

        viewModel.onSave { referme = true }
        advanceUntilIdle()

        assertTrue(referme)
        assertEquals(2, goals.all.size, "D04 : une ligne de plus, jamais celle qui court modifiee")
        val neuf = goals.all.single { it.active }
        assertEquals(AUJOURD_HUI, neuf.startedAt)
        assertEquals(GoalOrigin.MANUAL, neuf.origin)
        assertEquals(setOf(Macro.PROTEIN), neuf.manualFields)
        assertEquals(178.0, profiles.saved?.heightCm)
    }

    @Test
    fun `un echec de lecture se dit au lieu d afficher un formulaire vide`() = runTest(dispatcher) {
        // Un formulaire vide enregistre ecraserait le profil qui est en base par des
        // champs que personne n'a saisis. C'est plus qu'un mensonge d'affichage.
        val viewModel = viewModel(objectifCourant(), unreadable = true)

        assertTrue(viewModel.uiState.value.unreadable)
        assertNull(viewModel.uiState.value.form.birthDate)
    }

    @Test
    fun `un echec d ecriture se dit et conserve les corrections`() = runTest(dispatcher) {
        val viewModel = viewModel(objectifCourant())
        viewModel.onForm(viewModel.uiState.value.form.copy(heightCm = 178.0))
        goals.failure = true
        var referme = false

        viewModel.onSave { referme = true }
        advanceUntilIdle()

        assertFalse(referme, "l ecran ne se referme pas sur un echec")
        assertTrue(viewModel.uiState.value.failed)
        assertEquals(178.0, viewModel.uiState.value.form.heightCm, "la correction est conservee telle quelle")
    }

    // --- Montage -----------------------------------------------------------------

    private fun viewModel(initial: Goal, unreadable: Boolean = false): ProfileViewModel {
        goals = InMemoryGoals(listOf(initial), failure = unreadable)
        return ProfileViewModel(
            profiles = profiles,
            weights = weights,
            goals = goals,
            calculate = calculate,
            reviseGoal = ReviseGoal(profiles, weights, goals, clock, SequentialIdGenerator("goal")),
            clock = clock,
        )
    }

    private fun objectifCourant(manual: Map<Macro, Double> = emptyMap()) = Goal(
        id = GoalId("goal-initial"),
        startedAt = AUJOURD_HUI.minusMonths(1),
        origin = if (manual.isEmpty()) GoalOrigin.CALCULATED else GoalOrigin.MANUAL,
        strategy = DEMANDE.strategy,
        targetWeightKg = DEMANDE.targetWeightKg,
        targetDate = DEMANDE.targetDate,
        daily = calculate(PROFIL, DEMANDE).goal.overriddenBy(manual),
        manualFields = manual.keys,
    )

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 10)
        const val POIDS = 88.0

        /** L'exemple de docs/03 : homme, 35 ans, 182 cm, 88 kg, 80 kg en 6 mois. */
        val PROFIL = UserProfile(
            birthDate = LocalDate.of(1991, 3, 4),
            sex = Sex.MALE,
            heightCm = 182.0,
            activityLevel = ActivityLevel.MODERATE,
        )

        val DEMANDE = GoalRequest(
            strategy = GoalStrategy.LOSE,
            currentWeightKg = POIDS,
            targetWeightKg = 80.0,
            targetDate = AUJOURD_HUI.plusDays(182),
        )
    }
}
