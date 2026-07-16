package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamingTrackMatchUseCaseTest {
    @Test
    fun directProviderTrackIdWinsBeforeCache() {
        val operations = FakeStreamingTrackMatchOperations()
        operations.loaded = "cached"
        val track = StreamingPlaybackAdapter.placeholderTrack(streamingTrack("123"))

        val result = StreamingTrackMatchUseCase(operations).providerTrackIdFor(
            track,
            StreamingProviderName.NETEASE
        )

        assertEquals("123", result)
        assertEquals(emptyList<String>(), operations.events)
    }

    @Test
    fun providerTrackIdFallsBackToSavedMatch() {
        val operations = FakeStreamingTrackMatchOperations()
        operations.loaded = "  cached-789  "
        val track = localTrack()

        val result = StreamingTrackMatchUseCase(operations).providerTrackIdFor(
            track,
            StreamingProviderName.NETEASE
        )

        assertEquals("cached-789", result)
        assertEquals(listOf("load:netease"), operations.events)
    }

    @Test
    fun providerTrackIdReadsPrimaryIdFromStoredLxVersionSet() {
        val operations = FakeStreamingTrackMatchOperations()
        operations.loaded = StoredStreamingSourceMatchCodec.encode(
            primary = StreamingTrack(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "wy:main",
                title = "Song",
                artist = "Artist"
            ),
            orderedCandidates = listOf(
                StreamingTrack(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = "tx:live",
                    title = "Song (Live)",
                    artist = "Artist"
                )
            )
        )

        val result = StreamingTrackMatchUseCase(operations).providerTrackIdFor(
            localTrack(),
            StreamingProviderName.LUOXUE
        )

        assertEquals("wy:main", result)
    }

    @Test
    fun noSourceStatusIsNotReturnedAsAPlayableTrackId() {
        val operations = FakeStreamingTrackMatchOperations()
        operations.loaded = STREAMING_NO_SOURCE_MATCH

        val result = StreamingTrackMatchUseCase(operations).providerTrackIdFor(
            localTrack(),
            StreamingProviderName.NETEASE
        )

        assertEquals("", result)
    }

    @Test
    fun providerTrackIdCanBeReadFromNeteaseLocations() {
        val useCase = StreamingTrackMatchUseCase(FakeStreamingTrackMatchOperations())
        val marker = localTrack(dataPath = "streaming:netease:12345")
        val query = localTrack(dataPath = "https://music.163.com/#/song?id=67890")
        val path = localTrack(dataPath = "https://music.163.com/song/24680/")

        assertEquals("12345", useCase.directProviderTrackId(marker, StreamingProviderName.NETEASE))
        assertEquals("67890", useCase.directProviderTrackId(query, StreamingProviderName.NETEASE))
        assertEquals("24680", useCase.directProviderTrackId(path, StreamingProviderName.NETEASE))
    }

    @Test
    fun providerTrackIdFromCandidatesUsesFirstDirectMatch() {
        val useCase = StreamingTrackMatchUseCase(FakeStreamingTrackMatchOperations())
        val result = useCase.providerTrackIdFromCandidates(
            listOf(
                localTrack(dataPath = "file:///music/local.flac"),
                localTrack(dataPath = "netease:song/13579")
            ),
            StreamingProviderName.NETEASE
        )

        assertEquals("13579", result)
    }

    @Test
    fun heartbeatSeedCandidatesAreDedupedByStableLocation() {
        val useCase = StreamingTrackMatchUseCase(FakeStreamingTrackMatchOperations())
        val candidates = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        useCase.addHeartbeatSeedCandidate(candidates, seen, localTrack(id = 1L, dataPath = "same"))
        useCase.addHeartbeatSeedCandidate(candidates, seen, localTrack(id = 2L, dataPath = "same"))
        useCase.addHeartbeatSeedCandidate(candidates, seen, localTrack(id = 3L, dataPath = "other"))

        assertEquals(listOf(1L, 3L), candidates.map { it.id })
    }

    @Test
    fun heartbeatSeedCandidatesPreferQueuesThenSnapshotsAndDedupe() {
        val useCase = StreamingTrackMatchUseCase(FakeStreamingTrackMatchOperations())
        val current = localTrack(id = 1L, dataPath = "current")
        val indexed = localTrack(id = 2L, dataPath = "indexed")
        val serviceOnly = localTrack(id = 3L, dataPath = "service-only")
        val storeCurrent = localTrack(id = 4L, dataPath = "store-current")
        val viewOnly = localTrack(id = 5L, dataPath = "view-only")
        val serviceQueue = listOf(serviceOnly, indexed, current)
        val viewModelQueue = listOf(indexed, viewOnly, storeCurrent)

        val result = useCase.heartbeatSeedCandidates(
            serviceSnapshot = snapshot(currentTrack = current, currentIndex = 1, queueSize = serviceQueue.size),
            serviceQueue = serviceQueue,
            storeSnapshot = snapshot(currentTrack = storeCurrent, currentIndex = 2, queueSize = viewModelQueue.size),
            viewModelQueue = viewModelQueue
        )

        assertEquals(listOf(3L, 2L, 1L, 5L, 4L), result.map { it.id })
    }

    @Test
    fun snapshotQueueForHeartbeatMergesRuntimeQueuesBeforeStoreCurrentTrack() {
        val useCase = StreamingTrackMatchUseCase(FakeStreamingTrackMatchOperations())
        val shared = localTrack(id = 1L, dataPath = "shared")
        val serviceOnly = localTrack(id = 2L, dataPath = "service-only")
        val viewOnly = localTrack(id = 3L, dataPath = "view-only")
        val storeCurrent = localTrack(id = 4L, dataPath = "store-current")

        val result = useCase.snapshotQueueForHeartbeat(
            serviceQueue = listOf(shared, serviceOnly),
            viewModelQueue = listOf(shared, viewOnly),
            storeSnapshot = snapshot(currentTrack = storeCurrent)
        )

        assertEquals(listOf(1L, 2L, 3L, 4L), result.map { it.id })
    }

    @Test
    fun heartbeatSeedMissMessageIncludesSnapshotStoreAndQueueContext() {
        val useCase = StreamingTrackMatchUseCase(FakeStreamingTrackMatchOperations())
        val snapshotTrack = localTrack(id = 1L, dataPath = "snapshot-path")
        val storeTrack = localTrack(id = 2L, dataPath = "store-path")
        val queueTrack = localTrack(id = 3L, dataPath = "queue-path")

        val message = useCase.heartbeatSeedMissMessage(
            provider = StreamingProviderName.NETEASE,
            snapshot = snapshot(currentTrack = snapshotTrack, currentIndex = 2, queueSize = 4),
            storeSnapshot = snapshot(currentTrack = storeTrack),
            queue = listOf(queueTrack)
        )

        assertTrue(message.contains("Heartbeat seed missing provider=netease"))
        assertTrue(message.contains("currentIndex=2"))
        assertTrue(message.contains("queueSize=1"))
        assertTrue(message.contains("snapshotDataPath=snapshot-path"))
        assertTrue(message.contains("storeDataPath=store-path"))
        assertTrue(message.contains("q0=queue-path|Local|Artist"))
    }

    @Test
    fun saveProviderTrackIdTrimsAndSkipsBlank() {
        val operations = FakeStreamingTrackMatchOperations()
        val useCase = StreamingTrackMatchUseCase(operations)
        val track = localTrack()

        useCase.saveProviderTrackId(track, StreamingProviderName.NETEASE, "  456  ")
        useCase.saveProviderTrackId(track, StreamingProviderName.NETEASE, "  ")

        assertEquals(listOf("save:netease|456"), operations.events)
    }

    private class FakeStreamingTrackMatchOperations : StreamingTrackMatchOperations {
        var loaded = ""
        val events = mutableListOf<String>()

        override fun loadStreamingTrackMatch(track: Track, provider: String): String {
            events.add("load:$provider")
            return loaded
        }

        override fun saveStreamingTrackMatch(track: Track, provider: String, providerTrackId: String) {
            events.add("save:$provider|$providerTrackId")
        }
    }

    private fun localTrack(
        id: Long = 1L,
        contentUri: Uri = Uri.EMPTY,
        dataPath: String = "local:1"
    ): Track = Track(id, "Local", "Artist", "Album", 1000L, contentUri, dataPath)

    private fun snapshot(
        currentTrack: Track?,
        currentIndex: Int = -1,
        queueSize: Int = 0
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            currentTrack,
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

    private fun streamingTrack(id: String): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = "Song $id",
            artist = "Artist"
        )
}
