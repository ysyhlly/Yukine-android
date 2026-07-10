package app.yukine.streaming

import kotlin.math.absoluteValue
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.spec.X509EncodedKeySpec
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

interface NeteaseHttpClient {
    fun getJson(path: String, query: Map<String, String>, cookieHeader: String?): JSONObject

    /**
     * Posts a form request and exposes response Cookie headers. Existing test/local clients can
     * omit this capability; session maintenance then falls back to lightweight verification.
     */
    fun postForm(
        path: String,
        form: Map<String, String>,
        cookieHeader: String?
    ): NeteaseHttpResponse {
        throw StreamingGatewayException(
            "NetEase session refresh is unavailable for this HTTP client",
            code = StreamingErrorCode.SOURCE_UNAVAILABLE
        )
    }
}

data class NeteaseHttpResponse(
    val body: JSONObject,
    val setCookieHeaders: List<String> = emptyList()
)

class DefaultNeteaseHttpClient(
    private val baseUrl: String = "https://music.163.com",
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000
) : NeteaseHttpClient {
    override fun getJson(path: String, query: Map<String, String>, cookieHeader: String?): JSONObject {
        return request("GET", path, query, null, cookieHeader).body
    }

    override fun postForm(
        path: String,
        form: Map<String, String>,
        cookieHeader: String?
    ): NeteaseHttpResponse {
        val formBody = form.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return request("POST", path, emptyMap(), formBody, cookieHeader)
    }

    private fun request(
        method: String,
        path: String,
        query: Map<String, String>,
        formBody: String?,
        cookieHeader: String?
    ): NeteaseHttpResponse {
        val url = URL(url(path, query))
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Yukine-Android")
        connection.setRequestProperty("Referer", "https://music.163.com/")
        cookieHeader?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
        try {
            if (formBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(formBody)
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                    buildString {
                        var line = reader.readLine()
                        while (line != null) {
                            append(line)
                            line = reader.readLine()
                        }
                    }
                }
            }.orEmpty()
            if (status !in 200..299) {
                throw StreamingGatewayException(
                    "NetEase request failed ($status): ${body.ifBlank { path }}",
                    code = when (status) {
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        HttpURLConnection.HTTP_FORBIDDEN -> StreamingErrorCode.AUTH_REQUIRED
                        else -> StreamingErrorCode.SOURCE_UNAVAILABLE
                    }
                )
            }
            val json = JSONObject(body).also { validateNeteaseResponse(it, path) }
            val setCookieHeaders = connection.headerFields
                .entries
                .firstOrNull { (name, _) -> name.equals("Set-Cookie", ignoreCase = true) }
                ?.value
                .orEmpty()
                .filterNotNull()
            return NeteaseHttpResponse(json, setCookieHeaders)
        } catch (error: IOException) {
            throw StreamingGatewayException(
                "NetEase request failed: ${error.message ?: path}",
                cause = error,
                code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                retryable = true
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun validateNeteaseResponse(body: JSONObject, path: String) {
        val code = body.opt("code") ?: body.optJSONObject("body")?.opt("code") ?: return
        val normalized = when (code) {
            is Number -> code.toInt()
            is String -> code.toIntOrNull()
            else -> null
        } ?: return
        if (normalized in 200..299) {
            return
        }
        val message = body.optString("message")
            .ifBlank { body.optString("msg") }
            .ifBlank { body.optJSONObject("body")?.optString("message").orEmpty() }
            .ifBlank { body.optJSONObject("body")?.optString("msg").orEmpty() }
        if (normalized == 301 || normalized == 302 || normalized == 401 || normalized == 403) {
            throw StreamingGatewayException(
                message.ifBlank { "NetEase login expired, please sign in again" },
                code = StreamingErrorCode.AUTH_REQUIRED
            )
        }
        throw StreamingGatewayException(
            message.ifBlank { "NetEase request failed ($normalized): $path" },
            code = StreamingErrorCode.SOURCE_UNAVAILABLE
        )
    }

    private fun url(path: String, query: Map<String, String>): String {
        val queryText = query.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val normalizedBase = baseUrl.trimEnd('/')
        val normalizedPath = path.trimStart('/')
        return if (queryText.isBlank()) {
            "$normalizedBase/$normalizedPath"
        } else {
            "$normalizedBase/$normalizedPath?$queryText"
        }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}

data class NeteaseSessionRefreshResult(
    val cookieHeader: String,
    val changed: Boolean
)

private fun neteaseCookieValue(cookie: String, name: String): String? {
    return cookie.split(';').firstNotNullOfOrNull { pair ->
        val separator = pair.indexOf('=')
        if (separator <= 0 || !pair.substring(0, separator).trim().equals(name, ignoreCase = true)) {
            null
        } else {
            pair.substring(separator + 1).trim().takeIf { it.isNotBlank() }
        }
    }
}

/**
 * Minimal independent implementation of the request envelope required by NetEase's token refresh
 * endpoint. It follows the public protocol shape but intentionally exposes no login or password
 * operations; it only renews a session the user already established in the isolated WebView.
 */
private object NeteaseWeApiCipher {
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val PUBLIC_KEY_DER_BASE64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
    private val random = SecureRandom()

    fun encryptForm(payload: JSONObject): Map<String, String> {
        val secret = buildString(16) {
            repeat(16) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
        val firstPass = aesCbc(payload.toString(), PRESET_KEY)
        return mapOf(
            "params" to aesCbc(firstPass, secret),
            "encSecKey" to rsaNoPadding(secret.reversed())
        )
    }

    private fun aesCbc(value: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES"),
            IvParameterSpec(IV.toByteArray(StandardCharsets.UTF_8))
        )
        return Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun rsaNoPadding(value: String): String {
        val key = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_DER_BASE64, Base64.NO_WRAP))
        )
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}

class LocalNeteaseStreamingClient(
    private val authStore: StreamingLocalAuthStore?,
    private val httpClient: NeteaseHttpClient = DefaultNeteaseHttpClient()
) {
    private val heartbeatSeedSequence = AtomicInteger(0)
    private val heartbeatResultSequence = AtomicInteger(0)

    fun canHandle(provider: StreamingProviderName): Boolean {
        return supportsProvider(provider) && authStore?.connected(provider) == true
    }

    /** Lightweight account API used to validate a persisted session without loading a playlist. */
    fun verifySession(): String {
        return resolveUserId(requireCookie())
    }

    /**
     * Refreshes a NetEase web session through the provider's token-refresh endpoint when supported.
     * QR-login cookies are known to be unsupported by that endpoint, so callers must still verify
     * the returned/current session before deciding that a credential is invalid.
     */
    fun refreshSession(): NeteaseSessionRefreshResult {
        val cookie = requireCookie()
        val requestCookie = StreamingCookieHeaderParser.merge(cookie, "os=pc")
        val csrf = neteaseCookieValue(requestCookie, "__csrf").orEmpty()
        val response = httpClient.postForm(
            path = "/weapi/login/token/refresh",
            form = NeteaseWeApiCipher.encryptForm(JSONObject().put("csrf_token", csrf)),
            cookieHeader = requestCookie
        )
        var refreshed = requestCookie
        response.body.optString("cookie").takeIf { it.isNotBlank() }?.let { bodyCookie ->
            refreshed = StreamingCookieHeaderParser.merge(refreshed, bodyCookie)
        }
        refreshed = StreamingCookieHeaderParser.mergeSetCookieHeaders(refreshed, response.setCookieHeaders)
        return NeteaseSessionRefreshResult(
            cookieHeader = refreshed,
            changed = refreshed != cookie
        )
    }

    companion object {
        /**
         * Whether a direct-to-platform local client exists for [provider], independent of login
         * state. Used to decide which providers can function without a configured gateway. Only
         * NetEase currently has a local implementation; the others require a gateway backend.
         */
        fun supportsProvider(provider: StreamingProviderName): Boolean {
            return provider == StreamingProviderName.NETEASE
        }
    }

    fun userPlaylists(): List<StreamingPlaylist> {
        val cookie = requireCookie()
        val userId = resolveUserId(cookie)
        val body = httpClient.getJson(
            "/api/user/playlist",
            mapOf(
                "uid" to userId,
                "limit" to "1000",
                "offset" to "0",
                "includeVideo" to "true"
            ),
            cookie
        )
        return playlistRecords(body).map(::playlist)
    }

    fun userLikedTracks(): List<StreamingTrack> {
        val cookie = requireCookie()
        val userId = resolveUserId(cookie)
        val body = httpClient.getJson(
            "/api/likelist",
            mapOf("uid" to userId),
            cookie
        )
        return fetchSongs(idList(body.optJSONArray("ids")))
    }

    /**
     * NetEase "每日推荐" — the personalized 每日推荐 song list. Requires login. The endpoint returns
     * full song objects under `data.dailySongs`, so no second song-detail fetch is needed.
     */
    fun dailyRecommendedTracks(): List<StreamingTrack> {
        val cookie = requireCookie()
        val body = httpClient.getJson(
            "/api/v3/discovery/recommend/songs",
            emptyMap(),
            cookie
        )
        val data = body.optJSONObject("data") ?: JSONObject()
        val dailySongs = data.optJSONArray("dailySongs")
            ?: data.optJSONArray("songs")
            ?: body.optJSONArray("recommend")
        return songsFromArray(dailySongs)
    }

    fun search(request: StreamingSearchRequest): StreamingSearchResult {
        val normalized = request.normalizedLocal()
        if (normalized.query.isBlank() || !normalized.mediaTypes.contains(StreamingMediaType.TRACK)) {
            return StreamingSearchResult(
                provider = StreamingProviderName.NETEASE,
                query = normalized.query,
                page = normalized.page,
                pageSize = normalized.pageSize,
                total = 0
            )
        }
        val offset = (normalized.page - 1) * normalized.pageSize
        val body = httpClient.getJson(
            "/api/cloudsearch/pc",
            mapOf(
                "s" to normalized.query,
                "type" to "1",
                "limit" to normalized.pageSize.toString(),
                "offset" to offset.toString(),
                "total" to "true"
            ),
            authStore?.cookieHeader(StreamingProviderName.NETEASE)
        )
        val result = body.optJSONObject("result") ?: body.optJSONObject("data") ?: JSONObject()
        val tracks = songsFromArray(
            result.optJSONArray("songs")
                ?: result.optJSONArray("song")
                ?: body.optJSONArray("songs")
        )
        val total = result.optionalIntLocal("songCount")
            ?: result.optionalIntLocal("total")
            ?: tracks.size
        return StreamingSearchResult(
            provider = StreamingProviderName.NETEASE,
            query = normalized.query,
            page = normalized.page,
            pageSize = normalized.pageSize,
            total = total,
            hasMore = offset + tracks.size < total,
            tracks = tracks
        )
    }

    /**
     * NetEase "心动推荐" (智能播放 / intelligence list). Seeds an intelligent recommendation stream
     * from a song the user already likes. We use the first liked track as the seed and (when
     * available) one of the user's playlists as the playlist context, mirroring how the official
     * client starts 心动模式 from "我喜欢的音乐". Requires login.
     */
    fun heartbeatRecommendedTracks(
        seedTrackId: String?,
        playlistId: String?,
        count: Int = 30
    ): List<StreamingTrack> {
        val seedId = seedTrackId?.trim().orEmpty()
        if (seedId.isBlank()) {
            return heartbeatRecommendedTracks(count)
        }
        val cookie = authStore?.cookieHeader(StreamingProviderName.NETEASE)?.takeIf { it.isNotBlank() }
            ?: return similarTracks(seedId, count)
        val contextPlaylistId = playlistId?.trim()?.takeIf { it.isNotBlank() } ?: seedId
        val seen = HashSet<String>()
        val accumulated = ArrayList<StreamingTrack>()
        val targetCount = count.coerceIn(1, 100)
        val maxRounds = 6
        for (round in 0 until maxRounds) {
            if (accumulated.size >= targetCount) break
            val sid = if (round == 0) seedId else accumulated.lastOrNull()?.providerTrackId ?: seedId
            val batch = runCatching {
                val body = httpClient.getJson(
                    "/api/playmode/intelligence/list",
                    mapOf(
                        "id" to seedId,
                        "pid" to contextPlaylistId,
                        "sid" to sid,
                        "count" to targetCount.toString()
                    ),
                    cookie
                )
                heartbeatTracksFromBody(body)
            }.getOrDefault(emptyList())
            if (batch.isEmpty()) break
            var addedThisRound = 0
            for (track in batch) {
                if (seen.add(track.providerTrackId)) {
                    accumulated.add(track)
                    addedThisRound += 1
                    if (accumulated.size >= targetCount) {
                        break
                    }
                }
            }
            if (addedThisRound == 0) break
        }
        if (accumulated.size < targetCount) {
            val fallback = runCatching { similarTracks(seedId, targetCount) }.getOrDefault(emptyList())
            for (track in fallback) {
                if (seen.add(track.providerTrackId)) {
                    accumulated.add(track)
                }
                if (accumulated.size >= targetCount) {
                    break
                }
            }
        }
        return variedHeartbeatResultOrder(accumulated.ifEmpty { similarTracks(seedId, targetCount) })
    }

    fun heartbeatRecommendedTracks(count: Int = 30): List<StreamingTrack> {
        val cookie = requireCookie()
        val liked = userLikedTracks()
        val seed = variedLikedSeed(liked.filter { it.providerTrackId.isNotBlank() })
            ?: throw StreamingGatewayException(
                "网易云「我喜欢的音乐」为空，无法生成心动推荐。",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        val seedId = seed.providerTrackId
        val userId = resolveUserId(cookie)
        val playlistId = runCatching { userPlaylists() }.getOrNull()
            ?.let { preferredHeartbeatPlaylistId(it, userId) }
            ?: seedId
        val seen = HashSet<String>()
        val accumulated = ArrayList<StreamingTrack>()
        val targetCount = count.coerceIn(1, 100)
        val maxRounds = 6
        for (round in 0 until maxRounds) {
            if (accumulated.size >= targetCount) break
            val sid = if (round == 0) seedId else accumulated.lastOrNull()?.providerTrackId ?: seedId
            val batch = runCatching {
                val body = httpClient.getJson(
                    "/api/playmode/intelligence/list",
                    mapOf(
                        "id" to seedId,
                        "pid" to playlistId,
                        "sid" to sid,
                        "count" to targetCount.toString()
                    ),
                    cookie
                )
                heartbeatTracksFromBody(body)
            }.getOrDefault(emptyList())
            if (batch.isEmpty()) break
            var addedThisRound = 0
            for (track in batch) {
                if (seen.add(track.providerTrackId)) {
                    accumulated.add(track)
                    addedThisRound += 1
                    if (accumulated.size >= targetCount) {
                        break
                    }
                }
            }
            if (addedThisRound == 0) break
        }
        if (accumulated.isEmpty()) {
            return variedHeartbeatResultOrder(listOf(seed))
        }
        if (accumulated.size < targetCount) {
            val fallback = runCatching { similarTracks(seedId, targetCount) }.getOrDefault(emptyList())
            for (track in fallback) {
                if (track.providerTrackId == seedId) continue
                if (seen.add(track.providerTrackId)) {
                    accumulated.add(track)
                }
                if (accumulated.size >= targetCount) {
                    break
                }
            }
        }
        val result = if (accumulated.any { it.providerTrackId == seedId }) {
            accumulated
        } else {
            listOf(seed) + accumulated
        }
        return variedHeartbeatResultOrder(result)
    }

    private fun variedLikedSeed(tracks: List<StreamingTrack>): StreamingTrack? {
        if (tracks.isEmpty()) {
            return null
        }
        val offset = Math.floorMod(heartbeatSeedSequence.getAndIncrement(), tracks.size)
        return tracks[offset]
    }

    private fun variedHeartbeatResultOrder(tracks: List<StreamingTrack>): List<StreamingTrack> {
        if (tracks.size <= 1) {
            return tracks
        }
        val offset = Math.floorMod(heartbeatResultSequence.getAndIncrement(), tracks.size)
        if (offset == 0) {
            return tracks
        }
        return tracks.drop(offset) + tracks.take(offset)
    }

    private fun preferredHeartbeatPlaylistId(playlists: List<StreamingPlaylist>, userId: String): String? {
        val likedPlaylistId = userId.toLongOrNull()?.absoluteValue?.let { "${it + 1_000_000_000L}" }
        return playlists.firstOrNull {
            it.providerPlaylistId.isNotBlank() &&
                (it.providerPlaylistId == likedPlaylistId || it.title.contains("喜欢") || it.title.contains("liked", ignoreCase = true))
        }?.providerPlaylistId
            ?: playlists.firstOrNull { it.providerPlaylistId.isNotBlank() }?.providerPlaylistId
    }

    private fun songsFromArray(array: JSONArray?): List<StreamingTrack> {
        if (array == null) return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .map(::track)
    }

    fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        val cookie = requireCookie()
        val normalized = request.normalizedLocal()
        val detail = fetchPlaylistDetail(normalized.providerPlaylistId, cookie)
        val playlistObject = detail.optJSONObject("playlist")
            ?: detail.optJSONObject("result")
            ?: JSONObject()
        val trackIds = idList(playlistObject.optJSONArray("trackIds"))
        val embeddedTracks = playlistObject.optJSONArray("tracks")
        val declaredTotal = playlistObject.optionalIntLocal("trackCount")
        val total = maxOf(
            trackIds.size,
            declaredTotal ?: 0,
            embeddedTracks?.length() ?: 0
        )
        val offset = (normalized.page - 1) * normalized.pageSize
        val pageIds = trackIds.drop(offset).take(normalized.pageSize)
        val expectedWindowSize = if (total > 0) {
            (total - offset).coerceAtLeast(0).coerceAtMost(normalized.pageSize)
        } else {
            normalized.pageSize
        }
        val embeddedPage = songs(embeddedTracks, offset, normalized.pageSize)
        val hasCompleteEmbeddedWindow = expectedWindowSize > 0 && embeddedPage.size >= expectedWindowSize
        val allTracks = if (hasCompleteEmbeddedWindow) {
            null
        } else {
            runCatching {
                fetchPlaylistTrackWindow(
                    providerPlaylistId = normalized.providerPlaylistId,
                    offset = offset,
                    limit = normalized.pageSize,
                    total = total.takeIf { it > 0 },
                    cookie = cookie
                )
            }.getOrNull()
        }
        val tracks = when {
            hasCompleteEmbeddedWindow ->
                embeddedPage
            !allTracks.isNullOrEmpty() ->
                allTracks
            pageIds.isNotEmpty() ->
                fetchSongs(pageIds)
            else ->
                embeddedPage
        }
        val consumed = tracks.size
        return StreamingPlaylistDetail(
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = normalized.providerPlaylistId,
            playlist = playlist(playlistObject, normalized.providerPlaylistId),
            tracks = tracks,
            total = total,
            page = normalized.page,
            pageSize = normalized.pageSize,
            hasMore = offset + consumed < total
        )
    }

    fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        val cookie = requireCookie()
        val id = request.providerTrackId.trim()
        if (id.isBlank()) {
            throw StreamingGatewayException(
                "NetEase track id is empty",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }
        val body = httpClient.getJson(
            "/api/song/enhance/player/url/v1",
            mapOf(
                "ids" to JSONArray(listOf(id)).toString(),
                "level" to neteaseQuality(request.quality),
                "encodeType" to "flac"
            ),
            cookie
        )
        val source = playbackObjects(body).firstOrNull { idText(it.opt("id")) == id }
            ?: playbackObjects(body).firstOrNull()
            ?: JSONObject()
        val url = source.optionalStringLocal("url")
        if (url.isNullOrBlank()) {
            val message = source.optionalStringLocal("message")
                ?: source.optionalStringLocal("msg")
                ?: "NetEase playback URL is unavailable"
            throw StreamingGatewayException(
                message,
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }
        return StreamingPlaybackSource(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            url = url,
            expiresAtEpochMs = source.optionalLongLocal("expi")?.let { System.currentTimeMillis() + it * 1000L },
            mimeType = mimeType(url, source.optionalStringLocal("type") ?: source.optionalStringLocal("encodeType")),
            bitrate = source.optionalIntLocal("br"),
            codec = source.optionalStringLocal("type") ?: source.optionalStringLocal("encodeType"),
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 Yukine-Android",
                "Referer" to "https://music.163.com/",
                "Cookie" to cookie
            ),
            supportsRange = true
        )
    }

    private fun fetchSongs(ids: List<String>): List<StreamingTrack> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(200).flatMap { batch ->
            val body = httpClient.getJson(
                "/api/song/detail/",
                mapOf(
                    "id" to batch.first(),
                    "ids" to JSONArray(batch).toString()
                ),
                authStore?.cookieHeader(StreamingProviderName.NETEASE)
            )
            songs(body.optJSONArray("songs"), 0, batch.size)
        }
    }

    private fun fetchPlaylistDetail(providerPlaylistId: String, cookie: String): JSONObject {
        val v3 = runCatching {
            httpClient.getJson(
                "/api/v3/playlist/detail",
                mapOf(
                    "id" to providerPlaylistId,
                    "n" to "100000",
                    "s" to "0"
                ),
                cookie
            )
        }.getOrNull()
        val playlist = v3?.optJSONObject("playlist") ?: v3?.optJSONObject("result")
        val v3Tracks = playlist?.optJSONArray("tracks")?.length() ?: 0
        val v3TrackIds = playlist?.optJSONArray("trackIds")?.length() ?: 0
        if (v3Tracks > 0 || v3TrackIds > 0) {
            return v3 ?: JSONObject()
        }
        return httpClient.getJson(
            "/api/v6/playlist/detail",
            mapOf("id" to providerPlaylistId),
            cookie
        )
    }

    private fun fetchPlaylistTracksAll(
        providerPlaylistId: String,
        offset: Int,
        limit: Int,
        cookie: String
    ): List<StreamingTrack> {
        val body = httpClient.getJson(
            "/api/playlist/track/all",
            mapOf(
                "id" to providerPlaylistId,
                "limit" to limit.toString(),
                "offset" to offset.toString()
            ),
            cookie
        )
        return songs(body.optJSONArray("songs"), 0, limit)
    }

    private fun fetchPlaylistTrackWindow(
        providerPlaylistId: String,
        offset: Int,
        limit: Int,
        total: Int?,
        cookie: String
    ): List<StreamingTrack> {
        val tracks = ArrayList<StreamingTrack>()
        val seenIds = LinkedHashSet<String>()
        var nextOffset = offset
        var remaining = total?.let { (it - offset).coerceAtLeast(0).coerceAtMost(limit) } ?: limit
        while (remaining > 0) {
            val page = fetchPlaylistTracksAll(providerPlaylistId, nextOffset, remaining, cookie)
            if (page.isEmpty()) {
                break
            }
            var added = 0
            page.forEach { track ->
                val key = track.providerTrackId.ifBlank { track.stableKey }
                if (seenIds.add(key)) {
                    tracks += track
                    added += 1
                }
            }
            nextOffset += page.size
            remaining -= added
            if (added == 0 || tracks.size >= limit) {
                break
            }
            if (total != null && offset + tracks.size >= total) {
                break
            }
        }
        return tracks
    }

    private fun similarTracks(seedTrackId: String, count: Int): List<StreamingTrack> {
        val limit = count.coerceIn(1, 100)
        val body = httpClient.getJson(
            "/api/v1/discovery/simiSong",
            mapOf(
                "songid" to seedTrackId,
                "limit" to limit.toString()
            ),
            authStore?.cookieHeader(StreamingProviderName.NETEASE)
        )
        val songs = body.optJSONArray("songs")
            ?: body.optJSONObject("result")?.optJSONArray("songs")
            ?: body.optJSONObject("data")?.optJSONArray("songs")
        return songsFromArray(songs).take(limit)
    }

    private fun heartbeatTracksFromBody(body: JSONObject): List<StreamingTrack> {
        val data = body.optJSONObject("data")
        val list = body.optJSONArray("data")
            ?: data?.optJSONArray("list")
            ?: data?.optJSONArray("songs")
            ?: body.optJSONArray("recommend")
            ?: body.optJSONArray("songs")
        val tracks = ArrayList<StreamingTrack>()
        if (list != null) {
            for (i in 0 until list.length()) {
                val entry = list.optJSONObject(i) ?: continue
                val songObject = entry.optJSONObject("songInfo")
                    ?: entry.optJSONObject("songInfoDTO")
                    ?: entry.optJSONObject("song")
                    ?: entry.optJSONObject("track")
                    ?: entry.takeIf { it.has("name") }
                if (songObject != null) {
                    tracks += track(songObject)
                }
            }
        }
        return tracks
    }

    private fun resolveUserId(cookie: String): String {
        val body = httpClient.getJson("/api/nuser/account/get", emptyMap(), cookie)
        collectUserIds(body).firstOrNull()?.let { return it }
        throw StreamingGatewayException(
            "无法读取网易云账号 ID，请重新登录后再加载账户歌单。",
            code = StreamingErrorCode.AUTH_REQUIRED
        )
    }

    private fun requireCookie(): String {
        return authStore?.cookieHeader(StreamingProviderName.NETEASE)?.takeIf { it.isNotBlank() }
            ?: throw StreamingGatewayException(
                "请先登录网易云音乐账号后再加载歌单。",
                code = StreamingErrorCode.AUTH_REQUIRED
            )
    }

    private fun playlist(value: JSONObject, fallbackId: String? = null): StreamingPlaylist {
        val providerPlaylistId = idText(value.opt("id") ?: value.opt("playlistId"))
            ?: fallbackId
            ?: ""
        val cover = imageUrl(value.optionalStringLocal("coverImgUrl") ?: value.optionalStringLocal("picUrl"))
        return StreamingPlaylist(
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = providerPlaylistId,
            title = value.optionalStringLocal("name") ?: "NetEase Playlist",
            description = value.optionalStringLocal("description"),
            creator = value.optJSONObject("creator")?.optionalStringLocal("nickname"),
            coverUrl = cover,
            trackCount = value.optionalIntLocal("trackCount")
        )
    }

    private fun songs(array: JSONArray?, offset: Int, limit: Int): List<StreamingTrack> {
        if (array == null) return emptyList()
        val end = (offset + limit).coerceAtMost(array.length())
        if (offset >= end) return emptyList()
        return (offset until end)
            .mapNotNull { array.optJSONObject(it) }
            .map(::track)
    }

    private fun track(value: JSONObject): StreamingTrack {
        val album = value.optJSONObject("al") ?: value.optJSONObject("album") ?: JSONObject()
        val artistsArray = value.optJSONArray("ar") ?: value.optJSONArray("artists")
        val artists = artistRefs(artistsArray)
        val artistText = artists.joinToString(" / ") { it.name }
            .ifBlank { value.optionalStringLocal("artist").orEmpty() }
        val cover = imageUrl(album.optionalStringLocal("picUrl") ?: album.optionalStringLocal("blurPicUrl") ?: album.optionalStringLocal("pic"))
        val providerTrackId = idText(value.opt("id") ?: value.opt("songId") ?: value.opt("trackId")) ?: ""
        val playable = value.optInt("fee", 0) != 4
        return StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = providerTrackId,
            title = cleanDisplayText(value.optionalStringLocal("name") ?: value.optionalStringLocal("title") ?: "").orEmpty(),
            artist = cleanDisplayText(artistText).orEmpty(),
            artists = artists,
            album = cleanDisplayText(album.optionalStringLocal("name") ?: value.optionalStringLocal("album")),
            albumId = idText(album.opt("id")),
            durationMs = value.optionalLongLocal("dt") ?: value.optionalLongLocal("duration"),
            coverUrl = cover,
            coverThumbUrl = cover,
            playable = playable,
            description = songDescription(value, album.optionalStringLocal("name")),
            lyricSources = listOf(
                StreamingLyricSource(
                    provider = StreamingProviderName.NETEASE,
                    name = "网易云歌词",
                    providerTrackId = providerTrackId,
                    priority = 0
                )
            ),
            playbackCandidates = listOf(
                StreamingPlaybackCandidate(
                    provider = StreamingProviderName.NETEASE,
                    quality = null,
                    label = "网易云播放源",
                    providerTrackId = providerTrackId,
                    available = playable
                )
            )
        )
    }

    private fun songDescription(value: JSONObject, albumName: String?): String? {
        val aliases = value.optJSONArray("alia") ?: value.optJSONArray("alias")
        val aliasText = aliases?.let { array ->
            (0 until array.length())
                .mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
                .joinToString(" / ")
                .takeIf { it.isNotBlank() }
        }
        return listOfNotNull(
            aliasText?.let { "别名：$it" },
            albumName?.takeIf { it.isNotBlank() }?.let { "专辑：$it" }
        ).joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun artistRefs(array: JSONArray?): List<StreamingArtistRef> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val value = array.optJSONObject(index) ?: return@mapNotNull null
            val id = idText(value.opt("id")) ?: return@mapNotNull null
            val name = value.optionalStringLocal("name") ?: return@mapNotNull null
            StreamingArtistRef(StreamingProviderName.NETEASE, id, name)
        }
    }

    private fun playlistRecords(value: Any?, depth: Int = 0): List<JSONObject> {
        if (depth > 8 || value == null) return emptyList()
        return when (value) {
            is JSONArray -> (0 until value.length()).flatMap { playlistRecords(value.opt(it), depth + 1) }
            is JSONObject -> {
                val current = if (idText(value.opt("id") ?: value.opt("playlistId")) != null &&
                    value.optionalStringLocal("name") != null
                ) {
                    listOf(value)
                } else {
                    emptyList()
                }
                current + value.keys().asSequence().toList().flatMap { key ->
                    playlistRecords(value.opt(key), depth + 1)
                }
            }
            else -> emptyList()
        }
    }

    private fun collectUserIds(value: Any?, depth: Int = 0): List<String> {
        if (depth > 8 || value == null) return emptyList()
        return when (value) {
            is JSONArray -> unique((0 until value.length()).flatMap { index ->
                collectUserIds(value.opt(index), depth + 1)
            })
            is JSONObject -> {
                val direct = listOfNotNull(idText(value.opt("userId") ?: value.opt("userid")))
                unique(direct + value.keys().asSequence().toList().flatMap { key ->
                    collectUserIds(value.opt(key), depth + 1)
                })
            }
            else -> emptyList()
        }
    }

    private fun idList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return unique((0 until array.length()).mapNotNull { index ->
            val value = array.opt(index)
            if (value is JSONObject) {
                idText(value.opt("id") ?: value.opt("songId") ?: value.opt("trackId"))
            } else {
                idText(value)
            }
        })
    }

    private fun playbackObjects(value: Any?, depth: Int = 0): List<JSONObject> {
        if (depth > 6 || value == null) return emptyList()
        return when (value) {
            is JSONArray -> (0 until value.length()).flatMap { playbackObjects(value.opt(it), depth + 1) }
            is JSONObject -> {
                val current = if (value.has("url") && (value.has("id") || value.has("songId"))) {
                    listOf(value)
                } else {
                    emptyList()
                }
                current + value.keys().asSequence().toList().flatMap { key ->
                    playbackObjects(value.opt(key), depth + 1)
                }
            }
            else -> emptyList()
        }
    }

    private fun neteaseQuality(quality: StreamingAudioQuality): String {
        return when (quality) {
            StreamingAudioQuality.STANDARD -> "standard"
            StreamingAudioQuality.HIGH -> "higher"
            StreamingAudioQuality.LOSSLESS -> "lossless"
            StreamingAudioQuality.HIRES -> "hires"
        }
    }

    private fun mimeType(url: String, type: String?): String? {
        val lowerType = type?.lowercase()
        return when {
            lowerType == "flac" -> "audio/flac"
            lowerType == "mp3" -> "audio/mpeg"
            lowerType == "aac" -> "audio/aac"
            url.substringBefore('?').lowercase().endsWith(".flac") -> "audio/flac"
            url.substringBefore('?').lowercase().endsWith(".aac") -> "audio/aac"
            url.substringBefore('?').lowercase().endsWith(".m4a") -> "audio/mp4"
            url.substringBefore('?').lowercase().endsWith(".mp3") -> "audio/mpeg"
            else -> null
        }
    }

    private fun imageUrl(value: String?): String? {
        val raw = value?.takeIf { it.isNotBlank() } ?: return null
        return if (raw.contains("?param=")) raw else "$raw?param=600y600"
    }

    private fun idText(value: Any?): String? {
        return when (value) {
            is Number -> value.toLong().toString()
            is String -> value.trim().takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun unique(values: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        values.forEach { if (it.isNotBlank()) seen.add(it) }
        return seen.toList()
    }

    private fun StreamingPlaylistRequest.normalizedLocal(): StreamingPlaylistRequest {
        return copy(
            providerPlaylistId = providerPlaylistId.trim(),
            page = page.coerceAtLeast(1),
            pageSize = pageSize.coerceIn(1, 2000)
        )
    }

    private fun StreamingSearchRequest.normalizedLocal(): StreamingSearchRequest {
        return copy(
            query = query.trim(),
            mediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) },
            page = page.coerceAtLeast(1),
            pageSize = pageSize.coerceIn(1, 50)
        )
    }

    private fun JSONObject.optionalStringLocal(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name).trim().takeIf { it.isNotBlank() } else null
    }

    private fun cleanDisplayText(value: String?): String? {
        return value
            ?.replace(Regex("<[^>]+>"), " ")
            ?.replace("&amp;", "&")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")
            ?.replace("&quot;", "\"")
            ?.replace("&#39;", "'")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optionalIntLocal(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    private fun JSONObject.optionalLongLocal(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name) else null
    }
}
