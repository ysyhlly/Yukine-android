package app.yukine.playback

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class TogetherHttpRangeProbeTest {
    @Test
    fun rangeResponseIsAcceptedWithoutHeadOrAcceptRangesMetadata() {
        val observedMethod = AtomicReference<String>()
        val observedRange = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/audio") { exchange ->
                observedMethod.set(exchange.requestMethod)
                observedRange.set(exchange.requestHeaders.getFirst("Range"))
                exchange.responseHeaders.add("Content-Range", "bytes 0-0/4096")
                exchange.sendResponseHeaders(206, 1)
                exchange.responseBody.use { it.write(byteArrayOf(0)) }
            }
            start()
        }

        try {
            val size = TogetherHttpRangeProbe.contentLength(
                "http://127.0.0.1:${server.address.port}/audio",
                emptyMap()
            )

            assertEquals(4096L, size)
            assertEquals("GET", observedMethod.get())
            assertEquals("bytes=0-0", observedRange.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun advertisedRangeIsRejectedWhenServerIgnoresTheRangeRequest() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/audio") { exchange ->
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.sendResponseHeaders(200, 4)
                exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3, 4)) }
            }
            start()
        }

        try {
            val size = TogetherHttpRangeProbe.contentLength(
                "http://127.0.0.1:${server.address.port}/audio",
                emptyMap()
            )

            assertEquals(0L, size)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun requestHeadersAreForwardedWithoutLeakingIntoTheResult() {
        val observedToken = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/audio") { exchange ->
                observedToken.set(exchange.requestHeaders.getFirst("X-Playback-Token"))
                exchange.responseHeaders.add("Content-Range", "bytes 0-0/512")
                exchange.sendResponseHeaders(206, 1)
                exchange.responseBody.use { it.write(byteArrayOf(0)) }
            }
            start()
        }

        try {
            val size = TogetherHttpRangeProbe.contentLength(
                "http://127.0.0.1:${server.address.port}/audio",
                mapOf("X-Playback-Token" to "secret")
            )

            assertEquals(512L, size)
            assertEquals("secret", observedToken.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun malformedOrUnknownContentRangeHasNoUsableLength() {
        assertEquals(0L, TogetherHttpRangeProbe.contentRangeLength(null))
        assertEquals(0L, TogetherHttpRangeProbe.contentRangeLength("bytes 0-0/*"))
        assertEquals(0L, TogetherHttpRangeProbe.contentRangeLength("bytes 0-0/0"))
    }
}
