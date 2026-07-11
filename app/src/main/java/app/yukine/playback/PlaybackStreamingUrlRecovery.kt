package app.yukine.playback

import app.yukine.StreamingRepositorySource
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/** Resolves an expired current streaming URL inside the playback service process. */
internal class PlaybackStreamingUrlRecovery(
    private val repositorySource: StreamingRepositorySource,
    private val backgroundScheduler: BackgroundScheduler,
    private val mainPoster: MainPoster,
    private val resolvedSink: ResolvedSink,
    private val failureSink: FailureSink
) {
    fun interface BackgroundScheduler {
        fun schedule(task: Runnable)
    }

    fun interface MainPoster {
        fun post(task: Runnable): Boolean
    }

    fun interface ResolvedSink {
        fun onResolved(expectedTrackId: Long, track: Track, positionMs: Long)
    }

    fun interface FailureSink {
        fun onFailure(expectedTrackId: Long)
    }

    private val generation = AtomicInteger()

    fun refresh(track: Track?, positionMs: Long): Boolean {
        val current = track ?: return false
        val provider = StreamingPlaybackAdapter.streamingProviderName(current.dataPath) ?: return false
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(current.dataPath)
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: return false
        val quality = StreamingAudioQuality.fromWireName(
            StreamingDataPathMetadata.quality(current.dataPath)
        ) ?: StreamingAudioQuality.LOSSLESS
        val metadata = StreamingTrack(
            provider = provider,
            providerTrackId = providerTrackId,
            title = current.title,
            artist = current.artist,
            album = current.album,
            durationMs = current.durationMs,
            coverUrl = current.albumArtUriString().takeIf { it.isNotBlank() },
            playbackCandidates = StreamingPlaybackAdapter.playbackCandidates(current).drop(1),
            luoxueMusicInfoJson = StreamingPlaybackAdapter.luoxueMusicInfoJson(current.dataPath)
        )
        val requestGeneration = generation.incrementAndGet()
        return runCatching {
            backgroundScheduler.schedule(Runnable {
                val resolved = runCatching {
                    runBlocking {
                        repositorySource.current().resolvePlaybackTrack(
                            provider = provider,
                            providerTrackId = providerTrackId,
                            quality = quality,
                            metadata = metadata,
                            forceRefresh = true
                        ).track
                    }
                }
                mainPoster.post(Runnable {
                    if (requestGeneration != generation.get()) {
                        return@Runnable
                    }
                    resolved.fold(
                        onSuccess = { refreshed ->
                            resolvedSink.onResolved(current.id, refreshed, positionMs.coerceAtLeast(0L))
                        },
                        onFailure = {
                            failureSink.onFailure(current.id)
                        }
                    )
                })
            })
        }.isSuccess
    }
}
