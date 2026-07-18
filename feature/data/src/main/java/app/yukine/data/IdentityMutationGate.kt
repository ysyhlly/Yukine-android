package app.yukine.data

import java.util.concurrent.Callable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Process-wide serialization boundary for canonical identity mutations.
 *
 * Room already serializes SQLite writers, but independent WorkManager and foreground sync entry
 * points can otherwise occupy the single writer for long enough to starve each other. The fair,
 * reentrant lock keeps each bounded identity unit atomic without holding the gate across an entire
 * online batch.
 */
internal object IdentityMutationGate {
    private val lock = ReentrantLock(true)

    fun <T> withLock(block: () -> T): T = lock.withLock(block)

    @JvmStatic
    fun <T> runExclusive(block: Callable<T>): T = lock.withLock(block::call)
}
