package app.yukine.data.enrichment

import java.net.HttpURLConnection
import java.net.URL

data class MetadataHttpResponse(val statusCode: Int, val body: String)

fun interface MetadataHttpTransport {
    fun get(url: String, headers: Map<String, String>): MetadataHttpResponse
}

class UrlConnectionMetadataHttpTransport(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000
) : MetadataHttpTransport {
    override fun get(url: String, headers: Map<String, String>): MetadataHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            headers.forEach(connection::setRequestProperty)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            MetadataHttpResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

fun interface RequestRateLimiter {
    fun awaitPermit()
}

class OneRequestPerSecondRateLimiter(
    private val intervalNanos: Long = 1_000_000_000L,
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleepMillis: (Long) -> Unit = Thread::sleep
) : RequestRateLimiter {
    private var nextPermitNanos = 0L

    @Synchronized
    override fun awaitPermit() {
        val now = nanoTime()
        val waitNanos = nextPermitNanos - now
        if (waitNanos > 0L) sleepMillis((waitNanos + 999_999L) / 1_000_000L)
        val grantedAt = nanoTime().coerceAtLeast(nextPermitNanos)
        nextPermitNanos = if (Long.MAX_VALUE - grantedAt < intervalNanos) Long.MAX_VALUE else grantedAt + intervalNanos
    }
}

/** Process-wide limiter so independently created MusicBrainz clients share the same request budget. */
internal object MusicBrainzRequestRateLimiter : RequestRateLimiter {
    private val delegate = OneRequestPerSecondRateLimiter()

    override fun awaitPermit() = delegate.awaitPermit()
}
