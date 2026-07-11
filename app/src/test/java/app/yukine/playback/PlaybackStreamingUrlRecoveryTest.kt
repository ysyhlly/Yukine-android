package app.yukine.playback

import android.net.Uri
import app.yukine.EmptyStreamingRepositorySource
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamingUrlRecoveryTest {
    @Test
    fun serviceRecoveryResolvesAndReturnsFreshTrackWithoutActivity() {
        val resolved = mutableListOf<Triple<Long, Track, Long>>()
        val failures = mutableListOf<Long>()
        val recovery = PlaybackStreamingUrlRecovery(
            EmptyStreamingRepositorySource,
            PlaybackStreamingUrlRecovery.BackgroundScheduler(Runnable::run),
            PlaybackStreamingUrlRecovery.MainPoster { task -> task.run(); true },
            PlaybackStreamingUrlRecovery.ResolvedSink { expectedId, track, positionMs ->
                resolved += Triple(expectedId, track, positionMs)
            },
            PlaybackStreamingUrlRecovery.FailureSink(failures::add)
        )
        val expired = Track(
            77L,
            "Expired",
            "Artist",
            "Album",
            120_000L,
            Uri.parse("https://expired.example.test/song.mp3"),
            "streaming:mock:mock-track-echo"
        )

        assertTrue(recovery.refresh(expired, 12_345L))

        assertEquals(listOf(77L), resolved.map { it.first })
        assertEquals(listOf(12_345L), resolved.map { it.third })
        assertTrue(resolved.single().second.dataPath.startsWith("streaming:mock:mock-track-echo"))
        assertEquals(emptyList<Long>(), failures)
    }

    @Test
    fun serviceRecoveryRejectsNonStreamingTrack() {
        val recovery = PlaybackStreamingUrlRecovery(
            EmptyStreamingRepositorySource,
            PlaybackStreamingUrlRecovery.BackgroundScheduler(Runnable::run),
            PlaybackStreamingUrlRecovery.MainPoster { task -> task.run(); true },
            PlaybackStreamingUrlRecovery.ResolvedSink { _, _, _ -> },
            PlaybackStreamingUrlRecovery.FailureSink { }
        )
        val local = Track(1L, "Local", "Artist", "Album", 1_000L, Uri.EMPTY, "file:local")

        assertFalse(recovery.refresh(local, 0L))
    }
}
