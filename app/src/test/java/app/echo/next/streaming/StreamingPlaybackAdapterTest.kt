package app.echo.next.streaming

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingPlaybackAdapterTest {
    @Test
    fun neteasePlaceholderCarriesSongIdInDataPath() {
        val track = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "123456",
            title = "Song",
            artist = "Artist"
        )

        val placeholder = StreamingPlaybackAdapter.placeholderTrack(track)

        assertEquals("streaming:netease:123456", placeholder.dataPath)
        assertEquals(StreamingProviderName.NETEASE, StreamingPlaybackAdapter.providerName(placeholder.dataPath))
        assertEquals("123456", StreamingPlaybackAdapter.providerTrackId(placeholder.dataPath))
    }
}
