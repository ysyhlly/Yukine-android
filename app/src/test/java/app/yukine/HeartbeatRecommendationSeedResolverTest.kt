package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatRecommendationSeedResolverTest {
    @Test
    fun collectsHeartbeatSeedSources() {
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
            },
            serviceSnapshotProvider = { snapshot(serviceTrack) },
            serviceQueueProvider = { listOf(serviceQueueTrack) },
            storeSnapshotProvider = { snapshot(storeTrack) },
            viewModelQueueProvider = { listOf(viewModelQueueTrack) },
            libraryContextProvider = { listOf(libraryTrack) }
        )

        val request = resolver.request(StreamingProviderName.NETEASE)

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

    @Test
    fun largeSeedQueuesAreBoundedAroundCurrentTrackBeforeMatching() {
        var serviceQueueIds = emptyList<Long>()
        var viewModelQueueIds = emptyList<Long>()
        val serviceQueue = (0 until 200).map { track(it.toLong()) }
        val viewModelQueue = (300 until 430).map { track(it.toLong()) }
        val libraryContext = (1000 until 1050).map { track(it.toLong()) }
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
                ): String = "seed"

                override fun heartbeatSeedCandidates(
                    serviceSnapshot: PlaybackStateSnapshot?,
                    serviceQueue: List<Track?>?,
                    storeSnapshot: PlaybackStateSnapshot?,
                    viewModelQueue: List<Track?>?
                ): List<Track> {
                    serviceQueueIds = serviceQueue.orEmpty().mapNotNull { it?.id }
                    viewModelQueueIds = viewModelQueue.orEmpty().mapNotNull { it?.id }
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
                ): String = ""
            }
        )

        resolver.request(
            provider = StreamingProviderName.NETEASE,
            serviceSnapshot = snapshot(serviceQueue[100], currentIndex = 100, queueSize = serviceQueue.size),
            serviceQueue = serviceQueue,
            storeSnapshot = snapshot(viewModelQueue[60], currentIndex = 60, queueSize = viewModelQueue.size),
            viewModelQueue = viewModelQueue,
            libraryContextTracks = libraryContext
        )

        assertEquals(64, serviceQueueIds.size)
        assertEquals(48, viewModelQueueIds.size)
        assertTrue(serviceQueueIds.containsAll(listOf(1000L, 94L, 100L, 106L, 0L)))
        assertTrue(viewModelQueueIds.containsAll(listOf(354L, 360L, 366L, 300L)))
        assertTrue(199L !in serviceQueueIds)
        assertTrue(1049L !in serviceQueueIds)
        assertTrue(429L !in viewModelQueueIds)
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private fun snapshot(
        track: Track,
        currentIndex: Int = 0,
        queueSize: Int = 1
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            currentIndex,
            queueSize,
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
