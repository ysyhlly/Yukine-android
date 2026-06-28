package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingEnumsTest {
    @Test
    fun providerAliasesNormalizeToStableNames() {
        assertEquals(StreamingProviderName.NETEASE, StreamingProviderName.fromWireName("163_music"))
        assertEquals(StreamingProviderName.QQ_MUSIC, StreamingProviderName.fromWireName("qq-music"))
        assertEquals(StreamingProviderName.LUOXUE, StreamingProviderName.fromWireName("洛雪音源"))
        assertNull(StreamingProviderName.fromWireName("unknown-provider"))
    }

    @Test
    fun qualityAndErrorWireNamesRemainStable() {
        assertEquals(StreamingAudioQuality.LOSSLESS, StreamingAudioQuality.fromWireName("lossless"))
        assertEquals(StreamingErrorCode.AUTH_REQUIRED, StreamingErrorCode.fromWireName("AUTH_REQUIRED"))
        assertEquals(StreamingErrorCode.UNKNOWN, StreamingErrorCode.fromWireName("new_error"))
    }
}
