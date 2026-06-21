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
            artist = "Artist",
            description = "Intro",
            lyricSources = listOf(StreamingLyricSource(StreamingProviderName.NETEASE, "网易云歌词", "123456")),
            playbackCandidates = listOf(StreamingPlaybackCandidate(StreamingProviderName.NETEASE, StreamingAudioQuality.HIGH, "网易云播放源"))
        )

        val placeholder = StreamingPlaybackAdapter.placeholderTrack(track)

        org.junit.Assert.assertTrue(placeholder.dataPath.startsWith("streaming:netease:123456?"))
        assertEquals(StreamingProviderName.NETEASE, StreamingPlaybackAdapter.providerName(placeholder.dataPath))
        assertEquals("123456", StreamingPlaybackAdapter.providerTrackId(placeholder.dataPath))
    }

    @Test
    fun luoxueDataPathAliasesResolveToLuoxueProvider() {
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.providerName("streaming:lx:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.providerName("streaming:lx_music:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.providerName("streaming:luoxue:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.providerName("streaming:kw:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.providerName("streaming:mg:abc"))
        assertEquals("abc", StreamingPlaybackAdapter.providerTrackId("streaming:lx:abc"))
    }
}
