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
}
