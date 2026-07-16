package app.yukine

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingHttpProbeTest {
    @Test
    fun keepsHeadersOnSameOriginRedirect() {
        val receivedHeader = AtomicReference<String?>()
        val server = ProbeTestServer(expectedRequests = 2) { request ->
            if (request.path == "/redirect") {
                TestResponse.redirect("/audio")
            } else {
                receivedHeader.set(request.headers["authorization"])
                TestResponse.audio("audio/mpeg")
            }
        }
        server.start()
        try {
            val result = RecordingHttpProbe.probe(
                "${server.baseUrl}/redirect",
                mapOf("Authorization" to "Bearer private-token")
            )

            assertEquals("mpeg", result.codec)
            assertEquals("Bearer private-token", receivedHeader.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun stripsDynamicHeadersAcrossOriginRedirect() {
        val receivedHeader = AtomicReference<String?>()
        val target = ProbeTestServer { request ->
            receivedHeader.set(request.headers["authorization"])
            TestResponse.audio("audio/flac")
        }
        target.start()
        val source = ProbeTestServer { TestResponse.redirect("${target.baseUrl}/audio") }
        source.start()
        try {
            val result = RecordingHttpProbe.probe(
                "${source.baseUrl}/redirect",
                mapOf("Authorization" to "Bearer private-token")
            )

            assertEquals("flac", result.codec)
            assertNull(receivedHeader.get())
        } finally {
            source.stop()
            target.stop()
        }
    }

    private data class TestRequest(val path: String, val headers: Map<String, String>)

    private data class TestResponse(
        val status: String,
        val contentType: String = "",
        val location: String = "",
        val body: ByteArray = byteArrayOf()
    ) {
        companion object {
            fun redirect(location: String) = TestResponse("302 Found", location = location)
            fun audio(contentType: String) = TestResponse(
                status = "206 Partial Content",
                contentType = contentType,
                body = byteArrayOf(1)
            )
        }
    }

    private class ProbeTestServer(
        private val expectedRequests: Int = 1,
        private val handler: (TestRequest) -> TestResponse
    ) {
        private val server = ServerSocket(0)
        private val ready = CountDownLatch(1)
        private lateinit var thread: Thread
        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"

        fun start() {
            thread = Thread {
                ready.countDown()
                repeat(expectedRequests) {
                    if (!server.isClosed) server.accept().use(::serve)
                }
            }.apply {
                name = "RecordingHttpProbeTestServer"
                isDaemon = true
                start()
            }
            check(ready.await(2, TimeUnit.SECONDS))
        }

        fun stop() {
            server.close()
            if (::thread.isInitialized) thread.join(2_000)
        }

        private fun serve(socket: Socket) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            val headers = linkedMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
                }
                line = reader.readLine()
            }
            val response = handler(
                TestRequest(
                    path = requestLine.split(' ').getOrNull(1).orEmpty(),
                    headers = headers
                )
            )
            val responseHeaders = buildString {
                append("HTTP/1.1 ${response.status}\r\n")
                if (response.contentType.isNotBlank()) append("Content-Type: ${response.contentType}\r\n")
                if (response.location.isNotBlank()) append("Location: ${response.location}\r\n")
                append("Content-Length: ${response.body.size}\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.UTF_8)
            socket.getOutputStream().use { output ->
                output.write(responseHeaders)
                output.write(response.body)
                output.flush()
            }
        }
    }
}
