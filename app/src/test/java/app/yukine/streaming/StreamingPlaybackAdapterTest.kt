package app.yukine.streaming

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

    @Test
    fun luoxueDataPathAliasesResolveToLuoxueProvider() {
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.providerName("streaming:lx:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.providerName("streaming:lx_music:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.providerName("streaming:luoxue:abc"))
        assertEquals("abc", StreamingPlaybackAdapter.providerTrackId("streaming:lx:abc"))
    }
}
