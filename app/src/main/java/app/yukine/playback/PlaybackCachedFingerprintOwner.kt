package app.yukine.playback

import android.content.Context
import android.os.Handler
import android.util.Log
import app.yukine.data.MusicLibraryRepository
import app.yukine.fingerprint.ChromaprintSegmentAnalyzer
import app.yukine.model.Track
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Schedules WebDAV fingerprinting only after playback has had time to populate its owned cache.
 * The analyzer can read committed cache spans but has no upstream/network boundary.
 */
internal class PlaybackCachedFingerprintOwner private constructor(
    private val mainHandler: Handler,
    private val currentTrackProvider: CurrentTrackProvider,
    private val taskScheduler: TaskScheduler,
    private val analyzer: CachedFingerprintAnalyzer
) {
    fun interface CurrentTrackProvider {
        fun currentTrack(): Track?
    }

    fun interface TaskScheduler {
        fun schedule(task: Runnable)
    }

    fun interface CachedFingerprintAnalyzer {
        fun analyze(track: Track): Boolean
    }

    constructor(
        context: Context,
        repository: MusicLibraryRepository,
        cachedMediaReader: PlaybackCachedMediaReader,
        mainHandler: Handler,
        currentTrackProvider: CurrentTrackProvider,
        taskScheduler: TaskScheduler
    ) : this(
        mainHandler,
        currentTrackProvider,
        taskScheduler,
        RepositoryCachedFingerprintAnalyzer(
            context.applicationContext,
            repository,
            cachedMediaReader
        )
    )

    internal constructor(
        mainHandler: Handler,
        currentTrackProvider: CurrentTrackProvider,
        taskScheduler: TaskScheduler,
        analyzer: CachedFingerprintAnalyzer,
        testing: Boolean
    ) : this(mainHandler, currentTrackProvider, taskScheduler, analyzer)

    private val generation = AtomicInteger()
    private var delayedTask: Runnable? = null
    @Volatile
    private var released = false

    fun schedule(track: Track?) {
        if (released || !isWebDavHttpTrack(track)) return
        val selected = track ?: return
        val selectedGeneration = generation.incrementAndGet()
        delayedTask?.let(mainHandler::removeCallbacks)
        val task = Runnable {
            if (!isCurrent(selectedGeneration, selected)) return@Runnable
            taskScheduler.schedule(Runnable {
                if (isCurrent(selectedGeneration, selected)) {
                    analyzer.analyze(selected)
                }
            })
        }
        delayedTask = task
        mainHandler.postDelayed(task, CACHE_SETTLE_DELAY_MS)
    }

    fun release() {
        released = true
        generation.incrementAndGet()
        delayedTask?.let(mainHandler::removeCallbacks)
        delayedTask = null
    }

    private fun isCurrent(expectedGeneration: Int, expectedTrack: Track): Boolean {
        if (released || generation.get() != expectedGeneration) return false
        val current = currentTrackProvider.currentTrack() ?: return false
        return current.id == expectedTrack.id && current.contentUri == expectedTrack.contentUri
    }

    private fun isWebDavHttpTrack(track: Track?): Boolean {
        if (track == null || !track.dataPath.startsWith("webdav:")) return false
        val scheme = track.contentUri?.scheme
        return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
    }

    private class RepositoryCachedFingerprintAnalyzer(
        context: Context,
        private val repository: MusicLibraryRepository,
        private val cachedMediaReader: PlaybackCachedMediaReader
    ) : CachedFingerprintAnalyzer {
        private val analyzer = ChromaprintSegmentAnalyzer(context)
        private val directory = File(context.cacheDir, "webdav-fingerprint")

        override fun analyze(track: Track): Boolean {
            val candidate = repository.loadPendingWebDavAudioFingerprintCandidate(track.id)
                ?: return false
            directory.mkdirs()
            val cachedPrefix = File(directory, "source-${candidate.sourceId}.media")
            return try {
                val copied = cachedMediaReader.copyCachedPrefix(
                    track,
                    cachedPrefix,
                    MINIMUM_CACHED_PREFIX_BYTES,
                    MAXIMUM_CACHED_PREFIX_BYTES
                )
                if (copied < MINIMUM_CACHED_PREFIX_BYTES) return false
                val evidence = analyzer.analyzeCachedHead(candidate, cachedPrefix)
                if (!repository.saveAudioFingerprint(candidate, evidence)) return false
                repository.refreshAudioVerifiedMatches(listOf(track.id))
                true
            } catch (error: RuntimeException) {
                Log.d(TAG, "Cached WebDAV fingerprint skipped", error)
                false
            } finally {
                cachedPrefix.delete()
            }
        }
    }

    private companion object {
        const val TAG = "CachedFingerprint"
        const val CACHE_SETTLE_DELAY_MS = 12_000L
        const val MINIMUM_CACHED_PREFIX_BYTES = 512L * 1024L
        const val MAXIMUM_CACHED_PREFIX_BYTES = 8L * 1024L * 1024L
    }
}
