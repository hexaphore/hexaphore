package app.hexaphore.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * La valeur que ce flux émet **après** [write], et non celle qu'il émettait avant.
 *
 * C'est l'outil qui éprouve qu'un port observe vraiment. Une relecture après coup
 * passerait même si le flux n'avait jamais ré-émis — c'est-à-dire même si le port
 * était resté une lecture unique, qui est précisément le défaut corrigé par
 * [D53][decisions] : un instantané ne peut pas se démentir. Ici, l'attente expire et
 * le message le dit.
 *
 * Ici plutôt qu'en copie privée dans chaque jeu de tests de contrat : il en existait
 * trois, et trois copies d'un même décor divergent le jour où l'une gagne un délai
 * plus long ou avale une émission. C'est le même raisonnement qui a fait monter
 * [TestDispatchers] dans ce module.
 *
 * @param write l'écriture qui doit provoquer une nouvelle émission.
 * @param matching ce qu'on attend de la valeur émise. Les émissions intermédiaires
 *   sont ignorées : une écriture peut en produire plusieurs, et seul l'état stable
 *   se décrit.
 * @param timeoutMillis au-delà, l'absence d'émission est un échec et non une attente.
 * @throws IllegalStateException si le flux ne ré-émet pas dans le délai.
 *
 * [decisions]: docs/11-decisions.md
 */
suspend fun <T> Flow<T>.firstAfter(
    write: suspend () -> Unit,
    matching: (T) -> Boolean,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
): T = coroutineScope {
    val received = Channel<T>(Channel.UNLIMITED)
    val collection = launch(Dispatchers.Default) { collect { received.send(it) } }
    try {
        withTimeout(timeoutMillis) {
            // La valeur courante d'abord : c'est elle qui prouve que la collecte a
            // demarre, donc que l'ecriture qui suit ne peut pas passer inapercue.
            received.receive()
            write()
            var value = received.receive()
            while (!matching(value)) value = received.receive()
            value
        }
    } catch (_: TimeoutCancellationException) {
        error("le flux n a pas re-emis apres l ecriture : est-il encore une lecture unique ?")
    } finally {
        collection.cancel()
    }
}

private const val DEFAULT_TIMEOUT_MILLIS = 5_000L
