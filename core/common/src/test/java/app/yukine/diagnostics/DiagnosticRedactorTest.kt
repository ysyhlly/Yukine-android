package app.yukine.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    @Test
    fun removesCredentialsIdentifiersAndPrivatePaths() {
        val raw = "Authorization: Bearer abc\nCookie=session=xyz\naccess_token=qwerty " +
            "trackId=123 username='alice' /storage/emulated/0/Music/private.flac"

        val redacted = DiagnosticRedactor.redact(raw)

        listOf("abc", "xyz", "qwerty", "123", "alice", "private.flac").forEach {
            assertFalse(redacted.contains(it))
        }
        assertTrue(redacted.contains("<redacted>"))
        assertTrue(redacted.contains("<private-path>"))
    }

    @Test
    fun removesUnknownNetworkUrlQueryAndFragmentValues() {
        val raw = "primary=https://media.example/audio.flac?vuutv=signed-value&quality=lossless#session " +
            "fallback=rtsp://media.example/live#private-fragment"

        val redacted = DiagnosticRedactor.redact(raw)

        listOf("vuutv", "signed-value", "quality", "lossless", "session", "private-fragment").forEach {
            assertFalse(redacted.contains(it))
        }
        assertTrue(redacted.contains("https://media.example/audio.flac?<redacted-url-params>"))
        assertTrue(redacted.contains("rtsp://media.example/live#<redacted-url-fragment>"))
    }

    @Test
    fun leavesNetworkUrlsWithoutQueryOrFragmentReadable() {
        val raw = "host=https://media.example/audio.flac status=403"

        val redacted = DiagnosticRedactor.redact(raw)

        assertTrue(redacted.contains("https://media.example/audio.flac"))
        assertTrue(redacted.contains("status=403"))
    }

    @Test
    fun removesMediaTitlesPathsAndTrackMetadata() {
        val raw = "title=Private Song, dataPath=streaming:netease:123?lyrics=secret, " +
            "track=streaming:netease:456?album=private"

        val redacted = DiagnosticRedactor.redact(raw)

        listOf("Private Song", "netease", "lyrics", "album", "secret", "private").forEach {
            assertFalse(redacted.contains(it))
        }
        assertTrue(redacted.contains("title=<redacted>"))
        assertTrue(redacted.contains("dataPath=<redacted>"))
        assertTrue(redacted.contains("track=<redacted>"))
    }
}
