package app.hexaphore.platform

import app.hexaphore.domain.concurrency.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Les dispatchers réels de l'application.
 *
 * Comme [SystemClock], sa place définitive est `:core:common`.
 */
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main

    override val default: CoroutineDispatcher = Dispatchers.Default

    override val io: CoroutineDispatcher = Dispatchers.IO
}
