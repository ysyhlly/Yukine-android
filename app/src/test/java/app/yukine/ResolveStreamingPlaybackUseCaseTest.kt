package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveStreamingPlaybackUseCaseTest {
    @Test
    fun prepareReturnsRequestForUnresolvedStreamingPlaceholder() {
        val placeholder = StreamingPlaybackAdapter.placeholderTrack(streamingTrack("123"))
        val request = ResolveStreamingPlaybackUseCase().prepare(listOf(localTrack(1), placeholder), 5)

        requireNotNull(request)
        assertEquals(1, request.index)
        assertEquals(StreamingProviderName.NETEASE, request.provider)
        assertEquals("123", request.providerTrackId)
        assertEquals("Song 123", request.metadata?.title)
        assertEquals("Artist", request.metadata?.artist)
    }

    @Test
    fun prepareIgnoresNonStreamingOrResolvedTracks() {
        val useCase = ResolveStreamingPlaybackUseCase()

        assertNull(useCase.prepare(emptyList(), 0))
        assertNull(useCase.prepare(listOf(localTrack(1)), 0))
    }

    @Test
    fun replaceResolvedTrackOnlyReplacesPreparedIndex() {
        val useCase = ResolveStreamingPlaybackUseCase()
        val first = localTrack(1)
        val placeholder = StreamingPlaybackAdapter.placeholderTrack(streamingTrack("123"))
        val request = requireNotNull(useCase.prepare(listOf(first, placeholder), 1))
        val resolved = localTrack(3)

        val replaced = useCase.replaceResolvedTrack(request, resolved)

        assertEquals(listOf(1L, 3L), replaced.map { it.id })
    }

    @Test
    fun prepareNextPreResolveSelectsUnresolvedNextTrackAndThrottlesDuplicate() {
        var now = 1_000L
        val useCase = ResolveStreamingPlaybackUseCase(clockMs = { now })
        val current = localTrack(10)
        val next = StreamingPlaybackAdapter.placeholderTrack(streamingTrack("next"))
        val snapshot = snapshot(current, currentIndex = 0, queueSize = 2, positionMs = 1_000L, durationMs = 100_000L)

        val request = useCase.prepareNextPreResolve(snapshot, listOf(current, next))
        val duplicate = useCase.prepareNextPreResolve(snapshot, listOf(current, next))
        useCase.clearPreResolve(request?.key)
        val cooledDownDuplicate = useCase.prepareNextPreResolve(snapshot, listOf(current, next))
        now += 121_000L
        val retriedSnapshot = snapshot(localTrack(11), currentIndex = 0, queueSize = 2, positionMs = 1_000L, durationMs = 100_000L)
        val retried = useCase.prepareNextPreResolve(retriedSnapshot, listOf(localTrack(11), next))

        requireNotNull(request)
        assertEquals("netease:next", request.key)
        assertEquals(next.id, request.oldTrackId)
        assertNull(duplicate)
        assertNull(cooledDownDuplicate)
        assertEquals("next", retried?.providerTrackId)
    }

    @Test
    fun prepareRecoveryDowngradesResolvedStreamingTrackAndThrottlesDuplicate() {
        var now = 2_000L
        val useCase = ResolveStreamingPlaybackUseCase(
            clockMs = { now },
            unresolvedStreamingTrack = { false }
        )
        val current = resolvedStreamingTrack("song-1")
        val snapshot = snapshot(current, currentIndex = 0, queueSize = 1)

        val request = useCase.prepareRecovery(
            snapshot,
            selectedQuality = StreamingAudioQuality.HIRES,
            adaptiveQuality = StreamingAudioQuality.HIRES
        )
        val duplicate = useCase.prepareRecovery(snapshot, StreamingAudioQuality.HIRES, StreamingAudioQuality.HIRES)
        useCase.clearRecovery(request?.key)
        val cooledDownDuplicate = useCase.prepareRecovery(snapshot, StreamingAudioQuality.HIRES, StreamingAudioQuality.HIRES)
        now += 21_000L
        val retried = useCase.prepareRecovery(snapshot, StreamingAudioQuality.HIRES, StreamingAudioQuality.HIRES)

        requireNotNull(request)
        assertEquals(StreamingAudioQuality.LOSSLESS, request.quality)
        assertEquals("netease:song-1:LOSSLESS", request.key)
        assertNull(duplicate)
        assertNull(cooledDownDuplicate)
        assertEquals(StreamingAudioQuality.LOSSLESS, retried?.quality)
    }

    @Test
    fun recoveryQualityStepsDownTowardStandard() {
        val useCase = ResolveStreamingPlaybackUseCase()

        assertEquals(StreamingAudioQuality.LOSSLESS, useCase.recoveryQuality(StreamingAudioQuality.HIRES))
        assertEquals(StreamingAudioQuality.HIGH, useCase.recoveryQuality(StreamingAudioQuality.LOSSLESS))
        assertEquals(StreamingAudioQuality.STANDARD, useCase.recoveryQuality(StreamingAudioQuality.HIGH))
        assertEquals(StreamingAudioQuality.STANDARD, useCase.recoveryQuality(StreamingAudioQuality.STANDARD))
    }

    private fun localTrack(id: Long): Track =
        Track(id, "Local $id", "Artist", "Album", 1000L, Uri.EMPTY, "local:$id")

    private fun resolvedStreamingTrack(id: String): Track =
        Track(
            id.hashCode().toLong(),
            "Resolved $id",
            "Artist",
            "Album",
            1000L,
            Uri.EMPTY,
            "streaming:netease:$id"
        )

    private fun streamingTrack(id: String): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            durationMs = 3000L
        )

    private fun snapshot(
        track: Track,
        currentIndex: Int,
        queueSize: Int,
        positionMs: Long = 0L,
        durationMs: Long = 1000L
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            currentIndex,
            queueSize,
            positionMs,
            durationMs,
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
