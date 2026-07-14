package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamingResolvedPlaybackResumeTest {
    @Test
    fun pausedResolvedStreamResumesWithoutResolvingItsUrlAgain() {
        val resolved = StreamingPlaybackAdapter.toTrack(
            StreamingPlaybackSource(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "resolved",
                url = "https://audio.example.test/resolved.mp3"
            )
        )
        val snapshot = PlaybackStateSnapshot(
            resolved,
            0,
            1,
            12_000L,
            180_000L,
            false,
            false,
            "",
            false,
            1,
            1.0f,
            1.0f,
            0L
        )

        assertFalse(StreamingPlaybackAdapter.isUnresolvedStreamingTrack(resolved))
        assertNull(
            StreamingViewModel().playbackResolution.prepareCurrentStreamingQueueResolveTarget(
                snapshot = snapshot,
                queue = listOf(resolved)
            )
        )
    }
}
