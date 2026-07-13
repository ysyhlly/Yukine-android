package app.yukine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes library writes and blocking reads without exposing the UI state owner. */
internal class LibraryMutationContext(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val gateway: () -> LibraryGateway?
) {
    private val mutex = Mutex()

    suspend fun <T> runLocked(operation: () -> T): T =
        mutex.withLock { runInterruptible(ioDispatcher) { operation() } }

    fun <T> launch(
        failureStatusKey: String,
        operation: () -> T,
        onSuccess: ((T) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val result = runLocked(operation)
                onSuccess?.invoke(result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                gateway()?.showStatusKey(failureStatusKey)
            }
        }
    }
}
