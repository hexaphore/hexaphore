package app.hexaphore.domain.usecase

import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.InMemoryGoals
import app.hexaphore.core.testing.InMemoryProfiles
import app.hexaphore.core.testing.InMemoryWeightLog
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.goal.GoalId
import app.hexaphore.domain.goal.GoalOrigin
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import app.hexaphore.domain.profile.UserProfile
import app.hexaphore.domain.profile.WeightEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Corriger son profil, et ce que le recalcul n'a pas le droit de réécrire.
 *
 * Deux règles se croisent ici, et chacune protège quelque chose de différent.
 * [app.hexaphore.domain.goal.Goal.manualFields] protège le travail de l'utilisateur :
 * un compteur qu'il a fixé lui-même survit à toutes les corrections de taille ou de
 * poids qui suivront. `D04` protège son histoire : l'objectif de la semaine dernière ne
 * doit pas être repeint par celui d'aujourd'hui.
 *
 * Les deux se cassent en silence. Un recalcul qui écrase un verrou ne produit aucune
 * erreur, seulement un chiffre qui redevient celui du calcul ; une mise à jour en place
 * n'en produit pas davantage, seulement un mois d'historique jugé sur une règle qu'il
 * n'avait pas.
 */
class ReviseGoalTest {
    @Test
    fun `corriger son profil ecrit une nouvelle ligne et clot celle qui court`() = runTest {
        // D04. Le port n'offre aucun `update`, mais rien n'empecherait un appelant
        // d'ecrire une seconde ligne active : c'est `replace` qui clot la premiere,
        // et ce test dit qu'on passe bien par lui.
        val goals = goals(objectifCourant())

        revise(goals, profile = PLUS_PETIT)

        assertEquals(2, goals.all.size, "une correction ajoute une ligne, elle n en modifie aucune")
        val ancien = goals.all.single { it.id == INITIAL }
        val neuf = goals.all.single { it.active }
        assertEquals(AUJOURD_HUI, ancien.endedAt, "l ancien est clos a la date de debut du nouveau")
        assertEquals(AUJOURD_HUI, neuf.startedAt)
        assertNotEquals(ancien.daily.kcal, neuf.daily.kcal, "quatre centimetres de moins changent la depense")
    }

    @Test
    fun `un compteur fixe a la main survit au recalcul`() = runTest {
        // Le coeur de la tranche. Les proteines sont fixees a 150 g, puis la taille
        // et le poids cible changent -- ce qui deplace les six chiffres proposes.
        // Les 150 g doivent traverser.
        val goals = goals(objectifCourant(manual = PROTEINES_FIXEES))

        revise(goals, profile = PLUS_PETIT, request = CIBLE_PLUS_BASSE, manual = PROTEINES_FIXEES)

        val neuf = goals.all.single { it.active }
        assertEquals(150.0, neuf.daily.protein, "un compteur verrouille n est jamais reecrit par un recalcul")
        assertNotEquals(
            calculate(PLUS_PETIT, CIBLE_PLUS_BASSE).goal.protein,
            neuf.daily.protein,
            "sans le verrou, le recalcul aurait ecrit cette valeur-la",
        )
        assertEquals(setOf(Macro.PROTEIN), neuf.manualFields, "le verrou survit a la ligne qui le portait")
        assertEquals(GoalOrigin.MANUAL, neuf.origin)
    }

    @Test
    fun `les cinq autres compteurs suivent le recalcul`() = runTest {
        // La contrepartie du test precedent, et elle compte autant : un verrou qui
        // gelerait l'objectif entier passerait celui d'a cote sans rien proteger.
        val goals = goals(objectifCourant(manual = PROTEINES_FIXEES))
        val avant = goals.all.single()

        revise(goals, profile = PLUS_PETIT, request = CIBLE_PLUS_BASSE, manual = PROTEINES_FIXEES)

        val neuf = goals.all.single { it.active }
        assertNotEquals(avant.daily.kcal, neuf.daily.kcal, "les calories, elles, suivent le profil corrige")
        assertNotEquals(avant.daily.carbs, neuf.daily.carbs)
    }

    @Test
    fun `rendre un compteur au calcul le fait redevenir calcule`() = runTest {
        // La « confirmation explicite » de docs/02 : c'est ce geste-la, et il rend
        // aussi sa provenance a l'objectif.
        val goals = goals(objectifCourant(manual = PROTEINES_FIXEES))

        revise(goals, manual = emptyMap())

        val neuf = goals.all.single { it.active }
        assertEquals(propose.protein, neuf.daily.protein, "le compteur reprend la valeur du calcul")
        assertTrue(neuf.manualFields.isEmpty())
        assertEquals(GoalOrigin.CALCULATED, neuf.origin, "plus aucun compteur fixe, donc plus rien de manuel")
    }

    @Test
    fun `enregistrer sans rien changer n ecrit aucune version`() = runTest {
        // Sinon l'historique des changements de cap devient un journal de
        // consultations, et c'est justement la contrepartie qu'on paie en versionnant.
        val goals = goals(objectifCourant())

        revise(goals)

        assertEquals(1, goals.all.size, "aucune correction, donc aucune ligne")
        assertTrue(goals.all.single().active, "et celui qui courait court toujours")
    }

