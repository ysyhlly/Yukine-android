package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPlaybackHeadersTest {
    @Test
    fun headerStoreCopiesHeadersAndRemovesEmptyRegistrations() {
        val dataPath = "streaming:test:headers-${System.nanoTime()}"
        val mutableHeaders = linkedMapOf("Authorization" to "Bearer first")

        StreamingPlaybackHeaders.register(dataPath, mutableHeaders)
        mutableHeaders["Authorization"] = "Bearer changed"

        assertEquals(mapOf("Authorization" to "Bearer first"), StreamingPlaybackHeaders.forDataPath(dataPath))

        StreamingPlaybackHeaders.register(dataPath, emptyMap())

        assertEquals(emptyMap<String, String>(), StreamingPlaybackHeaders.forDataPath(dataPath))
    }

    @Test
    fun headerStoreIgnoresBlankDataPath() {
        StreamingPlaybackHeaders.register("", mapOf("Cookie" to "session=1"))

        assertEquals(emptyMap<String, String>(), StreamingPlaybackHeaders.forDataPath(""))
        assertEquals(emptyMap<String, String>(), StreamingPlaybackHeaders.forDataPath(null))
    }

    @Test
    fun qqPlaybackHeadersAreDiscardedEvenWithCurrentCredential() {
        val sourceHeaders = mapOf("Referer" to "https://y.qq.com/")
        val cookie = "uin=o12345; qqmusic_key=local-key"

        val runtimeHeaders = headersWithStreamingAuth(
            dataPath = "streaming:qqmusic:song-mid-1|media-mid-1",
            headers = sourceHeaders,
            localAuthStore = FakeAuthStore(cookie)
        )

        assertTrue(runtimeHeaders.isEmpty())
        assertEquals(null, sourceHeaders["Cookie"])
    }

    @Test
    fun qqPlaybackHeadersRejectNameOnlyCredential() {
        val runtimeHeaders = headersWithStreamingAuth(
            dataPath = "streaming:qqmusic:song-mid-1|media-mid-1",
            headers = mapOf("Referer" to "https://y.qq.com/"),
            localAuthStore = FakeAuthStore("uin=o12345; qqmusic_key=; qm_keyst=")
        )

        assertEquals(null, runtimeHeaders["Cookie"])
    }

    @Test
    fun playbackUrlGuardRejectsQqInvalidIpDiagnostic() {
        assertFalse(isSupportedPlaybackSourceUrl("163.125.230.232;invalid;"))
        assertFalse(isSupportedPlaybackSourceUrl("https://163.125.230.232;invalid;/audio.mp3"))
        assertTrue(isSupportedPlaybackSourceUrl("https://isure.stream.qqmusic.qq.com/C400test.m4a?vkey=test"))
    }

    private class FakeAuthStore(
        private val cookie: String?
    ) : StreamingLocalAuthStore {
        override fun authState(provider: StreamingProviderName): StreamingAuthState =
            StreamingAuthState(
                kind = LocalStreamingAuthStore.providerAuthKind(provider),
                connected = !cookie.isNullOrBlank()
            )

        override fun saveLogin(
            provider: StreamingProviderName,
            cookieHeader: String?,
            displayName: String?
        ): StreamingAuthState = authState(provider)

        override fun signOut(provider: StreamingProviderName): StreamingAuthState =
            authState(provider).copy(connected = false)

        override fun cookieHeader(provider: StreamingProviderName): String? = cookie

        override fun connected(provider: StreamingProviderName): Boolean = !cookie.isNullOrBlank()
    }
}
