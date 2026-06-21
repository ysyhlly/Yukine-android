package app.yukine.dashboard

import app.yukine.StreamingGatewayEndpointStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * HTTP implementation of DashboardGateway.
 * Uses the same endpoint as StreamingGateway, with /api/v1 prefix.
 */
class RemoteDashboardGateway(
    private val endpointStore: StreamingGatewayEndpointStore
) : DashboardGateway {

    companion object {
        private const val API_PREFIX = "/api/v1"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    override suspend fun home(): DashboardHomeResponse = withContext(Dispatchers.IO) {
        val response = get("/dashboard/home")
        DashboardJson.parseHomeResponse(response)
    }

    override suspend fun playbackState(): PlaybackStateResponse = withContext(Dispatchers.IO) {
        val response = get("/playback/state")
        DashboardJson.parsePlaybackState(response)
    }

    override suspend fun play(): PlaybackActionResponse = withContext(Dispatchers.IO) {
        val response = post("/playback/play", null)
        DashboardJson.parsePlaybackAction(response)
    }

    override suspend fun pause(): PlaybackActionResponse = withContext(Dispatchers.IO) {
        val response = post("/playback/pause", null)
        DashboardJson.parsePlaybackAction(response)
    }

    override suspend fun toggle(): PlaybackActionResponse = withContext(Dispatchers.IO) {
        val response = post("/playback/toggle", null)
        DashboardJson.parsePlaybackAction(response)
    }

    override suspend fun next(): PlaybackActionResponse = withContext(Dispatchers.IO) {
        val response = post("/playback/next", null)
        DashboardJson.parsePlaybackAction(response)
    }

    override suspend fun previous(): PlaybackActionResponse = withContext(Dispatchers.IO) {
        val response = post("/playback/previous", null)
        DashboardJson.parsePlaybackAction(response)
    }

    override suspend fun seek(positionMs: Long): PlaybackActionResponse = withContext(Dispatchers.IO) {
        val body = DashboardJson.seekRequest(positionMs).toString()
        val response = post("/playback/seek", body)
        DashboardJson.parsePlaybackAction(response)
    }

    override suspend fun recentActivity(limit: Int): RecentActivityResponse = withContext(Dispatchers.IO) {
        val response = get("/activity/recent?limit=$limit")
        DashboardJson.parseRecentActivity(response)
    }

    override suspend fun weeklyRecap(): WeeklyRecapResponse = withContext(Dispatchers.IO) {
        val response = get("/recap/weekly")
        DashboardJson.parseWeeklyRecapResponse(response)
    }

    // ── HTTP Helpers ────────────────────────────────────────────────────────

    private fun get(path: String): String {
        val conn = openConnection(path, "GET")
        return readResponse(conn)
    }

    private fun post(path: String, body: String?): String {
        val conn = openConnection(path, "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

        if (body != null) {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }
        } else {
            // Empty body for POST without payload
            conn.outputStream.close()
        }

        return readResponse(conn)
    }

    private fun openConnection(path: String, method: String): HttpURLConnection {
        if (!endpointStore.configured()) {
            throw DashboardGatewayException("Gateway not configured", httpCode = 0)
        }

        val fullUrl = url(path)
        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Yukine/Android")
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): String {
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }
                throw DashboardGatewayException(
                    "HTTP $code: ${conn.responseMessage}. $errorBody".trim(),
                    httpCode = code
                )
            }

            return BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun url(path: String): String {
        val base = endpointStore.endpoint().trimEnd('/')
        val apiPath = API_PREFIX + "/" + path.trimStart('/')
        return "$base$apiPath"
    }
}
