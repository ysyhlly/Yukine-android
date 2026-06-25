package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatRecommendationBindingsTest {
    @Test
    fun forwardsHeartbeatRecommendationEdges() {
        val calls = mutableListOf<String>()
        val bindings = HeartbeatRecommendationBindings(
            playbackServiceAvailability = QueuePlaybackServiceAvailability {
                calls += "service"
                true
            },
            seedRequestProvider = HeartbeatSeedRequestProvider { provider ->
                calls += "seed:${provider.wireName}"
                HeartbeatRecommendationSeedRequest(seedTrackId = "seed", playlistId = "seed")
            },
            heartbeatRecommendationStopper = QueueNoArgAction { calls += "stop" },
            queueAppender = HeartbeatQueueAppender { presentation ->
                calls += "append:${presentation.readyStatus}"
            },
            heartbeatPresentationPlayer = HeartbeatPresentationPlayer { presentation ->
                calls += "play:${presentation.tracks.size}:${presentation.emptyStatus}:${presentation.readyStatus}"
            },
            seedMissLogger = HeartbeatSeedMissLogger { request ->
                calls += "miss:${request.seedMissingMessage}"
            },
            statusSink = QueueStatusSink { status -> calls += "status:$status" }
        )

        bindings.hasPlaybackService()
        bindings.seedRequest(StreamingProviderName.NETEASE)
        bindings.stopHeartbeatRecommendationMode()
        bindings.appendToQueue(StreamingRecommendationPresentation(readyStatus = "Ready"))
        bindings.playHeartbeatRecommendation(StreamingRecommendationPresentation(emptyStatus = "Empty", readyStatus = "Playing"))
        bindings.logSeedMiss(HeartbeatRecommendationSeedRequest(seedMissingMessage = "Missing seed"))
        bindings.setStatus("Done")

        assertEquals(
            listOf(
                "service",
                "seed:netease",
                "stop",
                "append:Ready",
                "play:0:Empty:Playing",
                "miss:Missing seed",
                "status:Done"
            ),
            calls
        )
    }

    @Test
    fun seedResolverBindingsCollectHeartbeatSeedSources() {
        val calls = mutableListOf<String>()
        val serviceTrack = track(1L)
        val storeTrack = track(2L)
        val serviceQueueTrack = track(3L)
        val viewModelQueueTrack = track(4L)
        val libraryTrack = track(5L)
        val resolver = HeartbeatRecommendationSeedResolver(
            object : StreamingTrackMatchStore {
                override fun providerTrackIdFor(track: Track, provider: StreamingProviderName): String = ""

                override fun saveProviderTrackId(
                    track: Track,
                    provider: StreamingProviderName,
                    providerTrackId: String
                ) = Unit

                override fun providerTrackIdFromCandidates(
                    candidates: List<Track?>?,
                    provider: StreamingProviderName?
                ): String {
                    calls += "candidateIds:${candidates.orEmpty().mapNotNull { it?.id }}"
                    return "seed-${provider?.wireName}"
                }

                override fun heartbeatSeedCandidates(
                    serviceSnapshot: PlaybackStateSnapshot?,
                    serviceQueue: List<Track?>?,
                    storeSnapshot: PlaybackStateSnapshot?,
                    viewModelQueue: List<Track?>?
                ): List<Track> {
                    calls += "snapshots:${serviceSnapshot?.currentTrack?.id}:${storeSnapshot?.currentTrack?.id}"
                    calls += "serviceQueue:${serviceQueue.orEmpty().mapNotNull { it?.id }}"
                    calls += "viewModelQueue:${viewModelQueue.orEmpty().mapNotNull { it?.id }}"
                    return serviceQueue.orEmpty().filterNotNull()
                }

                override fun snapshotQueueForHeartbeat(
                    serviceQueue: List<Track?>?,
                    viewModelQueue: List<Track?>?,
                    storeSnapshot: PlaybackStateSnapshot?
                ): List<Track> = serviceQueue.orEmpty().filterNotNull()

                override fun heartbeatSeedMissMessage(
                    provider: StreamingProviderName?,
                    snapshot: PlaybackStateSnapshot?,
                    storeSnapshot: PlaybackStateSnapshot?,
                    queue: List<Track?>?
                ): String = "miss:${provider?.wireName}:${snapshot?.currentTrack?.id}:${queue.orEmpty().mapNotNull { it?.id }}"
            }
        )
        val bindings = HeartbeatRecommendationSeedResolverBindings(
            resolver = resolver,
            serviceSnapshotProvider = HeartbeatPlaybackSnapshotProvider { snapshot(serviceTrack) },
            serviceQueueProvider = HeartbeatQueueSnapshotProvider { listOf(serviceQueueTrack) },
            storeSnapshotProvider = HeartbeatPlaybackSnapshotProvider { snapshot(storeTrack) },
            viewModelQueueProvider = HeartbeatQueueSnapshotProvider { listOf(viewModelQueueTrack) },
            libraryContextProvider = HeartbeatQueueSnapshotProvider { listOf(libraryTrack) }
        )

        val request = bindings.request(StreamingProviderName.NETEASE)

        assertEquals("seed-netease", request.seedTrackId)
        assertEquals("seed-netease", request.playlistId)
        assertEquals("miss:netease:1:[5, 3]", request.seedMissingMessage)
        assertEquals(
            listOf(
                "snapshots:1:2",
                "serviceQueue:[5, 3]",
                "viewModelQueue:[4]"
            ),
            calls.take(3)
        )
        assertTrue(calls.last() == "candidateIds:[5, 3]" || calls.last() == "candidateIds:[3, 5]")
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private fun snapshot(track: Track): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            0,
            1,
            0L,
            track.durationMs,
            true,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )
}
