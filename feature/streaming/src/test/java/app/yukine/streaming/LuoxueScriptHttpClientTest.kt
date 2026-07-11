package app.yukine.streaming

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LuoxueScriptHttpClientTest {
    @Test
    fun rejectsLoopbackRequestsBeforeOpeningConnection() {
        val error = requestFailure("http://127.0.0.1:18080/private")

        assertTrue(error.message.orEmpty().contains("私有网络"))
    }

    @Test
    fun rejectsNonHttpProtocolsBeforeOpeningConnection() {
        val error = requestFailure("file:///data/local/tmp/source.js")

        assertTrue(error.message.orEmpty().contains("HTTP/HTTPS"))
    }

    private fun requestFailure(url: String): IllegalArgumentException {
        try {
            DefaultLuoxueScriptHttpClient().execute(
                LuoxueScriptHttpRequest(
                    url = url,
                    method = "GET",
                    headers = emptyMap(),
                    body = null,
                    timeoutMs = 1_000
                )
            )
            fail("Expected request to be blocked")
            throw AssertionError("Expected request to be blocked")
        } catch (error: IllegalArgumentException) {
            return error
        }
    }
}
