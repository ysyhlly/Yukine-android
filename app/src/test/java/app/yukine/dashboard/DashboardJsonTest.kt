package app.yukine.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardJsonTest {
    @Test
    fun playbackStateParsesMissingNullAndEmptyNullableStrings() {
        val missing = DashboardJson.parsePlaybackState(
            """
            {
              "isPlaying": false,
              "positionMs": 0,
              "durationMs": 0
            }
            """.trimIndent()
        )
        assertNull(missing.trackId)
        assertNull(missing.artworkUrl)

        val explicitNull = DashboardJson.parsePlaybackState(
            """
            {
              "isPlaying": true,
              "positionMs": 1000,
              "durationMs": 2000,
              "trackId": null,
              "title": null,
              "artist": null,
              "album": null,
              "artworkUrl": null
            }
            """.trimIndent()
        )
        assertNull(explicitNull.trackId)
        assertNull(explicitNull.title)
        assertNull(explicitNull.artist)
        assertNull(explicitNull.album)
        assertNull(explicitNull.artworkUrl)

        val emptyString = DashboardJson.parsePlaybackState(
            """
            {
              "isPlaying": true,
              "positionMs": 1000,
              "durationMs": 2000,
              "trackId": "",
              "title": "",
              "artist": "",
              "album": "",
              "artworkUrl": ""
            }
            """.trimIndent()
        )
        assertEquals("", emptyString.trackId)
        assertEquals("", emptyString.title)
        assertEquals("", emptyString.artist)
        assertEquals("", emptyString.album)
        assertEquals("", emptyString.artworkUrl)
    }

    @Test
    fun recentActivityParsesNullableArtworkUrlWithoutDroppingEmptyString() {
        val response = DashboardJson.parseRecentActivity(
            """
            {
              "items": [
                { "id": "missing", "title": "Missing", "subtitle": "", "timestamp": 1 },
                { "id": "null", "title": "Null", "subtitle": "", "artworkUrl": null, "timestamp": 2 },
                { "id": "empty", "title": "Empty", "subtitle": "", "artworkUrl": "", "timestamp": 3 }
              ]
            }
            """.trimIndent()
        )

        assertNull(response.items[0].artworkUrl)
        assertNull(response.items[1].artworkUrl)
        assertEquals("", response.items[2].artworkUrl)
    }
}
