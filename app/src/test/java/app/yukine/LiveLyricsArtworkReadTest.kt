package app.yukine

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveLyricsArtworkReadTest {
    @Test
    fun boundedReaderAcceptsPayloadAtLimit() {
        val payload = ByteArray(32) { it.toByte() }

        assertArrayEquals(
            payload,
            readLiveLyricsArtworkBytes(ByteArrayInputStream(payload), payload.size)
        )
    }

    @Test
    fun boundedReaderRejectsPayloadPastLimit() {
        val payload = ByteArray(33) { it.toByte() }

        assertNull(readLiveLyricsArtworkBytes(ByteArrayInputStream(payload), 32))
    }
}
