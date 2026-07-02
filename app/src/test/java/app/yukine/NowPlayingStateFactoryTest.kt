package app.yukine

import android.net.Uri
import app.yukine.model.LyricsLine
import app.yukine.model.Track
import app.yukine.playback.PlaybackRepeatMode
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingStateFactoryTest {
    @Test
    fun lyricsKeepLineTimesForTapToSeek() {
        val state = NowPlayingStateFactory.create(
            playbackState = snapshot(positionMs = 2_500L),
            lyrics = listOf(
                LyricsLine(1_000L, "first"),
                LyricsLine(12_000L, "second")
            ),
            lyricsStatus = "",
            lyricsOffsetMs = 0L
        )

        requireNotNull(state)
        assertEquals(2, state.lyrics.size)
        assertEquals(1_000L, state.lyrics[0].timeMs)
        assertEquals(12_000L, state.lyrics[1].timeMs)
        assertTrue(state.lyrics[0].active)
        assertFalse(state.lyrics[1].active)
    }

    private fun snapshot(positionMs: Long): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            Track(7L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:song.mp3"),
            0,
            1,
            positionMs,
            180_000L,
            true,
            false,
            "",
            false,
            PlaybackRepeatMode.REPEAT_ALL,
            1.0f,
            1.0f,
            0L
        )
}
