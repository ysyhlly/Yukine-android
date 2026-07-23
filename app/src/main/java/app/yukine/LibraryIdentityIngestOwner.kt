package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.diagnostics.DiagnosticLog
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Activity-scoped owner for expensive physical-source identity ingestion.
 *
 * Library publication never waits for this work. Repeated scan/import requests are coalesced,
 * while a request arriving during an active pass schedules one final pass over persisted rows.
 */
internal class LibraryIdentityIngestOwner private constructor(
    private val operations: Operations,
    private val executor: ExecutorService,
    private val identityChanged: Runnable,
    private val initialDelayMs: Long
) {
    fun interface Operations {
        fun ingestConfirmedSources(): Int
    }

    @JvmOverloads
    constructor(
        repository: MusicLibraryRepository,
        identityChanged: Runnable = Runnable {}
    ) : this(
        Operations(repository::ingestPendingConfirmedIdentitySources),
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "echo-library-identity").apply { priority = Thread.MIN_PRIORITY }
        },
        identityChanged,
        INITIAL_DELAY_MS
    )

    internal constructor(
        operations: Operations,
        executor: ExecutorService,
        testing: Boolean,
        identityChanged: Runnable = Runnable {}
    ) : this(operations, executor, identityChanged, 0L)

    private val running = AtomicBoolean()
    private val rerunRequested = AtomicBoolean()

    fun schedule() {
        rerunRequested.set(true)
        startIfNeeded()
    }

    fun release() {
        rerunRequested.set(false)
        executor.shutdownNow()
    }

    private fun startIfNeeded() {
        if (!running.compareAndSet(false, true)) return
        try {
            executor.execute(::drain)
        } catch (_: RejectedExecutionException) {
            running.set(false)
        }
    }

    private fun drain() {
        try {
            if (initialDelayMs > 0L) {
                try {
                    Thread.sleep(initialDelayMs)
                } catch (_: InterruptedException) {
                    return
                }
            }
            while (!Thread.currentThread().isInterrupted && rerunRequested.getAndSet(false)) {
                val changed = try {
                    operations.ingestConfirmedSources()
                } catch (error: Throwable) {
                    if (error is InterruptedException || Thread.currentThread().isInterrupted) {
                        return
                    }
                    DiagnosticLog.w(TAG, "Background library identity ingestion failed", error)
                    0
                }
                if (changed > 0) {
                    runCatching(identityChanged::run)
                        .onFailure { error ->
                            DiagnosticLog.w(TAG, "Unable to publish ingested library identities", error)
                        }
                }
            }
        } finally {
            running.set(false)
            if (rerunRequested.get() && !executor.isShutdown) {
                startIfNeeded()
            }
        }
    }

    private companion object {
        const val TAG = "LibraryIdentityIngest"
        const val INITIAL_DELAY_MS = 1_500L
    }
}
