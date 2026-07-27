package app.yukine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes library **writes**. Collection snapshot reads use [runRead] so they never hold the
 * exclusive write mutex and cannot block optimistic favorite patches waiting on a full load.
 */
internal class LibraryMutationContext(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val gateway: () -> LibraryGateway?
) {
    private val writeMutex = Mutex()

    /** Exclusive write path (playlist CRUD, favorite persist, imports that mutate). */
    suspend fun <T> runLocked(operation: () -> T): T =
        writeMutex.withLock { runInterruptible(ioDispatcher) { operation() } }

    /**
     * Non-exclusive read path (full collections snapshot, playlist track load for play).
     * Room WAL allows concurrent readers; writers still take [writeMutex] separately.
     */
    suspend fun <T> runRead(operation: () -> T): T =
        runInterruptible(ioDispatcher) { operation() }

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
            } catch (_: Throwable) {
                gateway()?.showStatusKey(failureStatusKey)
            }
        }
    }

    fun <T> launchRead(
        failureStatusKey: String,
        operation: () -> T,
        onSuccess: ((T) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val result = runRead(operation)
                onSuccess?.invoke(result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                gateway()?.showStatusKey(failureStatusKey)
            }
        }
    }
}
