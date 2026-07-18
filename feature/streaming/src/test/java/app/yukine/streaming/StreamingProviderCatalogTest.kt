package app.yukine.streaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingProviderCatalogTest {
    @Test
    fun bilibiliResolvesOnlyItsOwnImportedPlaybackSource() {
        val capabilities = StreamingProviderCatalog
            .localFirstDescriptor(StreamingProviderName.BILIBILI)
            .capabilities

        assertTrue(capabilities.supportsAudioResolve)
        assertFalse(capabilities.supportsAudioFallback)
        assertFalse(capabilities.supportsSearch)
    }
}
