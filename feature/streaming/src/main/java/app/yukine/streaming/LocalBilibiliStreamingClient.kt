package app.yukine.streaming

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

private const val BILIBILI_WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

interface BilibiliHttpClient {
    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject

    fun resolveRedirect(url: String): String
}

class DefaultBilibiliHttpClient(
    private val connectTimeoutMs: Int = 4_000,
    private val readTimeoutMs: Int = 8_000,
    private val maxResponseChars: Int = 4 * 1024 * 1024
) : BilibiliHttpClient {
    override fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val parsedUrl = requireHttpsUrl(url)
        val connection = parsedUrl.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", BILIBILI_WEB_USER_AGENT)
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use(::readBounded)
            }.orEmpty()
            if (status !in 200..299) {
                throw StreamingGatewayException(
                    "哔哩哔哩请求失败 ($status)",
                    code = when (status) {
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        HttpURLConnection.HTTP_FORBIDDEN -> StreamingErrorCode.AUTH_REQUIRED
                        429 -> StreamingErrorCode.RATE_LIMITED
                        else -> StreamingErrorCode.SOURCE_UNAVAILABLE
                    },
                    retryable = status == 429 || status >= 500
                )
            }
            return JSONObject(body)
        } catch (error: IOException) {
            throw StreamingGatewayException(
                "哔哩哔哩请求失败：${error.message ?: parsedUrl.host}",
                cause = error,
                code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                retryable = true
            )
        } finally {
            connection.disconnect()
        }
    }

    override fun resolveRedirect(url: String): String {
        var current = requireHttpsUrl(url)
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            if (!isAllowedRedirectHost(current.host)) {
                throw StreamingGatewayException(
                    "不支持的哔哩哔哩短链域名",
                    code = StreamingErrorCode.SOURCE_UNAVAILABLE
                )
            }
            val connection = current.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", BILIBILI_WEB_USER_AGENT)
            try {
                val status = connection.responseCode
                if (status !in REDIRECT_CODES) {
                    if (status in 200..299 && isBilibiliHost(current.host)) return current.toString()
                    throw StreamingGatewayException(
                        "哔哩哔哩短链解析失败 ($status)",
                        code = StreamingErrorCode.SOURCE_UNAVAILABLE
                    )
                }
                if (redirectIndex >= MAX_REDIRECTS) {
                    throw StreamingGatewayException(
                        "哔哩哔哩短链重定向次数过多",
                        code = StreamingErrorCode.SOURCE_UNAVAILABLE
                    )
                }
                val location = connection.getHeaderField("Location").orEmpty()
                current = requireHttpsUrl(URL(current, location).toString())
            } catch (error: IOException) {
                throw StreamingGatewayException(
                    "哔哩哔哩短链解析失败：${error.message ?: current.host}",
                    cause = error,
                    code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                    retryable = true
                )
            } finally {
                connection.disconnect()
            }
        }
        throw StreamingGatewayException("哔哩哔哩短链解析失败", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
    }

    private fun readBounded(reader: BufferedReader): String {
        return buildString {
            val buffer = CharArray(8 * 1024)
            var count = reader.read(buffer)
            while (count >= 0) {
                if (length + count > maxResponseChars) {
                    throw StreamingGatewayException(
                        "哔哩哔哩响应过大",
                        code = StreamingErrorCode.SOURCE_UNAVAILABLE
                    )
                }
                append(buffer, 0, count)
                count = reader.read(buffer)
            }
        }
    }

    private fun requireHttpsUrl(value: String): URL {
        val url = runCatching { URL(value) }.getOrNull()
            ?: throw StreamingGatewayException("哔哩哔哩链接无效", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw StreamingGatewayException("仅支持 HTTPS 哔哩哔哩链接", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        return url
    }

    private fun isAllowedRedirectHost(host: String): Boolean =
        host.equals("b23.tv", ignoreCase = true) || isBilibiliHost(host)

    private fun isBilibiliHost(host: String): Boolean =
        host.equals("bilibili.com", ignoreCase = true) ||
            host.endsWith(".bilibili.com", ignoreCase = true)

    private companion object {
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

data class BilibiliAccount(
    val mid: Long,
    val displayName: String,
    val avatarUrl: String?
)

class LocalBilibiliStreamingClient(
    private val authStore: StreamingLocalAuthStore? = null,
    private val httpClient: BilibiliHttpClient = DefaultBilibiliHttpClient()
) {
    fun verifySession(): BilibiliAccount {
        val data = apiData(getApi("/x/web-interface/nav", requiresAuth = true))
        if (!data.optBoolean("isLogin", false)) {
            throw authRequired()
        }
        val mid = data.optLong("mid", 0L)
        if (mid <= 0L) throw authRequired()
        return BilibiliAccount(
            mid = mid,
            displayName = data.optString("uname").ifBlank { "哔哩哔哩用户" },
            avatarUrl = normalizeImageUrl(data.optString("face"))
        )
    }

    fun userPlaylists(): List<StreamingPlaylist> {
        val account = verifySession()
        val data = apiData(
            getApi(
                "/x/v3/fav/folder/created/list-all",
                mapOf("up_mid" to account.mid.toString()),
                requiresAuth = true
            )
        )
        val folders = data.optJSONArray("list") ?: JSONArray()
        return (0 until folders.length())
            .mapNotNull(folders::optJSONObject)
            .mapNotNull { folder ->
                val id = folder.optLong("id", 0L)
                if (id <= 0L) return@mapNotNull null
                StreamingPlaylist(
                    provider = StreamingProviderName.BILIBILI,
                    providerPlaylistId = BilibiliTarget.Favorite(id).providerPlaylistId,
                    title = folder.optString("title").ifBlank { "收藏夹 $id" },
                    creator = account.displayName,
                    trackCount = folder.optInt("media_count", -1).takeIf { it >= 0 }
                )
            }
    }

    fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        requireBilibiliProvider(request.provider)
        val target = resolveTarget(request.providerPlaylistId)
        return when (target) {
            is BilibiliTarget.Video -> videoPlaylist(request, target)
            is BilibiliTarget.Favorite -> favoritePlaylist(request, target)
            is BilibiliTarget.ShortLink -> error("Short links are resolved before dispatch")
        }
    }

    fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        requireBilibiliProvider(request.provider)
        val key = parseTrackKey(request.providerTrackId)
        val view = viewData(BilibiliTarget.Video(key.videoId))
        val bvid = view.optString("bvid").ifBlank {
            throw StreamingGatewayException("视频缺少 BV 号", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        val cid = key.cid ?: firstCid(view)
        val data = apiData(
            getApi(
                "/x/player/playurl",
                mapOf(
                    "bvid" to bvid,
                    "cid" to cid.toString(),
                    "qn" to "127",
                    "fnval" to "4048",
                    "fourk" to "1"
                ),
                referer = "https://www.bilibili.com/video/$bvid"
            )
        )
        val audio = selectAudio(data.optJSONObject("dash")?.optJSONArray("audio"), request.quality)
        val url = audio.optString("baseUrl").ifBlank { audio.optString("base_url") }
        if (!url.startsWith("https://")) {
            throw StreamingGatewayException("哔哩哔哩未返回可播放音频", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        val bandwidth = audio.optInt("bandwidth", 0)
        val mimeType = audio.optString("mimeType").ifBlank { audio.optString("mime_type") }
            .takeIf { it.isNotBlank() }
        val codec = audio.optString("codecs").takeIf { it.isNotBlank() }
        return StreamingPlaybackSource(
            provider = StreamingProviderName.BILIBILI,
            providerTrackId = request.providerTrackId,
            url = url,
            expiresAtEpochMs = expiryEpochMs(url),
            mimeType = mimeType,
            bitrate = bandwidth.takeIf { it > 0 }?.div(1000),
            codec = codec,
            headers = mapOf(
                "Referer" to "https://www.bilibili.com/video/$bvid",
                "User-Agent" to BILIBILI_WEB_USER_AGENT
            ),
            requiresProxy = false,
            supportsRange = true
        )
    }

    private fun videoPlaylist(
        request: StreamingPlaylistRequest,
        target: BilibiliTarget.Video
    ): StreamingPlaylistDetail {
        val view = viewData(target)
        val pages = view.optJSONArray("pages") ?: JSONArray()
        val allTracks = (0 until pages.length())
            .mapNotNull(pages::optJSONObject)
            .filter { page -> target.page == null || page.optInt("page") == target.page }
            .map { page -> videoPageTrack(view, page, pages.length()) }
        if (target.page != null && allTracks.isEmpty()) {
            throw StreamingGatewayException("视频不存在第 ${target.page} P", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        val tracks = paginate(allTracks, request.page, request.pageSize)
        val bvid = view.optString("bvid")
        val title = view.optString("title").ifBlank { bvid }
        return StreamingPlaylistDetail(
            provider = StreamingProviderName.BILIBILI,
            providerPlaylistId = target.providerPlaylistId,
            playlist = StreamingPlaylist(
                provider = StreamingProviderName.BILIBILI,
                providerPlaylistId = target.providerPlaylistId,
                title = if (target.page == null) title else allTracks.first().title,
                description = view.optString("desc").takeIf { it.isNotBlank() },
                creator = view.optJSONObject("owner")?.optString("name")?.takeIf { it.isNotBlank() },
                coverUrl = normalizeImageUrl(view.optString("pic")),
                trackCount = allTracks.size
            ),
            tracks = tracks,
            total = allTracks.size,
            page = request.page,
            pageSize = request.pageSize,
            hasMore = request.page * request.pageSize < allTracks.size
        )
    }

    private fun favoritePlaylist(
        request: StreamingPlaylistRequest,
        target: BilibiliTarget.Favorite
    ): StreamingPlaylistDetail {
        val data = apiData(
            getApi(
                "/x/v3/fav/resource/list",
                mapOf(
                    "media_id" to target.mediaId.toString(),
                    "pn" to request.page.toString(),
                    "ps" to request.pageSize.coerceIn(1, 40).toString(),
                    "platform" to "web"
                ),
                requiresAuth = true
            )
        )
        val info = data.optJSONObject("info") ?: JSONObject()
        val medias = data.optJSONArray("medias") ?: JSONArray()
        val title = info.optString("title").ifBlank { "哔哩哔哩收藏夹" }
        val tracks = (0 until medias.length())
            .mapNotNull(medias::optJSONObject)
            .mapNotNull { media -> favoriteTrack(media, title) }
        val total = info.optInt("media_count", -1).takeIf { it >= 0 }
            ?: data.optInt("count", tracks.size)
        return StreamingPlaylistDetail(
            provider = StreamingProviderName.BILIBILI,
            providerPlaylistId = target.providerPlaylistId,
            playlist = StreamingPlaylist(
                provider = StreamingProviderName.BILIBILI,
                providerPlaylistId = target.providerPlaylistId,
                title = title,
                description = info.optString("intro").takeIf { it.isNotBlank() },
                creator = info.optJSONObject("upper")?.optString("name")?.takeIf { it.isNotBlank() },
                coverUrl = normalizeImageUrl(info.optString("cover")),
                trackCount = total
            ),
            tracks = tracks,
            total = total,
            page = request.page,
            pageSize = request.pageSize,
            hasMore = data.optBoolean("has_more", request.page * request.pageSize < total)
        )
    }

    private fun videoPageTrack(view: JSONObject, page: JSONObject, pageCount: Int): StreamingTrack {
        val bvid = view.optString("bvid")
        val cid = page.optLong("cid", 0L)
        if (bvid.isBlank() || cid <= 0L) {
            throw StreamingGatewayException("视频分 P 信息不完整", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        val videoTitle = view.optString("title").ifBlank { bvid }
        val partTitle = page.optString("part").ifBlank { "P${page.optInt("page", 1)}" }
        val owner = view.optJSONObject("owner")
        return StreamingTrack(
            provider = StreamingProviderName.BILIBILI,
            providerTrackId = trackId(bvid, cid),
            title = if (pageCount > 1) "$videoTitle · $partTitle" else videoTitle,
            artist = owner?.optString("name").orEmpty().ifBlank { "哔哩哔哩 UP 主" },
            artists = owner?.optLong("mid", 0L)?.takeIf { it > 0L }?.let { mid ->
                listOf(
                    StreamingArtistRef(
                        provider = StreamingProviderName.BILIBILI,
                        providerArtistId = mid.toString(),
                        name = owner.optString("name").ifBlank { "哔哩哔哩 UP 主" }
                    )
                )
            }.orEmpty(),
            album = videoTitle,
            durationMs = page.optLong("duration", 0L).takeIf { it > 0L }?.times(1000L),
            coverUrl = normalizeImageUrl(page.optString("first_frame").ifBlank { view.optString("pic") }),
            coverThumbUrl = normalizeImageUrl(view.optString("pic")),
            qualities = ALL_QUALITIES,
            description = view.optString("desc").takeIf { it.isNotBlank() }
        )
    }

    private fun favoriteTrack(media: JSONObject, playlistTitle: String): StreamingTrack? {
        if (media.optInt("type", 2) != 2) return null
        val bvid = media.optString("bvid").takeIf { it.isNotBlank() }
            ?: media.optLong("id", 0L).takeIf { it > 0L }?.let { "av$it" }
            ?: return null
        val cid = media.optJSONObject("ugc")?.optLong("first_cid", 0L)?.takeIf { it > 0L }
        val upper = media.optJSONObject("upper")
        return StreamingTrack(
            provider = StreamingProviderName.BILIBILI,
            providerTrackId = trackId(bvid, cid),
            title = media.optString("title").ifBlank { bvid },
            artist = upper?.optString("name").orEmpty().ifBlank { "哔哩哔哩 UP 主" },
            artists = upper?.optLong("mid", 0L)?.takeIf { it > 0L }?.let { mid ->
                listOf(
                    StreamingArtistRef(
                        provider = StreamingProviderName.BILIBILI,
                        providerArtistId = mid.toString(),
                        name = upper.optString("name").ifBlank { "哔哩哔哩 UP 主" }
                    )
                )
            }.orEmpty(),
            album = playlistTitle,
            durationMs = media.optLong("duration", 0L).takeIf { it > 0L }?.times(1000L),
            coverUrl = normalizeImageUrl(media.optString("cover")),
            qualities = ALL_QUALITIES,
            playable = media.optInt("attr", 0) != 9,
            unavailableReason = if (media.optInt("attr", 0) == 9) "视频已失效" else null,
            description = media.optString("intro").takeIf { it.isNotBlank() }
        )
    }

    private fun resolveTarget(providerPlaylistId: String): BilibiliTarget {
        val parsed = BilibiliLinkParser.parse(providerPlaylistId)
            ?: throw StreamingGatewayException("无法识别哔哩哔哩链接", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        if (parsed !is BilibiliTarget.ShortLink) return parsed
        val finalUrl = httpClient.resolveRedirect(parsed.url)
        return BilibiliLinkParser.parse(finalUrl)
            ?.takeUnless { it is BilibiliTarget.ShortLink }
            ?: throw StreamingGatewayException("哔哩哔哩短链目标无效", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
    }

    private fun viewData(target: BilibiliTarget.Video): JSONObject {
        val query = if (target.videoId.startsWith("BV")) {
            mapOf("bvid" to target.videoId)
        } else {
            mapOf("aid" to target.videoId.removePrefix("av"))
        }
        return apiData(getApi("/x/web-interface/view", query))
    }

    private fun firstCid(view: JSONObject): Long {
        val cid = view.optJSONArray("pages")?.optJSONObject(0)?.optLong("cid", 0L) ?: 0L
        if (cid <= 0L) {
            throw StreamingGatewayException("视频缺少可播放分 P", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        return cid
    }

    private fun selectAudio(audio: JSONArray?, quality: StreamingAudioQuality): JSONObject {
        val choices = audio?.let { array ->
            (0 until array.length()).mapNotNull(array::optJSONObject)
                .filter { item ->
                    item.optString("baseUrl").isNotBlank() || item.optString("base_url").isNotBlank()
                }
                .sortedBy { it.optInt("bandwidth", 0) }
        }.orEmpty()
        if (choices.isEmpty()) {
            throw StreamingGatewayException("哔哩哔哩未返回 DASH 音频", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        return when (quality) {
            StreamingAudioQuality.STANDARD -> choices.first()
            StreamingAudioQuality.HIGH -> choices[(choices.lastIndex / 2).coerceAtLeast(0)]
            StreamingAudioQuality.LOSSLESS,
            StreamingAudioQuality.HIRES -> choices.last()
        }
    }

    private fun getApi(
        path: String,
        query: Map<String, String> = emptyMap(),
        referer: String = "https://www.bilibili.com/",
        requiresAuth: Boolean = false
    ): JSONObject {
        val cookie = if (requiresAuth) requireCookie() else optionalCookie()
        val suffix = query.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        val headers = linkedMapOf(
            "Referer" to referer,
            "Origin" to "https://www.bilibili.com"
        )
        cookie?.let { headers["Cookie"] = it }
        return httpClient.getJson(
            "$API_BASE$path$suffix",
            headers
        )
    }

    private fun apiData(root: JSONObject): JSONObject {
        val code = root.optInt("code", Int.MIN_VALUE)
        if (code == 0) return root.optJSONObject("data") ?: JSONObject()
        val message = root.optString("message").ifBlank { root.optString("msg") }
        throw StreamingGatewayException(
            message.ifBlank { "哔哩哔哩接口返回错误 $code" },
            code = when (code) {
                -101, -111 -> StreamingErrorCode.AUTH_REQUIRED
                -352, -412 -> StreamingErrorCode.RATE_LIMITED
                -10403, 6002105 -> StreamingErrorCode.REGION_BLOCKED
                else -> StreamingErrorCode.SOURCE_UNAVAILABLE
            },
            retryable = code == -352 || code == -412
        )
    }

    private fun requireCookie(): String {
        return optionalCookie() ?: throw authRequired()
    }

    private fun optionalCookie(): String? =
        authStore
            ?.takeIf { it.connected(StreamingProviderName.BILIBILI) }
            ?.cookieHeader(StreamingProviderName.BILIBILI)
            ?.takeIf { it.isNotBlank() }

    private fun authRequired(): StreamingGatewayException =
        StreamingGatewayException("请先登录哔哩哔哩", code = StreamingErrorCode.AUTH_REQUIRED)

    private fun requireBilibiliProvider(provider: StreamingProviderName) {
        if (provider != StreamingProviderName.BILIBILI) {
            throw StreamingGatewayException("音源不匹配", code = StreamingErrorCode.UNSUPPORTED_OPERATION)
        }
    }

    private fun parseTrackKey(value: String): BilibiliTrackKey {
        val match = Regex("""(?i)^video:(BV[0-9A-Za-z]{10}|av\d+)(?::cid:(\d+))?$""")
            .matchEntire(value.trim())
            ?: throw StreamingGatewayException("哔哩哔哩歌曲 ID 无效", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        return BilibiliTrackKey(
            videoId = match.groupValues[1].let {
                if (it.startsWith("BV", ignoreCase = true)) "BV${it.substring(2)}" else it.lowercase()
            },
            cid = match.groupValues.getOrNull(2)?.toLongOrNull()?.takeIf { it > 0L }
        )
    }

    private fun trackId(videoId: String, cid: Long?): String =
        buildString {
            append("video:")
            append(videoId)
            cid?.let {
                append(":cid:")
                append(it)
            }
        }

    private fun <T> paginate(values: List<T>, page: Int, pageSize: Int): List<T> {
        val from = ((page.coerceAtLeast(1) - 1) * pageSize.coerceAtLeast(1)).coerceAtMost(values.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(values.size)
        return values.subList(from, to)
    }

    private fun normalizeImageUrl(value: String?): String? {
        val raw = value?.trim().orEmpty()
        return when {
            raw.isBlank() -> null
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("https://") -> raw
            raw.startsWith("http://") -> "https://${raw.removePrefix("http://")}"
            else -> raw
        }
    }

    private fun expiryEpochMs(url: String): Long? {
        val seconds = Regex("""[?&]deadline=(\d+)""").find(url)
            ?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: return null
        return seconds * 1000L
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class BilibiliTrackKey(
        val videoId: String,
        val cid: Long?
    )

    private companion object {
        const val API_BASE = "https://api.bilibili.com"
        val ALL_QUALITIES = StreamingAudioQuality.entries.toSet()
    }
}
