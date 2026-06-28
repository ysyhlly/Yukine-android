package app.yukine.common

import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
