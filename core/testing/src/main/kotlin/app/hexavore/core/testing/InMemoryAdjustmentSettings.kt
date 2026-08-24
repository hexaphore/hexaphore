package app.hexavore.core.testing

import app.hexavore.domain.goal.AdjustmentSettings
import app.hexavore.domain.goal.AdjustmentSetup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * Les réponses aux suggestions, en mémoire.
 *
 * Première implémentation du port, comme [InMemoryGoals] et [InMemoryWeightLog].
 *
 * **Les trois écritures sont distinctes, et le faux ne les confond pas.** « Ignorer »
 * ne doit pas laisser la trace d'un ajustement accepté : le silence dure autant, mais
 * ce n'est pas le même fait, et un faux qui les fondrait laisserait passer un
 * adaptateur qui les fond aussi — l'historique dirait alors qu'un objectif a été
 * corrigé un jour où l'utilisateur avait refusé.
 */
class InMemoryAdjustmentSettings(initial: AdjustmentSetup = AdjustmentSetup()) : AdjustmentSettings {
    private val state = MutableStateFlow(initial)

    /** L'état courant, pour qu'un test l'affirme sans passer par un flux. */
    val setup: AdjustmentSetup get() = state.value

    override fun observe(): Flow<AdjustmentSetup> = state

    override suspend fun accepted(on: LocalDate) {
        state.value = state.value.copy(lastAcceptedOn = on)
    }

    override suspend fun ignored(on: LocalDate) {
        state.value = state.value.copy(lastIgnoredOn = on)
    }

    override suspend fun stop() {
        state.value = state.value.copy(enabled = false)
    }

    /**
     * Pose l'etat entier, et **remplace** au lieu de fusionner.
     *
     * Un `copy` partiel serait plus indulgent que le vrai, qui reecrit les trois
     * cles : un « ne plus proposer » d'avant la restauration survivrait a un fichier
     * qui ne le portait pas, et le test n'y verrait rien.
     */
    override suspend fun restore(setup: AdjustmentSetup) {
        state.value = setup
    }
}
