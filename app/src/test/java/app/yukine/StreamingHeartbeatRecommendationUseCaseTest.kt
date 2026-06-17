package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingHeartbeatRecommendationUseCaseTest {
    @Test
    fun playlistPlaceholdersStartModeAndDedupeTracks() {
        val useCase = StreamingHeartbeatRecommendationUseCase()
        useCase.startLoading(StreamingProviderName.NETEASE)

        val placeholders = useCase.playlistPlaceholders(
            listOf(streamingTrack("1"), streamingTrack("1"), streamingTrack("2"))
        )

        assertEquals(listOf("streaming:netease:1", "streaming:netease:2"), placeholders.map { it.dataPath })
        assertTrue(useCase.accepts(StreamingProviderName.NETEASE))
        assertFalse(useCase.canContinueLoading(StreamingProviderName.NETEASE))
    }

    @Test
    fun prepareRefillRespectsModeRemainingAndCooldown() {
        var now = 1_000L
        val useCase = StreamingHeartbeatRecommendationUseCase(clockMs = { now }, refillRetryMs = 30_000L)
        useCase.startLoading(StreamingProviderName.NETEASE)
        useCase.playlistPlaceholders(listOf(streamingTrack("seed")))

        assertNull(useCase.prepareRefill(snapshot(currentIndex = 0, queueSize = 10)))
        now += 30_001L
        val refill = useCase.prepareRefill(snapshot(currentIndex = 4, queueSize = 6))
        val duplicateWhileLoading = useCase.prepareRefill(snapshot(currentIndex = 4, queueSize = 6))
        useCase.markLoadingFinished()
        val cooledDownDuplicate = useCase.prepareRefill(snapshot(currentIndex = 4, queueSize = 6))

        assertEquals(StreamingProviderName.NETEASE, refill?.provider)
        assertNull(duplicateWhileLoading)
        assertNull(cooledDownDuplicate)
    }

    @Test
    fun appendPlaceholdersSkipPreviouslySeenTracks() {
        val useCase = StreamingHeartbeatRecommendationUseCase()
        useCase.startLoading(StreamingProviderName.NETEASE)
        useCase.playlistPlaceholders(listOf(streamingTrack("1"), streamingTrack("2")))

        val appended = useCase.appendPlaceholders(listOf(streamingTrack("2"), streamingTrack("3")))

        assertEquals(listOf("streaming:netease:3"), appended.map { it.dataPath })
    }

    @Test
    fun stopClearsModeAndSeenTracks() {
        val useCase = StreamingHeartbeatRecommendationUseCase()
        useCase.startLoading(StreamingProviderName.NETEASE)
        useCase.playlistPlaceholders(listOf(streamingTrack("1")))

        useCase.stop()
        assertFalse(useCase.accepts(StreamingProviderName.NETEASE))
        val placeholders = useCase.playlistPlaceholders(listOf(streamingTrack("1")))

        assertEquals(listOf("streaming:netease:1"), placeholders.map { it.dataPath })
    }

    private fun streamingTrack(id: String): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = "Song $id",
            artist = "Artist"
        )

    private fun snapshot(currentIndex: Int, queueSize: Int): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            Track(1L, "Track", "Artist", "Album", 1000L, android.net.Uri.EMPTY, "local:1"),
            currentIndex,
            queueSize,
            0L,
            1000L,
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
