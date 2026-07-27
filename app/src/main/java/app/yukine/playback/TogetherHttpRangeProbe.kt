package app.yukine.playback

import java.net.HttpURLConnection
import java.net.URL

/**
 * Verifies the HTTP capability that Junto actually needs instead of trusting provider metadata.
 *
 * `Accept-Ranges` on a HEAD response is only advisory: some media servers omit it while correctly
 * serving byte ranges, and others advertise it but ignore Range requests. A one-byte GET is both
 * cheap and definitive.
 */
internal object TogetherHttpRangeProbe {
    private const val TIMEOUT_MS = 8_000

    fun contentLength(url: String, headers: Map<String, String>): Long = runCatching {
        (URL(url).openConnection() as HttpURLConnection).useConnection { connection ->
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.setRequestProperty("Range", "bytes=0-0")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                return@useConnection 0L
            }
            contentRangeLength(connection.getHeaderField("Content-Range"))
        }
    }.getOrDefault(0L)

    internal fun contentRangeLength(value: String?): Long {
        val total = value.orEmpty()
            .substringAfterLast('/', "")
            .trim()
            .toLongOrNull()
            ?: return 0L
        return total.takeIf { it > 0L } ?: 0L
    }

    private inline fun <T> HttpURLConnection.useConnection(
        block: (HttpURLConnection) -> T
    ): T = try {
        block(this)
    } finally {
        disconnect()
    }
}
