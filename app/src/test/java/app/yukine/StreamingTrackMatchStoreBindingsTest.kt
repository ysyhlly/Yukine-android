package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingTrackMatchStoreBindingsTest {
    @Test
    fun delegatesProviderTrackIdLoadingAndSaving() {
        val operations = FakeStreamingTrackMatchOperations(loaded = " cached-id ")
        val bindings = StreamingTrackMatchStoreBindings(StreamingTrackMatchUseCase(operations))
        val track = localTrack()

        val direct = bindings.directProviderTrackId(
            localTrack(dataPath = "streaming:netease:123"),
            StreamingProviderName.NETEASE
        )
        val loaded = bindings.providerTrackIdFor(track, StreamingProviderName.NETEASE)
        bindings.saveProviderTrackId(track, StreamingProviderName.NETEASE, " 456 ")

        assertEquals("123", direct)
        assertEquals("cached-id", loaded)
        assertEquals(listOf("load:1:netease", "save:1:netease:456"), operations.events)
    }

    @Test
    fun delegatesCandidateAndHeartbeatHelpers() {
        val bindings = StreamingTrackMatchStoreBindings(StreamingTrackMatchUseCase(FakeStreamingTrackMatchOperations()))
        val current = localTrack(id = 1L, dataPath = "current")
        val queued = localTrack(id = 2L, dataPath = "netease:song/2468")
        val snapshot = snapshot(current, 0, 1)

        val candidateId = bindings.providerTrackIdFromCandidates(listOf(queued), StreamingProviderName.NETEASE)
        val candidates = bindings.heartbeatSeedCandidates(snapshot, listOf(queued), snapshot, listOf(current))
        val queue = bindings.snapshotQueueForHeartbeat(listOf(queued), listOf(current), snapshot)
        val miss = bindings.heartbeatSeedMissMessage(StreamingProviderName.NETEASE, snapshot, snapshot, queue)

        assertEquals("2468", candidateId)
        assertEquals(listOf(2L, 1L), candidates.map { it.id })
        assertEquals(listOf(2L, 1L), queue.map { it.id })
        assertEquals(true, miss.contains("Heartbeat seed missing provider=netease"))
        assertEquals(true, miss.contains("queueSize=2"))
    }

    private class FakeStreamingTrackMatchOperations(
        private val loaded: String = ""
    ) : StreamingTrackMatchOperations {
        val events = mutableListOf<String>()

        override fun loadStreamingTrackMatch(track: Track, provider: String): String {
            events += "load:${track.id}:$provider"
            return loaded
        }

        override fun saveStreamingTrackMatch(track: Track, provider: String, providerTrackId: String) {
            events += "save:${track.id}:$provider:$providerTrackId"
        }
    }

    private fun localTrack(
        id: Long = 1L,
        dataPath: String = "file:$id.mp3"
    ): Track =
        Track(id, "Song $id", "Artist", "Album", 120_000L, null, dataPath)

    private fun snapshot(
        track: Track?,
        currentIndex: Int,
        queueSize: Int
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            currentIndex,
            queueSize,
            0L,
            0L,
            false,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )
}
