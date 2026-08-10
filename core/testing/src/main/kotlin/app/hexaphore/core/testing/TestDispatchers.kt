package app.hexaphore.core.testing

import app.hexaphore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Les trois dispatchers rabattus sur un seul.
 *
 * C'est tout l'intérêt du port : un test pose son propre dispatcher et le travail
 * qu'un adaptateur voulait faire ailleurs revient sous son contrôle. Sans cela, une
 * lecture partie sur un vrai pool de threads oblige à attendre, et un test qui
 * attend est un test intermittent.
 *
 * Ici plutôt qu'en copie privée dans chaque module de test : trois copies d'un même
 * décor divergent le jour où l'une distingue [main] des deux autres.
 */
class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher

    override val default: CoroutineDispatcher get() = dispatcher

    override val io: CoroutineDispatcher get() = dispatcher
}
