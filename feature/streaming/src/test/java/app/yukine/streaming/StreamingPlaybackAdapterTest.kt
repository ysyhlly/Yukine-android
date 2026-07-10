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
        assertEquals(StreamingProviderName.NETEASE, StreamingPlaybackAdapter.streamingProviderName(placeholder.dataPath))
        assertEquals("123456", StreamingPlaybackAdapter.providerTrackId(placeholder.dataPath))
    }

    @Test
    fun luoxueDataPathAliasesResolveToLuoxueProvider() {
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.streamingProviderName("streaming:lx:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.streamingProviderName("streaming:lx_music:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.streamingProviderName("streaming:luoxue:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.streamingProviderName("streaming:kw:abc"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingPlaybackAdapter.streamingProviderName("streaming:mg:abc"))
        assertEquals("abc", StreamingPlaybackAdapter.providerTrackId("streaming:lx:abc"))
    }

    @Test
    fun qqMusicPlaceholderPreservesMediaMidTailInProviderTrackId() {
        val track = StreamingTrack(
            provider = StreamingProviderName.QQ_MUSIC,
            providerTrackId = "songMid123|mediaMid456",
            title = "Song",
            artist = "Artist",
            album = "Album"
        )

        val placeholder = StreamingPlaybackAdapter.placeholderTrack(track)

        // 必须保留 "|mediaMid" 尾部，否则 QQ 回放会用错 mediaMid 导致解析失败被跳过。
        assertEquals(
            "songMid123|mediaMid456",
            StreamingPlaybackAdapter.providerTrackId(placeholder.dataPath)
        )
        assertEquals(StreamingProviderName.QQ_MUSIC, StreamingPlaybackAdapter.streamingProviderName(placeholder.dataPath))
    }

    @Test
    fun resolvedTrackRetainsStreamingAlbumMetadata() {
        val resolved = StreamingPlaybackAdapter.toTrack(
            source = StreamingPlaybackSource(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                url = "https://stream.qq.example/song.mp3"
            ),
            metadata = StreamingTrack(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                title = "Song",
                artist = "Artist",
                album = "Album"
            )
        )

        assertEquals("Album", resolved.album)
    }

    @Test
    fun luoxueProviderTrackIdPreservesSourcePrefixColon() {
        // 洛雪音源的 providerTrackId 形如 "kw:12345"，冒号是 ID 的一部分，不能被截断。
        val track = StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = "kw:12345",
            title = "Song",
            artist = "Artist"
        )

        val placeholder = StreamingPlaybackAdapter.placeholderTrack(track)

        assertEquals("kw:12345", StreamingPlaybackAdapter.providerTrackId(placeholder.dataPath))
    }
}
