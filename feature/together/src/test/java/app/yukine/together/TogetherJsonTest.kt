package app.yukine.together

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class TogetherJsonTest {
    @Test
    fun queueIncludesPublicDisplayMetadata() {
        val item = TogetherQueueItem(
            stableId = "7",
            title = "Title",
            artist = "Artist",
            sourceUri = "https://audio.example/7",
            album = "Album",
            artworkUri = "https://img.example/7.jpg",
            durationMs = 7_000L
        )

        val json = JSONArray(TogetherJson.queue(listOf(item))).getJSONObject(0)

        assertEquals("Album", json.getString("album"))
        assertEquals("https://img.example/7.jpg", json.getString("artwork_uri"))
        assertEquals(7_000L, json.getLong("duration_ms"))
    }

    @Test
    fun queueDoesNotPublishDeviceLocalArtworkUris() {
        val item = TogetherQueueItem(
            stableId = "8",
            title = "Local",
            artist = "Artist",
            sourceUri = "content://media/external/audio/8",
            artworkUri = "content://media/external/audio/albumart/8"
        )

        val json = JSONArray(TogetherJson.queue(listOf(item))).getJSONObject(0)

        assertEquals("", json.getString("artwork_uri"))
    }
}
