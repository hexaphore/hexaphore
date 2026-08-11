package app.hexaphore.core.testing

import app.hexaphore.domain.profile.WeightEntry
import app.hexaphore.domain.profile.WeightLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Le journal de poids, en mémoire.
 *
 * Première implémentation du port, comme [InMemoryGoals] et [InMemoryProfiles].
 */
class InMemoryWeightLog : WeightLog {
    private val state = MutableStateFlow<WeightEntry?>(null)

    /** La dernière pesée écrite, pour qu'un test l'affirme sans passer par un flux. */
    val recorded: WeightEntry? get() = state.value

    override fun observeRecent(limit: Int): Flow<List<WeightEntry>> = state.map { listOfNotNull(it) }

    override fun observeLatest(): Flow<WeightEntry?> = state

    override suspend fun record(entry: WeightEntry) {
        state.value = entry
    }
}
