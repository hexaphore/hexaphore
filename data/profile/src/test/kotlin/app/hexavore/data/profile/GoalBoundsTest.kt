package app.hexavore.data.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.database.entity.GoalEntity
import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.core.testing.TestDispatchers
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * La borne de fin d'un objectif, éprouvée là où elle est **observable**.
 *
 * Elle ne l'est pas par le contrat des ports, et c'est la découverte de ce travail.
 * Tout objectif clos par `replace` l'est à la date de début de son successeur, donc
 * les deux périodes se touchent sans trou : le `ORDER BY started_at DESC LIMIT 1` de
 * la requête rend alors le bon objectif **même si la borne est relâchée en `>=`**.
 * Vérifié en défaisant : passer la requête à `>=` laisse les 49 cas du contrat au
 * vert. Un test qui ne tombe pas quand la règle casse ne garde pas cette règle.
 *
 * Ce qui la rend observable est un objectif clos **sans successeur qui reprenne le
 * jour même**. `replace` n'en produit pas, mais une sauvegarde restaurée le fera
 * (tranche 8), et surtout la convention est **publiée** — `Goal.coversOn` est publique
 * et le KDoc du DAO l'affirme. Une règle publiée tient seule ou n'est pas une règle.
 *
 * Le pendant côté domaine est `GoalCoverageTest`, qui éprouve la même convention sur
 * `coversOn` sans passer par une base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class GoalBoundsTest {
    private lateinit var base: HexavoreDatabase
    private lateinit var magasin: RoomProfileStore

    @Before
    fun ouvrir() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        base = Room.inMemoryDatabaseBuilder(context, HexavoreDatabase::class.java).build()
        magasin = RoomProfileStore(
            profiles = base.profileDao(),
            goalDao = base.goalDao(),
            ids = SequentialIdGenerator("pesee"),
            clock = FixedClock(MAINTENANT),
            dispatchers = TestDispatchers(Dispatchers.IO),
        )
    }

    @After
    fun fermer() = base.close()

    @Test
    fun `le jour de fin d un objectif clos ne lui appartient plus`() = runBlocking {
        // Le cas discriminant : aucun successeur ne couvre ce jour-la, donc l'ordre de
        // tri ne peut pas rattraper une borne relachee. La reponse juste est `null`.
        ecrireObjectifClos()

        assertNull(magasin.observeGoalOn(FIN).first())
    }

    @Test
    fun `la veille de la fin lui appartient encore`() = runBlocking {
        // L'autre borne du meme seuil. Sans ce cas, une requete qui ne rendrait
        // jamais rien passerait le test precedent en ne mesurant rien.
        ecrireObjectifClos()

        assertEquals(IDENTIFIANT, magasin.observeGoalOn(FIN.minusDays(1)).first()?.id?.value)
    }

    @Test
    fun `le jour de debut lui appartient`() = runBlocking {
        ecrireObjectifClos()

        assertEquals(IDENTIFIANT, magasin.observeGoalOn(DEBUT).first()?.id?.value)
    }

    @Test
    fun `la veille du debut ne lui appartient pas`() = runBlocking {
        ecrireObjectifClos()

        assertNull(magasin.observeGoalOn(DEBUT.minusDays(1)).first())
    }

    private suspend fun ecrireObjectifClos() = base.goalDao().insert(
        GoalEntity(
            id = IDENTIFIANT,
            startedAt = DEBUT.toString(),
            endedAt = FIN.toString(),
            // Clos : la cle d'activite porte l'identifiant, pas la constante.
            activeKey = IDENTIFIANT,
            origin = GoalOrigin.CALCULATED.name,
            strategy = GoalStrategy.MAINTAIN.name,
            targetWeightKg = null,
            targetDate = null,
            kcal = 2_000.0,
            proteinG = 112.0,
            carbG = 223.0,
            sugarG = 50.0,
            fatG = 67.0,
            fiberG = 28.0,
            createdAt = MAINTENANT.toEpochMilli(),
        ),
    )

    private companion object {
        val MAINTENANT: Instant = Instant.parse("2026-08-10T10:00:00Z")
        val DEBUT: LocalDate = LocalDate.of(2026, 6, 1)
        val FIN: LocalDate = LocalDate.of(2026, 7, 1)
        const val IDENTIFIANT = "goal-clos"
    }
}
