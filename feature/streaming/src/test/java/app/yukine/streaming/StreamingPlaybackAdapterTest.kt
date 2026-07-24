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
                url = "https://stream.qq.example/song.flac",
                codec = "flac",
                bitrate = 1_411,
                sampleRate = 96_000,
                bitDepth = 24,
                channelCount = 2
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
        assertEquals("flac", resolved.codec)
        assertEquals(1_411, resolved.bitrateKbps)
        assertEquals(96_000, resolved.sampleRateHz)
        assertEquals(24, resolved.bitsPerSample)
        assertEquals(2, resolved.channelCount)
    }

    @Test
    fun resolvedTrackCarriesPlaybackMimeTypeInItsPersistentDataPath() {
        val resolved = StreamingPlaybackAdapter.toTrack(
            source = StreamingPlaybackSource(
                provider = StreamingProviderName.BILIBILI,
                providerTrackId = "video:BV1TEST:cid:42",
                url = "https://cdn.example/audio.m4s",
                mimeType = "audio/mp4"
            )
        )

        assertEquals(
            "audio/mp4",
            app.yukine.common.StreamingDataPathMetadata.playbackMimeType(resolved.dataPath)
        )
        assertEquals(
            "video:BV1TEST:cid:42",
            StreamingPlaybackAdapter.providerTrackId(resolved.dataPath)
        )
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
        val musicInfo = """{"hash":"lx-hash","album_id":"22"}"""
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
                        provider = StreamingProviderName.LUOXUE,
                        quality = StreamingAudioQuality.LOSSLESS,
                        label = "LX/酷狗",
                        providerTrackId = "kg:lx-hash.22.0",
                        luoxueMusicInfoJson = musicInfo
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
                "luoxue:kg:lx-hash.22.0:lossless"
            ),
            candidates.map { candidate ->
                "${candidate.provider.wireName}:${candidate.providerTrackId}:${candidate.quality?.wireName.orEmpty()}"
            }
        )
        assertEquals("QQ 音乐", candidates[1].label)
        assertEquals(StreamingAudioQuality.HIGH, candidates[1].quality)
        assertEquals(normalizeLuoxueMusicInfoJson(musicInfo), candidates[2].luoxueMusicInfoJson)
    }
}
