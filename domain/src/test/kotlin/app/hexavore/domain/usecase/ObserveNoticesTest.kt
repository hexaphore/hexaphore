package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryAiCredentials
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryKeyRejection
import app.hexavore.core.testing.InMemoryNoticeSettings
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.core.testing.SampleDiary
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProviderCredentials
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.profile.WeightEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Ce qui mérite une pastille, et ce qui n'en mérite pas.
 *
 * **Chaque règle a deux cas et non un** : celui qui l'allume, et celui qui l'éteint.
 * Une pastille dont on n'éprouve que l'allumage peut rester allumée pour toujours sans
 * qu'aucun test ne s'en aperçoive — et c'est la seule façon de rater ce qui compte
 * vraiment, puisqu'une pastille perpétuelle est du papier peint.
 */
class ObserveNoticesTest {
    private val settings = InMemoryNoticeSettings()
    private val credentials = InMemoryAiCredentials()
    private val rejection = InMemoryKeyRejection()
    private val weights = InMemoryWeightLog()
    private val diary = InMemoryDiaryRepository()

    // --- Aucune IA ---------------------------------------------------------------------

    @Test
    fun `sans fournisseur actif, la pastille d IA s allume`() = runTest {
        assertTrue(Notice.AI_NOT_CONFIGURED in notices())
    }

    @Test
    fun `une cle enregistree eteint la pastille d IA`() = runTest {
        credentials.save(AiProvider.ANTHROPIC, CLE)

        assertFalse(Notice.AI_NOT_CONFIGURED in notices())
    }

    // --- Cle refusee -------------------------------------------------------------------

    @Test
    fun `une cle refusee s allume, une fois qu il y a une cle`() = runTest {
        credentials.save(AiProvider.ANTHROPIC, CLE)
        rejection.note()

        assertTrue(Notice.AI_KEY_REJECTED in notices())
    }

    @Test
    fun `une cle refusee sans fournisseur actif ne dit rien de plus`() = runTest {
        // Les deux pastilles d'IA se posent au meme endroit et demandent le meme geste :
        // les allumer ensemble dirait deux fois la meme chose.
        rejection.note()

        assertFalse(Notice.AI_KEY_REJECTED in notices())
    }

    @Test
    fun `une analyse qui aboutit eteint la pastille de refus`() = runTest {
        credentials.save(AiProvider.ANTHROPIC, CLE)
        rejection.note()

        rejection.clear()

        assertFalse(Notice.AI_KEY_REJECTED in notices())
    }

    // --- Poids -------------------------------------------------------------------------

    @Test
    fun `sans aucune pesee, la pastille du poids s allume`() = runTest {
        // Celui qui n'a jamais note de poids est precisement celui a qui la courbe ne
        // sert a rien : attendre une premiere pesee pour en reclamer une serait un
        // silence qui ne se romprait jamais.
        assertTrue(Notice.WEIGHT_STALE in notices())
    }

    @Test
    fun `une pesee du jour eteint la pastille du poids`() = runTest {
        weights.record(WeightEntry(date = AUJOURD_HUI, weightKg = 70.0))

        assertFalse(Notice.WEIGHT_STALE in notices())
    }

    @Test
    fun `une pesee de six jours ne reclame rien`() = runTest {
        // La fenetre de la moyenne mobile fait sept jours : se plaindre plus tot
        // reclamerait une pesee dont le calcul n'a pas encore besoin.
        weights.record(WeightEntry(date = AUJOURD_HUI.minusDays(6), weightKg = 70.0))

        assertFalse(Notice.WEIGHT_STALE in notices())
    }

    @Test
    fun `une pesee de sept jours reclame la suivante`() = runTest {
        weights.record(WeightEntry(date = AUJOURD_HUI.minusDays(7), weightKg = 70.0))

        assertTrue(Notice.WEIGHT_STALE in notices())
    }

    // --- Hier --------------------------------------------------------------------------

    @Test
    fun `une veille vide s allume`() = runTest {
        assertTrue(Notice.YESTERDAY_EMPTY in notices())
    }

    @Test
    fun `une veille notee ne s allume pas`() = runTest {
        SampleDiary.day(HIER).forEach { diary.save(it) }

        assertFalse(Notice.YESTERDAY_EMPTY in notices())
    }

    @Test
    fun `c est bien la veille qui est regardee, pas aujourd hui`() = runTest {
        // Sans ce cas, lire le jour courant passerait les deux precedents : une
        // journee neuve est vide elle aussi.
        SampleDiary.day(AUJOURD_HUI).forEach { diary.save(it) }

        assertTrue(Notice.YESTERDAY_EMPTY in notices(), "aujourd'hui n'est pas hier")
    }

    // --- Les reglages ------------------------------------------------------------------

    @Test
    fun `une pastille eteinte ne s allume pas, meme quand sa situation est vraie`() = runTest {
        settings.setEnabled(Notice.WEIGHT_STALE, enabled = false)

        assertFalse(Notice.WEIGHT_STALE in notices())
        assertTrue(Notice.YESTERDAY_EMPTY in notices(), "les autres ne s'eteignent pas avec elle")
    }

    @Test
    fun `tout eteindre ne laisse rien`() = runTest {
        Notice.entries.forEach { settings.setEnabled(it, enabled = false) }

        assertEquals(emptySet<Notice>(), notices())
    }

    private suspend fun notices() = ObserveNotices(
        settings = settings,
        credentials = credentials,
        rejection = rejection,
        weights = weights,
        diary = diary,
        clock = FixedClock.atNoon(AUJOURD_HUI),
    )().first()

    private companion object {
        val AUJOURD_HUI: LocalDate = LocalDate.of(2026, 8, 24)
        val HIER: LocalDate = AUJOURD_HUI.minusDays(1)
        val CLE = ProviderCredentials(ApiKey("sk-ant-de-test"), model = "claude-opus-5", baseUrl = "https://x/")
    }
}
