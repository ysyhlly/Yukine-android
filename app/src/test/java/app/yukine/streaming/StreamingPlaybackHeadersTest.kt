package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingPlaybackHeadersTest {
    @Test
    fun headerStoreCopiesHeadersAndRemovesEmptyRegistrations() {
        val dataPath = "streaming:test:headers-${System.nanoTime()}"
        val mutableHeaders = linkedMapOf("Authorization" to "Bearer first")

        StreamingPlaybackHeaders.register(dataPath, mutableHeaders)
        mutableHeaders["Authorization"] = "Bearer changed"

        assertEquals(mapOf("Authorization" to "Bearer first"), StreamingPlaybackHeaders.forDataPath(dataPath))

        StreamingPlaybackHeaders.register(dataPath, emptyMap())

        assertEquals(emptyMap<String, String>(), StreamingPlaybackHeaders.forDataPath(dataPath))
    }

    @Test
    fun headerStoreIgnoresBlankDataPath() {
        StreamingPlaybackHeaders.register("", mapOf("Cookie" to "session=1"))

        assertEquals(emptyMap<String, String>(), StreamingPlaybackHeaders.forDataPath(""))
        assertEquals(emptyMap<String, String>(), StreamingPlaybackHeaders.forDataPath(null))
    }
}
