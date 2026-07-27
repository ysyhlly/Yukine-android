package app.yukine.playback

import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProviderName
import app.yukine.together.TogetherQueueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTogetherStreamingResolverTest {
    @Test
    fun refreshReusesPreviouslyVerifiedLengthWhenProbeIsTemporarilyUnavailable() {
        val previous = source(sizeBytes = 12_345L, supportsRange = true)
        val refreshed = playbackSource(supportsRange = true)

        val resolved = resolvedTogetherSource(previous, refreshed, probedSizeBytes = 0L)

        assertEquals("https://refreshed.example/audio", resolved.resolvedUrl)
        assertEquals(12_345L, resolved.sizeBytes)
        assertTrue(resolved.supportsRange)
    }

    @Test
    fun unresolvedSourceWithoutVerifiedLengthFallsBackToLocalMaterialization() {
        val previous = source(sizeBytes = 0L, supportsRange = true)
        val refreshed = playbackSource(supportsRange = true)

        val resolved = resolvedTogetherSource(previous, refreshed, probedSizeBytes = 0L)

        assertEquals(0L, resolved.sizeBytes)
        assertFalse(resolved.supportsRange)
    }

    @Test
    fun knownRangeSizeIsReusedWithoutRequiringSuccessfulProbe() {
        val previous = source(sizeBytes = 99_999L, supportsRange = true)
        val refreshed = playbackSource(supportsRange = true)

        // When resolve() skips the network probe, probedSizeBytes is the known size.
        val resolved = resolvedTogetherSource(previous, refreshed, probedSizeBytes = previous.sizeBytes)

        assertEquals(99_999L, resolved.sizeBytes)
        assertTrue(resolved.supportsRange)
        assertEquals("https://refreshed.example/audio", resolved.resolvedUrl)
    }

    private fun source(
        sizeBytes: Long,
        supportsRange: Boolean
    ) = TogetherQueueSource.Streaming(
        provider = "luoxue",
        providerTrackId = "kw:123",
        quality = "lossless",
        resolvedUrl = "https://old.example/audio",
        supportsRange = supportsRange,
        sizeBytes = sizeBytes,
        luoxueMusicInfoJson = """{"id":"kw_123","name":"Ahead of Us"}"""
    )

    private fun playbackSource(supportsRange: Boolean) = StreamingPlaybackSource(
        provider = StreamingProviderName.LUOXUE,
        providerTrackId = "kw:123",
        url = "https://refreshed.example/audio",
        mimeType = "audio/flac",
        supportsRange = supportsRange
    )
}
