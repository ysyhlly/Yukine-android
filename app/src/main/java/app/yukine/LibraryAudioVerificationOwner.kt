package app.yukine

import android.content.Context
import app.yukine.data.MusicLibraryRepository
import app.yukine.fingerprint.AudioFingerprintCandidate
import app.yukine.fingerprint.AudioFingerprintEvidence
import app.yukine.fingerprint.ChromaprintSegmentAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Activity-scoped lifecycle owner for bounded, offline-only traditional audio verification. */
internal class LibraryAudioVerificationOwner private constructor(
    private val operations: Operations,
    private val executor: ExecutorService,
    private val identityChanged: Runnable
) {
    interface Operations {
        fun pending(limit: Int): List<AudioFingerprintCandidate>
        fun analyze(candidate: AudioFingerprintCandidate): AudioFingerprintEvidence
        fun save(candidate: AudioFingerprintCandidate, evidence: AudioFingerprintEvidence): Boolean
        fun fail(candidate: AudioFingerprintCandidate, errorCode: String): Boolean
        fun refreshMatches(localTrackIds: List<Long>): Int = 0
    }

    @JvmOverloads
    constructor(
        context: Context,
        repository: MusicLibraryRepository,
        identityChanged: Runnable = Runnable {}
    ) : this(
        RepositoryOperations(context.applicationContext, repository),
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "echo-audio-verification").apply { priority = Thread.MIN_PRIORITY }
        },
        identityChanged
    )

    internal constructor(
        operations: Operations,
        executor: ExecutorService,
        testing: Boolean,
        identityChanged: Runnable = Runnable {}
    ) : this(operations, executor, identityChanged)

    private val running = AtomicBoolean()

    fun schedule() {
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            try {
                val verifiedTrackIds = ArrayList<Long>(BATCH_LIMIT)
                operations.pending(BATCH_LIMIT).forEach { candidate ->
                    if (Thread.currentThread().isInterrupted) return@execute
                    runCatching { operations.analyze(candidate) }
                        .onSuccess { evidence ->
                            if (operations.save(candidate, evidence)) {
                                verifiedTrackIds += candidate.track.id
                            }
                        }
                        .onFailure { error ->
                            if (error is InterruptedException) return@execute
                            operations.fail(candidate, error.message ?: error.javaClass.simpleName)
                        }
                }
                if (verifiedTrackIds.isNotEmpty()) {
                    val mergedRecordingCount = operations.refreshMatches(verifiedTrackIds.distinct())
                    if (mergedRecordingCount > 0) {
                        identityChanged.run()
                    }
                }
            } finally {
                running.set(false)
            }
        }
    }

    fun release() {
        executor.shutdownNow()
    }

    private class RepositoryOperations(
        context: Context,
        private val repository: MusicLibraryRepository
    ) : Operations {
        private val analyzer = ChromaprintSegmentAnalyzer(context)

        override fun pending(limit: Int) = repository.loadPendingAudioFingerprintCandidates(limit)
        override fun analyze(candidate: AudioFingerprintCandidate) = analyzer.analyze(candidate)
        override fun save(candidate: AudioFingerprintCandidate, evidence: AudioFingerprintEvidence) =
            repository.saveAudioFingerprint(candidate, evidence)
        override fun fail(candidate: AudioFingerprintCandidate, errorCode: String) =
            repository.recordAudioFingerprintFailure(candidate, errorCode)
        override fun refreshMatches(localTrackIds: List<Long>) =
            repository.refreshAudioVerifiedMatches(localTrackIds)
    }

    companion object {
        private const val BATCH_LIMIT = 4
    }
}
