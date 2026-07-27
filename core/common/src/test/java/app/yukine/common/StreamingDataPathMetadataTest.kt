package app.yukine.common

import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

class StreamingDataPathMetadataTest {
    @Test
    fun streamingTrackRequiresStreamingPrefix() {
        assertTrue(StreamingDataPathMetadata.isStreamingTrack("streaming:netease:track-1"))
        assertFalse(StreamingDataPathMetadata.isStreamingTrack(null))
        assertFalse(StreamingDataPathMetadata.isStreamingTrack(""))
        assertFalse(StreamingDataPathMetadata.isStreamingTrack("file:///music/track.mp3"))
    }

    @Test
    fun providerAndTrackIdReadStreamingDataPath() {
        val dataPath = "streaming:163_music:track-1?quality=lossless#fragment"

        assertEquals(StreamingProviderName.NETEASE, StreamingDataPathMetadata.provider(dataPath))
        assertEquals("netease", StreamingDataPathMetadata.providerName(dataPath))
        assertEquals("track-1", StreamingDataPathMetadata.providerTrackId(dataPath))
    }

    @Test
    fun providerTrackIdPreservesQqCompoundTrackId() {
        val dataPath = "streaming:qq-music:songMid|mediaMid?sourceOptions=[]"

        assertEquals(StreamingProviderName.QQ_MUSIC, StreamingDataPathMetadata.provider(dataPath))
        assertEquals("songMid|mediaMid", StreamingDataPathMetadata.providerTrackId(dataPath))
    }

    @Test
    fun providerReturnsNullForInvalidDataPath() {
        assertNull(StreamingDataPathMetadata.provider("streaming:unknown:track-1"))
        assertEquals("", StreamingDataPathMetadata.providerTrackId("streaming:netease:"))
    }

    @Test
    fun qualityReturnsBlankForMissingOrEmptyDataPath() {
        assertEquals("", StreamingDataPathMetadata.quality(null))
        assertEquals("", StreamingDataPathMetadata.quality(""))
        assertEquals("", StreamingDataPathMetadata.quality("streaming:netease:track-1"))
    }

    @Test
    fun qualityReadsDelimitedQueryValue() {
        assertEquals("lossless", StreamingDataPathMetadata.quality("streaming:netease:track-1?quality=LOSSLESS&sourceOptions=[]"))
        assertEquals("high", StreamingDataPathMetadata.quality("streaming:qq:track-2?quality= high |extra"))
        assertEquals("standard", StreamingDataPathMetadata.quality("streaming:mock:track-3?quality=standard#fragment"))
    }

    @Test
    fun playbackMimeTypeReadsValidatedEncodedQueryValue() {
        assertEquals(
            "audio/mp4",
            StreamingDataPathMetadata.playbackMimeType(
                "streaming:bilibili:video:BV1:cid:2?playbackMime=audio%2Fmp4"
            )
        )
        assertEquals(
            "",
            StreamingDataPathMetadata.playbackMimeType(
                "streaming:bilibili:video:BV1:cid:2?playbackMime=not-a-mime"
            )
        )
        assertEquals("", StreamingDataPathMetadata.playbackMimeType("file:///music/local.m4s"))
    }

    @Test
    fun luoxueMusicInfoRestoresBoundedJsonObject() {
        val json = """{"id":"kw_123","name":"Ahead of Us"}"""
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toByteArray(StandardCharsets.UTF_8))

        val restored = JSONObject(
            StreamingDataPathMetadata.luoxueMusicInfoJson(
                "streaming:luoxue:kw:123?lxmi=$encoded"
            ).orEmpty()
        )
        assertEquals("kw_123", restored.getString("id"))
        assertEquals("Ahead of Us", restored.getString("name"))
        assertNull(
            StreamingDataPathMetadata.luoxueMusicInfoJson(
                "streaming:luoxue:kw:123?lxmi=not-base64"
            )
        )
    }

    @Test
    fun streamingDataPathRoundTripsPlaybackIdentityAndPrivateMetadata() {
        val dataPath = StreamingDataPathMetadata.streamingDataPath(
            provider = "luoxue",
            providerTrackId = "kw:123",
            quality = "lossless",
            playbackMimeType = "audio/flac",
            luoxueMusicInfoJson = """{"id":"kw_123","name":"Ahead of Us"}"""
        )

        assertEquals(StreamingProviderName.LUOXUE, StreamingDataPathMetadata.provider(dataPath))
        assertEquals("kw:123", StreamingDataPathMetadata.providerTrackId(dataPath))
        assertEquals("lossless", StreamingDataPathMetadata.quality(dataPath))
        assertEquals("audio/flac", StreamingDataPathMetadata.playbackMimeType(dataPath))
        assertEquals(
            "Ahead of Us",
            JSONObject(StreamingDataPathMetadata.luoxueMusicInfoJson(dataPath).orEmpty())
                .getString("name")
        )
    }
}
