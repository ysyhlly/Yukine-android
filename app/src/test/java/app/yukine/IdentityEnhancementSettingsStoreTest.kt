package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityEnhancementSettingsStoreTest {
    @Test
    fun proxyNormalizationAcceptsHttpAndRejectsNonNetworkValues() {
        assertEquals(
            "https://proxy.example/ws/2/",
            IdentityEnhancementSettingsStore.normalizeMusicBrainzProxy(" https://proxy.example/ws/2/// ")
        )
        assertEquals("http://127.0.0.1:8080/", IdentityEnhancementSettingsStore.normalizeMusicBrainzProxy("http://127.0.0.1:8080"))
        assertEquals("", IdentityEnhancementSettingsStore.normalizeMusicBrainzProxy("file:///tmp/proxy"))
        assertEquals("", IdentityEnhancementSettingsStore.normalizeMusicBrainzProxy(""))
    }

    @Test
    fun metadataGatewayNormalizationKeepsOnlyCompatibleNetworkEndpoints() {
        assertEquals(
            "https://gateway.example/base/",
            IdentityEnhancementSettingsStore.normalizeMetadataGatewayEndpoint(" https://gateway.example/base/// ")
        )
        assertEquals("http://127.0.0.1:8787/", IdentityEnhancementSettingsStore.normalizeMetadataGatewayEndpoint("http://127.0.0.1:8787"))
        assertEquals("", IdentityEnhancementSettingsStore.normalizeMetadataGatewayEndpoint("content://gateway"))
    }
}