    @Test
    fun `une pesee n est enregistree que si le poids a change`() = runTest {
        // Le poids affiche est celui de la derniere pesee connue. Le reecrire a la
        // date du jour parce que l'ecran a ete ouvert affirmerait qu'on s'est pese
        // aujourd'hui, et la moyenne mobile compterait une mesure inexistante.
        val goals = goals(objectifCourant())

        revise(goals, profile = PLUS_PETIT)

        assertEquals(1, weights.entries.size, "aucune pesee du jour n a ete inventee")
        assertEquals(DERNIERE_PESEE, weights.latest?.date)
    }

    @Test
    fun `un poids corrige devient la pesee du jour`() = runTest {
        val goals = goals(objectifCourant())

        revise(goals, request = DEMANDE.copy(currentWeightKg = 86.0))

        assertEquals(AUJOURD_HUI, weights.latest?.date)
        assertEquals(86.0, weights.latest?.weightKg)
        assertEquals(2, goals.all.size, "deux kilos de moins, donc un objectif de moins")
    }

    @Test
    fun `un objectif corrige le jour meme prend la journee entiere`() = runTest {
        // L'ancien est clos a sa propre date de debut : il ne couvre alors aucune
        // journee, et c'est juste. Le cas etait eprouve sur `coversOn` seule
        // (GoalCoverageTest) avant que l'ecran existe ; il l'est ici de bout en bout.
        val goals = goals(objectifCourant(depuis = AUJOURD_HUI))

        revise(goals, request = DEMANDE.copy(strategy = GoalStrategy.MAINTAIN, targetWeightKg = null))

        val ancien = goals.all.single { it.id == INITIAL }
        assertEquals(AUJOURD_HUI, ancien.endedAt)
        assertFalse(ancien.coversOn(AUJOURD_HUI), "clos a sa propre date de debut, il ne couvre plus rien")
        assertEquals(goals.all.single { it.active }, goals.observeGoalOn(AUJOURD_HUI).first())
    }

    @Test
    fun `le profil corrige est enregistre, et le reste ne bouge pas`() = runTest {
        val goals = goals(objectifCourant())

        revise(goals, profile = PROFIL.copy(activityLevel = ActivityLevel.SEDENTARY))

        assertEquals(ActivityLevel.SEDENTARY, profiles.saved?.activityLevel)
        assertEquals(PROFIL.birthDate, profiles.saved?.birthDate, "le reste du profil n a pas bouge")
    }

    // --- Montage -----------------------------------------------------------------

    private val clock = FixedClock.atNoon(AUJOURD_HUI)
    private val profiles = InMemoryProfiles(PROFIL)
    private val weights = InMemoryWeightLog(listOf(WeightEntry(DERNIERE_PESEE, POIDS)))
    private val calculate = CalculateDailyGoal(clock)
    private val ids = SequentialIdGenerator("goal")

    /** Ce que le calcul propose pour le profil de référence, sans aucun verrou. */
    private val propose: DailyGoal get() = calculate(PROFIL, DEMANDE).goal

    private fun goals(initial: Goal) = InMemoryGoals(listOf(initial))

    private fun objectifCourant(
        depuis: LocalDate = AUJOURD_HUI.minusMonths(1),
        manual: Map<Macro, Double> = emptyMap(),
    ) = Goal(
        id = INITIAL,
        startedAt = depuis,
        origin = if (manual.isEmpty()) GoalOrigin.CALCULATED else GoalOrigin.MANUAL,
        strategy = DEMANDE.strategy,
        targetWeightKg = DEMANDE.targetWeightKg,
        targetDate = DEMANDE.targetDate,
        daily = propose.overriddenBy(manual),
        manualFields = manual.keys.toSet(),
    )

    private suspend fun revise(
        goals: InMemoryGoals,
        profile: UserProfile = PROFIL,
        request: GoalRequest = DEMANDE,
        manual: Map<Macro, Double> = emptyMap(),
    ) = ReviseGoal(profiles, weights, goals, clock, ids)(
        GoalRevision(
            profile = profile,
            request = request,
            // Ce que l'ecran affichait au moment de l'appui : le cas d'usage ecrit
            // ces chiffres-la et n'en recalcule aucun.
            calculated = calculate(profile, request).goal,
            manual = manual,
        ),
    )

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 10)
        val DERNIERE_PESEE: LocalDate = AUJOURD_HUI.minusDays(3)
        val INITIAL = GoalId("goal-initial")
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

        /** Une correction qui déplace la dépense, donc les six chiffres proposés. */
        val PLUS_PETIT = PROFIL.copy(heightCm = 178.0)

        /** Et une qui déplace les protéines, qui se calculent sur le poids **cible**. */
        val CIBLE_PLUS_BASSE = DEMANDE.copy(targetWeightKg = 78.0)

        val PROTEINES_FIXEES = mapOf(Macro.PROTEIN to 150.0)
    }
}
