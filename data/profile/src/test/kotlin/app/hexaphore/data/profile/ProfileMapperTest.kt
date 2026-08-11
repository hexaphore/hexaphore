package app.hexaphore.data.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexaphore.core.database.HexaphoreDatabase
import app.hexaphore.core.database.entity.GoalEntity
import app.hexaphore.core.database.entity.ProfileEntity
import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.core.testing.TestDispatchers
import app.hexaphore.domain.goal.GoalOrigin
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import app.hexaphore.domain.profile.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Ce qu'une base écrite par une version plus récente ne doit pas casser.
 *
 * Cette propriété **n'appartient pas au contrat des ports** et ne peut pas y figurer :
 * les énumérations du domaine sont fermées, donc aucun appelant de `Profiles` ou de
 * `Goals` ne peut soumettre une valeur que le mapper aurait à replier. Le seul chemin
 * qui l'atteint est celui d'une base déjà écrite — une sauvegarde restaurée depuis une
 * version plus récente, ou une rétrogradation d'application.
 *
 * Les tests écrivent donc **directement l'entité**, sans passer par le port. Le repli
 * était documenté depuis la tranche 4 ; rien ne l'éprouvait, et un repli qui plante est
 * un profil inaccessible, donc une application inutilisable.
 *
 * **Chaque repli est choisi pour être le moins engageant** : sous-estimer la dépense
 * plutôt que la surestimer, et appliquer la moyenne des deux formules pour un sexe non
 * reconnu — exactement ce que « je préfère ne pas répondre » demande déjà.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class ProfileMapperTest {
    private lateinit var base: HexaphoreDatabase
    private lateinit var magasin: RoomProfileStore

    @Before
    fun ouvrir() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        base = Room.inMemoryDatabaseBuilder(context, HexaphoreDatabase::class.java).build()
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
    fun `un sexe inconnu applique la moyenne des deux formules`() = runBlocking {
        ecrireProfil(sex = "MARTIEN")

        assertEquals(Sex.UNSPECIFIED, magasin.observeProfile().first()!!.sex)
    }

    @Test
    fun `un niveau d activite inconnu retombe sur le plus bas`() = runBlocking {
        // Le moins engageant : sous-estimer la depense plutot que la surestimer.
        ecrireProfil(activityLevel = "ATHLETE_OLYMPIQUE")

        assertEquals(ActivityLevel.SEDENTARY, magasin.observeProfile().first()!!.activityLevel)
    }

    @Test
    fun `un systeme d unites inconnu retombe sur le metrique`() = runBlocking {
        // Le stockage est toujours metrique : c'est l'affichage qui varie.
        ecrireProfil(unitSystem = "COUDEES")

        assertEquals(UnitSystem.METRIC, magasin.observeProfile().first()!!.unitSystem)
    }

    @Test
    fun `un profil dont toutes les enumerations sont inconnues reste lisible`() = runBlocking {
        // Le cas qui compte vraiment : trois replis a la fois, et le profil revient
        // quand meme. Planter ici rendrait l'application entiere inaccessible.
        ecrireProfil(sex = "MARTIEN", activityLevel = "ATHLETE_OLYMPIQUE", unitSystem = "COUDEES")

        val profil = magasin.observeProfile().first()!!
        assertEquals(TAILLE_CM, profil.heightCm, 0.0)
        assertEquals(Sex.UNSPECIFIED, profil.sex)
    }

    @Test
    fun `une provenance et une strategie inconnues retombent sur le calcul et le maintien`() = runBlocking {
        ecrireObjectif(origin = "IMPORTE_DE_MARS", strategy = "SECHE_EXTREME")

        val objectif = magasin.observeCurrent().first()!!
        assertEquals(GoalOrigin.CALCULATED, objectif.origin)
        assertEquals(GoalStrategy.MAINTAIN, objectif.strategy)
    }

    @Test
    fun `un compteur fixe inconnu est ignore, les autres survivent`() = runBlocking {
        // Perdre un verrou fait qu'un recalcul reecrira ce compteur, ce qui est
        // ennuyeux. Rendre l'objectif illisible ferait perdre les six.
        ecrireObjectif(manualFields = "PROTEIN,VITAMINE_C,FAT")

        assertEquals(setOf(Macro.PROTEIN, Macro.FAT), magasin.observeCurrent().first()!!.manualFields)
    }

    @Test
    fun `un objectif sans compteur fixe rend un ensemble vide`() = runBlocking {
        // La chaine vide decoupee sur une virgule rend une chaine vide, et non rien
        // du tout : sans le repli, l'ensemble contiendrait un element fantome.
        ecrireObjectif(manualFields = "")

        assertEquals(emptySet<Macro>(), magasin.observeCurrent().first()!!.manualFields)
    }

    private suspend fun ecrireProfil(
        sex: String = Sex.MALE.name,
        activityLevel: String = ActivityLevel.MODERATE.name,
        unitSystem: String = UnitSystem.METRIC.name,
    ) = base.profileDao().upsert(
        ProfileEntity(
            birthDate = "1991-03-04",
            sex = sex,
            heightCm = TAILLE_CM,
            activityLevel = activityLevel,
            unitSystem = unitSystem,
            createdAt = INSTANT_MILLIS,
            updatedAt = INSTANT_MILLIS,
        ),
    )

    private suspend fun ecrireObjectif(
        origin: String = GoalOrigin.CALCULATED.name,
        strategy: String = GoalStrategy.MAINTAIN.name,
        manualFields: String = "",
    ) = base.goalDao().insert(
        GoalEntity(
            id = "goal-relu",
            startedAt = "2026-06-01",
            endedAt = null,
            activeKey = GoalEntity.ACTIVE,
            origin = origin,
            strategy = strategy,
            targetWeightKg = null,
            targetDate = null,
            kcal = 2_000.0,
            proteinG = 112.0,
            carbG = 223.0,
            sugarG = 50.0,
            fatG = 67.0,
            fiberG = 28.0,
            manualFields = manualFields,
            createdAt = INSTANT_MILLIS,
        ),
    )

    private companion object {
        val MAINTENANT: Instant = Instant.parse("2026-08-10T10:00:00Z")
        val INSTANT_MILLIS: Long = MAINTENANT.toEpochMilli()
        const val TAILLE_CM = 182.0
    }
}
