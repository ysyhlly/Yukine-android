package app.yukine.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackWaveformCacheTest {
    @Test
    fun localWaveformFallbackRejectsStreamingAndRemoteUris() {
        assertFalse(PlaybackWaveformCache.canRead("streaming:netease:1", null, 240_000L, 96))
        assertFalse(PlaybackWaveformCache.canRead("webdav:source:1", null, 240_000L, 96))
        assertFalse(PlaybackWaveformCache.canRead("", "https://example.test/audio.mp3", 240_000L, 96))
        assertFalse(PlaybackWaveformCache.canRead("", "http://example.test/audio.mp3", 240_000L, 96))
    }

    @Test
    fun localWaveformFallbackAllowsLocalFileSources() {
        val file = File("song.mp3").absoluteFile

        assertTrue(PlaybackWaveformCache.canRead(file.path, null, 240_000L, 96))
        assertTrue(PlaybackWaveformCache.canRead("", file.toURI().toString(), 240_000L, 96))
    }
}
