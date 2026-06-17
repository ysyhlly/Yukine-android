package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingPlaylistLinkParserTest {

    @Test
    fun parsesNeteaseShareLink() {
        val ref = StreamingPlaylistLinkParser.parse(
            "https://music.163.com/#/playlist?id=123456",
            StreamingProviderName.MOCK
        )
        assertEquals(StreamingProviderName.NETEASE, ref?.provider)
        assertEquals("123456", ref?.providerPlaylistId)
    }

    @Test
    fun parsesSpotifyUri() {
        val ref = StreamingPlaylistLinkParser.parse(
            "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            StreamingProviderName.MOCK
        )
        assertEquals(StreamingProviderName.SPOTIFY, ref?.provider)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", ref?.providerPlaylistId)
    }

    @Test
    fun parsesSpotifyOpenLink() {
        val ref = StreamingPlaylistLinkParser.parse(
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc",
            StreamingProviderName.MOCK
        )
        assertEquals(StreamingProviderName.SPOTIFY, ref?.provider)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", ref?.providerPlaylistId)
    }

    @Test
    fun parsesYouTubePlaylistParam() {
        val ref = StreamingPlaylistLinkParser.parse(
            "https://www.youtube.com/playlist?list=PLabc123",
            StreamingProviderName.MOCK
        )
        assertEquals(StreamingProviderName.YOUTUBE, ref?.provider)
        assertEquals("PLabc123", ref?.providerPlaylistId)
    }

    @Test
    fun bareIdUsesFallbackProvider() {
        val ref = StreamingPlaylistLinkParser.parse("987654", StreamingProviderName.QQ_MUSIC)
        assertEquals(StreamingProviderName.QQ_MUSIC, ref?.provider)
        assertEquals("987654", ref?.providerPlaylistId)
    }

    @Test
    fun blankInputReturnsNull() {
        assertNull(StreamingPlaylistLinkParser.parse("   ", StreamingProviderName.MOCK))
        assertNull(StreamingPlaylistLinkParser.parse(null, StreamingProviderName.MOCK))
    }
}
