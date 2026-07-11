package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun luoxueMusicInfoSurvivesDataPathRoundTripWithoutBecomingCacheIdentityText() {
        val musicInfo = """{"hash":"abc123","album_id":"22","nested":{"label":"完整信息"},"list":[1,2]}"""
        val placeholder = StreamingPlaybackAdapter.placeholderTrack(
            StreamingTrack(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:abc123.22.33",
                title = "Song",
                artist = "Artist",
                luoxueMusicInfoJson = musicInfo
            )
        )

        assertEquals(
            normalizeLuoxueMusicInfoJson(musicInfo),
            StreamingPlaybackAdapter.luoxueMusicInfoJson(placeholder.dataPath)
        )
        val cacheIdentity = app.yukine.common.StreamingDataPathMetadata.cacheIdentity(placeholder.dataPath)
        assertFalse(cacheIdentity.orEmpty().contains("完整信息"))
        org.junit.Assert.assertTrue(cacheIdentity.orEmpty().contains("lxmiHash="))
    }

    @Test
    fun invalidLuoxueMusicInfoFallsBackWithoutWritingPayload() {
        val placeholder = StreamingPlaybackAdapter.placeholderTrack(
            StreamingTrack(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kw:12345",
                title = "Song",
                artist = "Artist",
                luoxueMusicInfoJson = "not json"
            )
        )

        assertNull(StreamingPlaybackAdapter.luoxueMusicInfoJson(placeholder.dataPath))
        assertFalse(placeholder.dataPath.contains("lxmi="))
    }

    @Test
    fun playbackCandidatesDecodePrimaryAndAlternatesWithoutDuplicates() {
        val placeholder = StreamingPlaybackAdapter.placeholderTrack(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "netease-1",
                title = "Song",
                artist = "Artist",
                playbackCandidates = listOf(
                    StreamingPlaybackCandidate(
                        provider = StreamingProviderName.QQ_MUSIC,
                        quality = StreamingAudioQuality.HIGH,
                        label = "QQ 音乐",
                        providerTrackId = "qq-1"
                    ),
                    StreamingPlaybackCandidate(
                        provider = StreamingProviderName.KUGOU,
                        quality = StreamingAudioQuality.LOSSLESS,
                        label = "酷狗音乐",
                        providerTrackId = "kugou-1"
                    ),
                    StreamingPlaybackCandidate(
                        provider = StreamingProviderName.QQ_MUSIC,
                        quality = StreamingAudioQuality.HIGH,
                        label = "重复 QQ",
                        providerTrackId = "qq-1"
                    )
                )
            )
        )

        val candidates = StreamingPlaybackAdapter.playbackCandidates(placeholder)

        assertEquals(
            listOf(
                "netease:netease-1:",
                "qqmusic:qq-1:high",
                "kugou:kugou-1:lossless"
            ),
            candidates.map { candidate ->
                "${candidate.provider.wireName}:${candidate.providerTrackId}:${candidate.quality?.wireName.orEmpty()}"
            }
        )
        assertEquals("QQ 音乐", candidates[1].label)
        assertEquals(StreamingAudioQuality.HIGH, candidates[1].quality)
    }
}
