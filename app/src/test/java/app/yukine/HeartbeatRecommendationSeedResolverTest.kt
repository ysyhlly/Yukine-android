package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatRecommendationSeedResolverTest {
    @Test
    fun requestCollectsCandidatesAndDirectSeed() {
        val serviceTrack = localTrack(id = 10L)
        val queuedTrack = localTrack(id = 11L)
        val playlistTrack = localTrack(id = 12L)
        val store = FakeStreamingTrackMatchStore().apply {
            heartbeatCandidates = listOf(serviceTrack, queuedTrack)
            heartbeatQueueSnapshot = listOf(serviceTrack, queuedTrack)
            providerTrackIdFromCandidateResult = " seed-11 "
            heartbeatSeedMissLogMessage = "Heartbeat seed missing provider=netease"
        }
        val resolver = HeartbeatRecommendationSeedResolver(store)

        val request = resolver.request(
            StreamingProviderName.NETEASE,
            playbackSnapshot(serviceTrack, 0, 2, true),
            listOf(serviceTrack),
            playbackSnapshot(queuedTrack, 1, 2, true),
            listOf(queuedTrack),
            listOf(playlistTrack)
        )

        assertEquals(setOf(serviceTrack.id, queuedTrack.id), request.candidates.map { it.id }.toSet())
        assertEquals(2, request.candidates.size)
        assertEquals("seed-11", request.seedTrackId)
        assertEquals("seed-11", request.playlistId)
        assertEquals("Heartbeat seed missing provider=netease", request.seedMissingMessage)
        assertEquals(listOf(12L, 10L), store.lastHeartbeatCandidateServiceQueue?.mapNotNull { it?.id })
        assertEquals(listOf(12L, 10L), store.lastHeartbeatQueueServiceQueue?.mapNotNull { it?.id })
        assertTrue(request.hasSeed)
        assertTrue(request.hasCandidates)
    }

    @Test
    fun requestVariesFirstCandidateAcrossClicks() {
        val candidates = (1L..8L).map { id -> localTrack(id = id) }
        val store = FakeStreamingTrackMatchStore().apply {
            heartbeatCandidates = candidates
            heartbeatQueueSnapshot = candidates
            providerTrackIdFromCandidateResult = "seed"
        }
        val resolver = HeartbeatRecommendationSeedResolver(store)

        val firstCandidateIds = (1..24).mapNotNull {
            resolver.request(
                StreamingProviderName.NETEASE,
                null,
                emptyList(),
                null,
                emptyList()
            ).candidates.firstOrNull()?.id
        }.toSet()

        assertTrue(firstCandidateIds.size > 1)
    }

    private class FakeStreamingTrackMatchStore : StreamingTrackMatchStore {
        var providerTrackIdFromCandidateResult: String = ""
        var heartbeatCandidates: List<Track> = emptyList()
        var heartbeatQueueSnapshot: List<Track> = emptyList()
        var heartbeatSeedMissLogMessage: String = ""
        var lastHeartbeatCandidateServiceQueue: List<Track?>? = null
        var lastHeartbeatQueueServiceQueue: List<Track?>? = null

        override fun providerTrackIdFor(track: Track, provider: StreamingProviderName): String = ""

        override fun saveProviderTrackId(
            track: Track,
            provider: StreamingProviderName,
            providerTrackId: String
        ) = Unit

        override fun providerTrackIdFromCandidates(
            candidates: List<Track?>?,
            provider: StreamingProviderName?
        ): String = providerTrackIdFromCandidateResult

        override fun heartbeatSeedCandidates(
            serviceSnapshot: PlaybackStateSnapshot?,
            serviceQueue: List<Track?>?,
            storeSnapshot: PlaybackStateSnapshot?,
            viewModelQueue: List<Track?>?
        ): List<Track> {
            lastHeartbeatCandidateServiceQueue = serviceQueue
            return heartbeatCandidates
        }

        override fun snapshotQueueForHeartbeat(
            serviceQueue: List<Track?>?,
            viewModelQueue: List<Track?>?,
            storeSnapshot: PlaybackStateSnapshot?
        ): List<Track> {
            lastHeartbeatQueueServiceQueue = serviceQueue
            return heartbeatQueueSnapshot
        }

        override fun heartbeatSeedMissMessage(
            provider: StreamingProviderName?,
            snapshot: PlaybackStateSnapshot?,
            storeSnapshot: PlaybackStateSnapshot?,
            queue: List<Track?>?
        ): String = heartbeatSeedMissLogMessage
    }

    private fun localTrack(id: Long): Track =
        Track(id, "Local $id", "Artist", "Album", 1000L, android.net.Uri.EMPTY, "file:$id")

    private fun playbackSnapshot(
        track: Track,
        index: Int,
        queueSize: Int,
        playing: Boolean
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            index,
            queueSize,
            0L,
            track.durationMs,
            playing,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )
}
