package app.yukine

import android.net.Uri
import app.yukine.fingerprint.AudioFingerprintCandidate
import app.yukine.fingerprint.AudioFingerprintEvidence
import app.yukine.model.Track
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAudioVerificationOwnerTest {
    @Test
    fun runsBoundedBatchOffCallerAndCoalescesConcurrentSchedules() {
        val pendingCalls = AtomicInteger()
        val analyzed = AtomicInteger()
        val saved = AtomicInteger()
        val entered = CountDownLatch(1)
        val releaseAnalysis = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val refreshed = CountDownLatch(1)
        val identityChanged = CountDownLatch(1)
        val refreshedTrackIds = mutableListOf<Long>()
        val operations = object : LibraryAudioVerificationOwner.Operations {
            override fun pending(limit: Int): List<AudioFingerprintCandidate> {
                pendingCalls.incrementAndGet()
                assertEquals(4, limit)
                return listOf(candidate(1L), candidate(2L))
            }

            override fun analyze(candidate: AudioFingerprintCandidate): AudioFingerprintEvidence {
                entered.countDown()
                assertTrue(releaseAnalysis.await(2, TimeUnit.SECONDS))
                analyzed.incrementAndGet()
                return AudioFingerprintEvidence("", "fp-${candidate.sourceId}", 1, 10_000L)
            }

            override fun save(
                candidate: AudioFingerprintCandidate,
                evidence: AudioFingerprintEvidence
            ): Boolean {
                saved.incrementAndGet()
                completed.countDown()
                return true
            }

            override fun fail(candidate: AudioFingerprintCandidate, errorCode: String) = false

            override fun refreshMatches(localTrackIds: List<Long>): Int {
                refreshedTrackIds += localTrackIds
                refreshed.countDown()
                return 1
            }
        }
        val owner = LibraryAudioVerificationOwner(
            operations,
            Executors.newSingleThreadExecutor(),
            true,
            Runnable { identityChanged.countDown() }
        )
        try {
            owner.schedule()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            owner.schedule()
            releaseAnalysis.countDown()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertTrue(refreshed.await(2, TimeUnit.SECONDS))
            assertTrue(identityChanged.await(2, TimeUnit.SECONDS))
            assertEquals(1, pendingCalls.get())
            assertEquals(2, analyzed.get())
            assertEquals(2, saved.get())
            assertEquals(listOf(1L, 2L), refreshedTrackIds)
        } finally {
            owner.release()
        }
    }

    @Test
    fun analysisFailureIsPersistedWithoutStoppingRemainingCandidates() {
        val failures = AtomicInteger()
        val successes = AtomicInteger()
        val completed = CountDownLatch(2)
        val operations = object : LibraryAudioVerificationOwner.Operations {
            override fun pending(limit: Int) = listOf(candidate(1L), candidate(2L))
            override fun analyze(candidate: AudioFingerprintCandidate): AudioFingerprintEvidence {
                if (candidate.sourceId == 1L) error("decoder unavailable")
                return AudioFingerprintEvidence("", "ok", 1, 10_000L)
            }
            override fun save(
                candidate: AudioFingerprintCandidate,
                evidence: AudioFingerprintEvidence
            ): Boolean {
                successes.incrementAndGet()
                completed.countDown()
                return true
            }
            override fun fail(candidate: AudioFingerprintCandidate, errorCode: String): Boolean {
                failures.incrementAndGet()
                completed.countDown()
                return true
            }
        }
        val owner = LibraryAudioVerificationOwner(
            operations,
            Executors.newSingleThreadExecutor(),
            true
        )
        try {
            owner.schedule()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(1, failures.get())
            assertEquals(1, successes.get())
        } finally {
            owner.release()
        }
    }

    private fun candidate(sourceId: Long) = AudioFingerprintCandidate(
        sourceId,
        Track(
            sourceId,
            "Track $sourceId",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("content://media/$sourceId"),
            "/music/$sourceId.flac",
            sourceId,
            null
        ),
        "signature-$sourceId"
    )
}
