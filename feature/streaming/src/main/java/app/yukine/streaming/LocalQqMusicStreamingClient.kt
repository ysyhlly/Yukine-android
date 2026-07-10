package app.yukine.streaming

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

interface QqMusicHttpClient {
    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject
    fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject
}

internal fun hasQqPlaybackCredential(cookie: String?): Boolean {
    return qqCookieValue(cookie, "qqmusic_key", "qm_keyst", "psrf_qqaccess_token") != null
}

internal fun qqCookieValue(cookie: String?, vararg names: String): String? {
    val pairs = cookie.orEmpty().split(";")
    for (name in names) {
        val value = pairs.firstNotNullOfOrNull { pair ->
            val trimmed = pair.trim()
            val eq = trimmed.indexOf('=')
            if (eq <= 0) {
                null
            } else if (trimmed.substring(0, eq).trim().equals(name, ignoreCase = true)) {
                trimmed.substring(eq + 1).trim().takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
        if (!value.isNullOrBlank()) return value
    }
    return null
}

class DefaultQqMusicHttpClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000
) : QqMusicHttpClient {
    override fun getJson(url: String, headers: Map<String, String>): JSONObject {
        return request("GET", url, null, headers)
    }

    override fun postJson(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        return request("POST", url, body.toString(), headers + ("Content-Type" to "application/json; charset=utf-8"))
    }

    private fun request(method: String, url: String, body: String?, headers: Map<String, String>): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Origin", "https://y.qq.com")
        connection.setRequestProperty("Referer", "https://y.qq.com/")
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Yukine-Android")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        try {
            if (body != null) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { input ->
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
                    "QQ 音乐请求失败 ($status): ${response.ifBlank { url }}",
                    code = StreamingErrorCode.SOURCE_UNAVAILABLE
                )
            }
            return JSONObject(response)
        } catch (error: IOException) {
            throw StreamingGatewayException(
                "QQ 音乐请求失败: ${error.message ?: url}",
                cause = error,
                code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                retryable = true
            )
        } finally {
            connection.disconnect()
        }
    }
}

class LocalQqMusicStreamingClient(
    private val authStore: StreamingLocalAuthStore? = null,
    private val httpClient: QqMusicHttpClient = DefaultQqMusicHttpClient()
) {
    fun search(request: StreamingSearchRequest): StreamingSearchResult {
        requireCookie()
        val normalized = request.normalizedLocal()
        if (normalized.query.isBlank()) {
            return StreamingSearchResult(
                provider = StreamingProviderName.QQ_MUSIC,
                query = normalized.query,
                page = normalized.page,
                pageSize = normalized.pageSize,
                total = 0
            )
        }
        val tracks = if (normalized.mediaTypes.contains(StreamingMediaType.TRACK)) {
            searchTracks(normalized)
        } else {
            emptyList()
        }
        val smartbox = if (tracks.isEmpty()) smartboxSearch(normalized) else emptyList()
        val allTracks = tracks.ifEmpty { smartbox }
        val total = allTracks.size
        return StreamingSearchResult(
            provider = StreamingProviderName.QQ_MUSIC,
            query = normalized.query,
            page = normalized.page,
            pageSize = normalized.pageSize,
            total = total,
            hasMore = allTracks.size >= normalized.pageSize,
            tracks = allTracks
        )
    }

    fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        val cookie = requireCookie()
        val normalized = request.normalizedLocal()
        val body = playlistDetailBody(normalized.providerPlaylistId, cookie)
        val cdList = body.optJSONArray("cdlist")
        val playlistValue = cdList?.optJSONObject(0) ?: JSONObject()
        val songList = playlistValue.optJSONArray("songlist")
            ?: playlistValue.optJSONArray("songList")
            ?: JSONArray()
        val tracks = (0 until songList.length())
            .mapNotNull { songList.optJSONObject(it) }
            .map(::track)
        val offset = ((normalized.page - 1) * normalized.pageSize).coerceAtLeast(0)
        val pageTracks = tracks.drop(offset).take(normalized.pageSize)
        val playlist = StreamingPlaylist(
            provider = StreamingProviderName.QQ_MUSIC,
            providerPlaylistId = normalized.providerPlaylistId,
            title = playlistValue.optionalStringLocal("dissname")
                ?: playlistValue.optionalStringLocal("title")
                ?: "QQ 音乐歌单",
            description = playlistValue.optionalStringLocal("desc"),
            creator = playlistValue.optionalStringLocal("nick")
                ?: playlistValue.optJSONObject("creator")?.optionalStringLocal("nick"),
            coverUrl = playlistValue.optionalStringLocal("logo")
                ?: playlistValue.optionalStringLocal("picurl"),
            trackCount = tracks.size
        )
        return StreamingPlaylistDetail(
            provider = StreamingProviderName.QQ_MUSIC,
            providerPlaylistId = normalized.providerPlaylistId,
            playlist = playlist,
            tracks = pageTracks,
            total = tracks.size,
            page = normalized.page,
            pageSize = normalized.pageSize,
            hasMore = offset + pageTracks.size < tracks.size
        )
    }

    private fun playlistDetailBody(providerPlaylistId: String, cookie: String): JSONObject {
        val encodedId = encode(providerPlaylistId)
        val disstidBody = runCatching {
            qqPlaylistDetailBody("disstid", encodedId, cookie)
        }.getOrNull()
        if (disstidBody != null && disstidBody.hasPlaylistSongs()) {
            return disstidBody
        }
        return qqPlaylistDetailBody("dirid", encodedId, cookie)
    }

    private fun qqPlaylistDetailBody(parameterName: String, encodedId: String, cookie: String): JSONObject {
        return httpClient.getJson(
            "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" +
                "?type=1&json=1&utf8=1&onlysong=0&$parameterName=$encodedId" +
                "&format=json&g_tk=5381&loginUin=0&hostUin=0&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json",
            defaultHeaders(cookie)
        )
    }

    fun userPlaylists(): List<StreamingPlaylist> {
        val cookie = requireCookie()
        val uin = uinFromCookie(cookie)
        val created = runCatching { createdPlaylists(uin, cookie) }.getOrDefault(emptyList())
        val collected = runCatching { collectedPlaylists(uin, cookie) }.getOrDefault(emptyList())
        val result = (likedPlaylist(uin, cookie, created) + created + collected)
            .distinctBy { it.providerPlaylistId }
        if (result.isEmpty()) {
            throw StreamingGatewayException(
                "QQ 音乐未返回账号歌单，请确认 Cookie 包含 uin 和 qqmusic_key/qm_keyst",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }
        return result
    }

    private fun createdPlaylists(uin: String, cookie: String): List<StreamingPlaylist> {
        val body = httpClient.getJson(
            "https://c.y.qq.com/rsc/fcgi-bin/fcg_user_created_diss" +
                "?format=json&utf8=1&hostUin=0&hostuin=${encode(uin)}&loginUin=0" +
                "&sin=0&size=200&g_tk=5381&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json",
            defaultHeaders(cookie)
        )
        return playlistArray(body, "disslist", "cdlist", "list")
            .mapNotNull { playlistSummary(it) }
    }

    private fun collectedPlaylists(uin: String, cookie: String): List<StreamingPlaylist> {
        val body = httpClient.getJson(
            "https://c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg" +
                "?format=json&ct=20&cid=205360956&userid=${encode(uin)}&reqtype=3" +
                "&sin=0&ein=49&g_tk=5381&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json",
            defaultHeaders(cookie)
        )
        return playlistArray(body, "cdlist", "disslist", "list")
            .mapNotNull { playlistSummary(it) }
    }

    private fun likedPlaylist(
        uin: String,
        cookie: String,
        created: List<StreamingPlaylist>
    ): List<StreamingPlaylist> {
        val existing = created.firstOrNull { it.providerPlaylistId == "201" || it.title.contains("喜欢") }
        if (existing != null) {
            return listOf(existing)
        }
        val body = runCatching {
            httpClient.getJson(
                "https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg" +
                    "?format=json&cid=205360838&userid=${encode(uin)}&reqfrom=1" +
                    "&g_tk=5381&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json",
                defaultHeaders(cookie)
            )
        }.getOrNull() ?: return emptyList()
        val first = body.optJSONObject("data")
            ?.optJSONArray("mymusic")
            ?.optJSONObject(0)
        val likedId = first?.optionalStringLocal("id") ?: "201"
        val count = first?.optionalIntLocal("num0")
        return listOf(
            StreamingPlaylist(
                provider = StreamingProviderName.QQ_MUSIC,
                providerPlaylistId = likedId,
                title = "我喜欢",
                coverUrl = "https://y.gtimg.cn/mediastyle/global/img/cover_like.png",
                trackCount = count
            )
        )
    }

    fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        val cookie = requireCookie()
        val id = request.providerTrackId.trim()
        if (id.isBlank()) {
            throw StreamingGatewayException("QQ 音乐歌曲 ID 为空", code = StreamingErrorCode.UNSUPPORTED_OPERATION)
        }
        val parts = id.split('|')
        val songMid = parts.firstOrNull().orEmpty().ifBlank { id }
        val mediaMid = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: songMid
        var lastMessage = ""
        for (quality in qqQualityFallbacks(request.quality)) {
            for (fileName in qqFileNameCandidates(songMid, mediaMid, quality)) {
                runCatching {
                    return resolvePlaybackWithFileName(id, songMid, quality, fileName, cookie)
                }.onFailure { error ->
                    lastMessage = error.message.orEmpty()
                }
            }
        }
        throw StreamingGatewayException(
            lastMessage.ifBlank { "QQ 音乐未返回可播放链接，可能需要会员或地区不可用" },
            code = StreamingErrorCode.SOURCE_UNAVAILABLE
        )
    }

    private fun resolvePlaybackWithFileName(
        providerTrackId: String,
        songMid: String,
        quality: StreamingAudioQuality,
        fileName: String,
        cookie: String
    ): StreamingPlaybackSource {
        val uin = uinFromCookie(cookie)
        val authst = qqCookieValue(cookie, "qqmusic_key")
        val params = JSONObject()
            .put("guid", qqGuid())
            .put("songmid", JSONArray().put(songMid))
            .put("filename", JSONArray().put(fileName))
            .put("songtype", JSONArray().put(0))
            .put("uin", uin)
            .put("loginflag", 1)
            .put("platform", "20")
        authst?.let { params.put("authst", it) }
        val body = JSONObject()
            .put("loginUin", uin)
            .put(
                "comm",
                JSONObject()
                    .put("format", "json")
                    .put("ct", 24)
                    .put("cv", 0)
                    .put("uin", uin)
            )
            .put(
                "req_0",
                JSONObject()
                    .put("module", "vkey.GetVkeyServer")
                    .put("method", "CgiGetVkey")
                    .put("param", params)
            )
        val result = httpClient.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body, defaultHeaders(cookie))
        val data = qqVkeyData(result)
        val midUrlInfo = qqMidUrlInfo(data)
        val purl = midUrlInfo.optionalStringLocal("purl")
        if (purl.isNullOrBlank()) {
            val message = midUrlInfo.optionalStringLocal("msg")
                ?: data.optionalStringLocal("msg")
                ?: "QQ 音乐未返回可播放链接，可能需要会员或地区不可用"
            throw StreamingGatewayException(message, code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        val sip = data.optJSONArray("sip")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { value -> value.isNotBlank() } }
        }.orEmpty()
        val url = qqPlaybackUrl(purl, sip)
        val resolvedFileName = purl.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() } ?: fileName
        return StreamingPlaybackSource(
            provider = StreamingProviderName.QQ_MUSIC,
            providerTrackId = providerTrackId,
            url = url,
            expiresAtEpochMs = System.currentTimeMillis() + 55 * 60 * 1000L,
            mimeType = mimeType(resolvedFileName),
            bitrate = bitrate(resolvedFileName, quality),
            codec = resolvedFileName.substringAfterLast('.', "").takeIf { it.isNotBlank() },
            headers = mapOf(
                "Referer" to "https://y.qq.com/",
                "User-Agent" to "Mozilla/5.0 Yukine-Android"
            ),
            supportsRange = true
        )
    }

    private fun searchTracks(request: StreamingSearchRequest): List<StreamingTrack> {
        val cookie = requireCookie()
        val uin = uinFromCookie(cookie)
        val body = JSONObject()
            .put("comm", JSONObject().put("ct", "19").put("cv", "1859").put("uin", uin))
            .put(
                "req_1",
                JSONObject()
                    .put("module", "music.search.SearchCgiService")
                    .put("method", "DoSearchForQQMusicMobile")
                    .put(
                        "param",
                        JSONObject()
                            .put("remoteplace", "txt.mqq.all")
                            .put("searchid", "10000001")
                            .put("search_type", 0)
                            .put("query", request.query)
                            .put("page_num", request.page)
                            .put("num_per_page", request.pageSize)
                    )
            )
        val result = httpClient.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body, defaultHeaders(cookie))
        val songs = qqSearchSongArrays(result)
        return songs.flatMap { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }
            .distinctBy { qqTrackIdentity(it) }
            .filter { qqTrackIdentity(it).isNotBlank() }
            .map(::track)
    }

    private fun smartboxSearch(request: StreamingSearchRequest): List<StreamingTrack> {
        val cookie = requireCookie()
        val body = httpClient.getJson(
            "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?format=json&key=${encode(request.query)}",
            defaultHeaders(cookie)
        )
        val songs = body.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("itemlist") ?: JSONArray()
        return (0 until songs.length())
            .mapNotNull { songs.optJSONObject(it) }
            .map { item ->
                val songMid = item.optionalStringLocal("mid") ?: item.optionalStringLocal("id") ?: ""
                val albumMid = item.optionalStringLocal("albummid")
                    ?: item.optionalStringLocal("album_mid")
                    ?: item.optionalStringLocal("albumMid")
                val cover = qqCoverUrl(albumMid)
                StreamingTrack(
                    provider = StreamingProviderName.QQ_MUSIC,
                    providerTrackId = songMid,
                    title = cleanDisplayText(item.optionalStringLocal("name")).orEmpty(),
                    artist = cleanDisplayText(item.optionalStringLocal("singer")).orEmpty(),
                    album = cleanDisplayText(item.optionalStringLocal("album")),
                    coverUrl = cover,
                    coverThumbUrl = cover,
                    qualities = setOf(StreamingAudioQuality.STANDARD, StreamingAudioQuality.HIGH),
                    playable = songMid.isNotBlank(),
                    playbackCandidates = qqCandidates(songMid)
                )
            }
    }

    private fun playlistSummary(value: JSONObject): StreamingPlaylist? {
        val id = value.optionalStringLocal("disstid")
            ?: value.optionalStringLocal("tid")
            ?: value.optionalStringLocal("dissid")
            ?: value.optionalStringLocal("id")
            ?: value.optionalStringLocal("dirid")
            ?: value.optionalStringLocal("dirId")
            ?: return null
        val title = value.optionalStringLocal("dissname")
            ?: value.optionalStringLocal("diss_name")
            ?: value.optionalStringLocal("title")
            ?: value.optionalStringLocal("name")
            ?: "QQ 音乐歌单"
        return StreamingPlaylist(
            provider = StreamingProviderName.QQ_MUSIC,
            providerPlaylistId = id,
            title = title,
            description = value.optionalStringLocal("desc") ?: value.optionalStringLocal("subtitle"),
            creator = value.optionalStringLocal("nick")
                ?: value.optJSONObject("creator")?.optionalStringLocal("nick"),
            coverUrl = value.optionalStringLocal("logo")
                ?: value.optionalStringLocal("diss_cover")
                ?: value.optionalStringLocal("picurl")
                ?: value.optionalStringLocal("picUrl")
                ?: value.optionalStringLocal("cover"),
            trackCount = value.optionalIntLocal("songnum")
                ?: value.optionalIntLocal("song_cnt")
                ?: value.optionalIntLocal("song_num")
                ?: value.optionalIntLocal("total")
                ?: value.optionalIntLocal("count")
        )
    }

    private fun playlistArray(body: JSONObject, vararg names: String): List<JSONObject> {
        val candidates = ArrayList<JSONArray>()
        for (name in names) {
            body.optJSONObject("data")?.optJSONArray(name)?.let { candidates += it }
            body.optJSONArray(name)?.let { candidates += it }
        }
        val array = candidates.firstOrNull { it.length() > 0 } ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun track(value: JSONObject): StreamingTrack {
        val songMid = value.optionalStringLocal("mid")
            ?: value.optionalStringLocal("songmid")
            ?: value.optionalStringLocal("songMid")
            ?: idText(value.opt("id"))
            ?: ""
        val file = value.optJSONObject("file") ?: JSONObject()
        val mediaMid = file.optionalStringLocal("media_mid")
            ?: file.optionalStringLocal("mediaMid")
            ?: value.optionalStringLocal("media_mid")
            ?: value.optionalStringLocal("mediaMid")
            ?: value.optionalStringLocal("strMediaMid")
            ?: value.optionalStringLocal("str_media_mid")
            ?: songMid
        val providerTrackId = if (mediaMid.isNotBlank() && mediaMid != songMid) "$songMid|$mediaMid" else songMid
        val album = value.optJSONObject("album") ?: value.optJSONObject("albumInfo")
        val albumTitle = album?.optionalStringLocal("title")
            ?: album?.optionalStringLocal("name")
            ?: value.optionalStringLocal("albumname")
            ?: value.optionalStringLocal("albumName")
            ?: value.optionalStringLocal("album")
        val albumId = idText(album?.opt("id"))
            ?: idText(value.opt("albumid") ?: value.opt("albumId"))
        val albumMid = album?.optionalStringLocal("mid")
            ?: album?.optionalStringLocal("pmid")
            ?: value.optionalStringLocal("albummid")
            ?: value.optionalStringLocal("album_mid")
            ?: value.optionalStringLocal("albumMid")
        val artists = qqArtistRefs(value.optJSONArray("singer"))
        val artistText = artists.joinToString(" / ") { it.name }
            .ifBlank { value.optionalStringLocal("singer").orEmpty() }
        val cover = qqCoverUrl(albumMid)
        val title = value.optionalStringLocal("title")
            ?: value.optionalStringLocal("name")
            ?: value.optionalStringLocal("songname")
            ?: value.optionalStringLocal("songName")
            ?: value.optionalStringLocal("songorig")
            ?: ""
        val qualities = qqQualities(file)
        val playable = songMid.isNotBlank() && value.optInt("status", 0) >= 0
        return StreamingTrack(
            provider = StreamingProviderName.QQ_MUSIC,
            providerTrackId = providerTrackId,
            title = cleanDisplayText(title).orEmpty(),
            artist = cleanDisplayText(artistText).orEmpty(),
            artists = artists,
            album = cleanDisplayText(albumTitle),
            albumId = albumId,
            durationMs = value.optionalLongLocal("interval")?.times(1000L),
            coverUrl = cover,
            coverThumbUrl = cover,
            qualities = qualities,
            playable = playable,
            unavailableReason = if (playable) null else "QQ 音乐暂不可播放",
            description = value.optionalStringLocal("subtitle") ?: value.optionalStringLocal("desc"),
            lyricSources = listOf(
                StreamingLyricSource(
                    provider = StreamingProviderName.QQ_MUSIC,
                    name = "QQ 音乐歌词",
                    providerTrackId = providerTrackId,
                    priority = 10
                )
            ),
            playbackCandidates = qqCandidates(providerTrackId, qualities)
        )
    }

    private fun qqArtistRefs(array: JSONArray?): List<StreamingArtistRef> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val value = array.optJSONObject(index) ?: return@mapNotNull null
            val id = value.optionalStringLocal("mid") ?: idText(value.opt("id")) ?: return@mapNotNull null
            val name = value.optionalStringLocal("name") ?: value.optionalStringLocal("title") ?: return@mapNotNull null
            StreamingArtistRef(StreamingProviderName.QQ_MUSIC, id, name)
        }
    }

    private fun qqCandidates(
        providerTrackId: String,
        qualities: Set<StreamingAudioQuality> = setOf(StreamingAudioQuality.STANDARD, StreamingAudioQuality.HIGH)
    ): List<StreamingPlaybackCandidate> {
        return qualities.ifEmpty { setOf(StreamingAudioQuality.STANDARD) }
            .sortedBy { it.ordinal }
            .map { quality ->
                StreamingPlaybackCandidate(
                    provider = StreamingProviderName.QQ_MUSIC,
                    quality = quality,
                    label = "QQ 音乐",
                    providerTrackId = providerTrackId,
                    available = providerTrackId.isNotBlank()
                )
            }
    }

    private fun qqQualities(file: JSONObject): Set<StreamingAudioQuality> {
        val qualities = linkedSetOf<StreamingAudioQuality>()
        if (file.optLong("size_128mp3", 0L) > 0 || file.optLong("size_96aac", 0L) > 0) {
            qualities += StreamingAudioQuality.STANDARD
        }
        if (file.optLong("size_320mp3", 0L) > 0 || file.optLong("size_192aac", 0L) > 0) {
            qualities += StreamingAudioQuality.HIGH
        }
        if (file.optLong("size_flac", 0L) > 0 || file.optLong("size_ape", 0L) > 0) {
            qualities += StreamingAudioQuality.LOSSLESS
        }
        if (file.optLong("size_hires", 0L) > 0) {
            qualities += StreamingAudioQuality.HIRES
        }
        return qualities.ifEmpty { setOf(StreamingAudioQuality.STANDARD, StreamingAudioQuality.HIGH) }
    }

    private fun qqFileNameCandidates(
        songMid: String,
        mediaMid: String,
        quality: StreamingAudioQuality
    ): List<String> {
        return when (quality) {
            StreamingAudioQuality.HIRES,
            StreamingAudioQuality.LOSSLESS -> listOf("F000$songMid$mediaMid.flac")
            StreamingAudioQuality.HIGH -> listOf("M800$songMid$mediaMid.mp3", "C600$songMid$mediaMid.m4a")
            StreamingAudioQuality.STANDARD -> listOf("M500$songMid$mediaMid.mp3", "C400$songMid$mediaMid.m4a")
        }.distinct()
    }

    private fun qqQualityFallbacks(quality: StreamingAudioQuality): List<StreamingAudioQuality> {
        val ordered = when (quality) {
            StreamingAudioQuality.HIRES -> listOf(
                StreamingAudioQuality.HIRES,
                StreamingAudioQuality.LOSSLESS,
                StreamingAudioQuality.HIGH,
                StreamingAudioQuality.STANDARD
            )
            StreamingAudioQuality.LOSSLESS -> listOf(
                StreamingAudioQuality.LOSSLESS,
                StreamingAudioQuality.HIGH,
                StreamingAudioQuality.STANDARD
            )
            StreamingAudioQuality.HIGH -> listOf(StreamingAudioQuality.HIGH, StreamingAudioQuality.STANDARD)
            StreamingAudioQuality.STANDARD -> listOf(StreamingAudioQuality.STANDARD)
        }
        return ordered.distinct()
    }

    private fun qqCoverUrl(albumMid: String?): String? {
        val mid = albumMid?.substringBefore('_')?.takeIf { it.isNotBlank() } ?: return null
        return "https://y.gtimg.cn/music/photo_new/T002R500x500M000${mid}.jpg"
    }

    private fun defaultHeaders(cookie: String? = authStore?.cookieHeader(StreamingProviderName.QQ_MUSIC)): Map<String, String> {
        val headers = linkedMapOf(
            "Referer" to "https://y.qq.com/",
            "Origin" to "https://y.qq.com",
            "User-Agent" to "Mozilla/5.0 Yukine-Android"
        )
        cookie?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
        return headers
    }

    private fun requireCookie(): String {
        val cookie = authStore?.cookieHeader(StreamingProviderName.QQ_MUSIC)?.takeIf { it.isNotBlank() }
        if (cookie.isNullOrBlank()) {
            throw StreamingGatewayException(
                "请先登录 QQ 音乐，再使用本机音源",
                code = StreamingErrorCode.AUTH_REQUIRED
            )
        }
        if (!hasQqPlaybackCredential(cookie)) {
            throw StreamingGatewayException(
                "QQ 音乐登录凭证不完整（缺少有效的 qqmusic_key/qm_keyst），请退出登录后重新登录 QQ 音乐",
                code = StreamingErrorCode.AUTH_REQUIRED
            )
        }
        return cookie
    }

    private fun qqVkeyData(result: JSONObject): JSONObject {
        return result.optJSONObject("req_0")?.optJSONObject("data")
            ?: result.optJSONObject("req_0")?.optJSONObject("result")
            ?: result.optJSONObject("data")
            ?: result
    }

    private fun qqMidUrlInfo(data: JSONObject): JSONObject {
        val direct = data.optJSONArray("midurlinfo")?.optJSONObject(0)
        if (direct != null) return direct
        val nested = data.optJSONObject("data")?.optJSONArray("midurlinfo")?.optJSONObject(0)
        if (nested != null) return nested
        return data.optJSONObject("midurlinfo") ?: JSONObject()
    }

    private fun qqSearchSongArrays(result: JSONObject): List<JSONArray> {
        val data = result.optJSONObject("req_1")?.optJSONObject("data")
            ?: result.optJSONObject("data")
            ?: result
        return listOfNotNull(
            data.optJSONObject("body")?.optJSONArray("item_song"),
            data.optJSONObject("body")?.optJSONObject("song")?.optJSONArray("list"),
            data.optJSONObject("song")?.optJSONArray("list"),
            data.optJSONArray("list"),
            data.optJSONArray("item_song")
        ).filter { it.length() > 0 }
    }

    private fun qqTrackIdentity(value: JSONObject): String {
        return value.optionalStringLocal("mid")
            ?: value.optionalStringLocal("songmid")
            ?: value.optionalStringLocal("songMid")
            ?: idText(value.opt("id"))
            ?: ""
    }

    private fun uinFromCookie(cookie: String): String {
        val raw = qqCookieValue(cookie, "uin", "qqmusic_uin", "p_uin", "pt2gguin", "loginUin", "wxuin")
        return Regex("o?(\\d+)").find(raw.orEmpty())?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "0"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun qqGuid(): String =
        UUID.randomUUID().toString().filter { it.isDigit() }.padEnd(10, '0').take(10)

    /**
     * QQ vkey responses commonly contain HTTP SIP endpoints. Android blocks cleartext traffic for
     * QQ domains by design, while the equivalent HTTPS endpoint serves the same signed purl.
     * Prefer an HTTPS SIP, avoid the websocket host, and upgrade the final URL instead of
     * weakening the app-wide network security policy.
     */
    private fun qqPlaybackUrl(purl: String, sip: List<String>): String {
        val rawUrl = if (purl.startsWith("http://", ignoreCase = true) || purl.startsWith("https://", ignoreCase = true)) {
            purl
        } else {
            val playableSip = sip.filterNot(::isQqWebSocketSip)
            val base = playableSip.firstOrNull { it.startsWith("https://", ignoreCase = true) }
                ?: playableSip.firstOrNull()
                ?: "https://isure.stream.qqmusic.qq.com/"
            base.trimEnd('/') + "/" + purl.trimStart('/')
        }
        return if (rawUrl.startsWith("http://", ignoreCase = true)) {
            "https://${rawUrl.substring("http://".length)}"
        } else {
            rawUrl
        }
    }

    private fun isQqWebSocketSip(value: String): Boolean {
        return value.startsWith("http://ws", ignoreCase = true) ||
            value.startsWith("https://ws", ignoreCase = true)
    }

    private fun bitrate(fileName: String, quality: StreamingAudioQuality): Int = when {
        fileName.startsWith("C400", ignoreCase = true) -> 96
        fileName.startsWith("C600", ignoreCase = true) -> 192
        else -> bitrate(quality)
    }

    private fun bitrate(quality: StreamingAudioQuality): Int = when (quality) {
        StreamingAudioQuality.STANDARD -> 128
        StreamingAudioQuality.HIGH -> 320
        StreamingAudioQuality.LOSSLESS -> 900
        StreamingAudioQuality.HIRES -> 1600
    }

    private fun mimeType(fileName: String): String = when {
        fileName.endsWith(".flac", ignoreCase = true) -> "audio/flac"
        fileName.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
        else -> "audio/mpeg"
    }

    private fun idText(value: Any?): String? = when (value) {
        is Number -> value.toLong().toString()
        is String -> value.trim().takeIf { it.isNotBlank() }
        else -> null
    }

    private fun StreamingSearchRequest.normalizedLocal(): StreamingSearchRequest {
        return copy(
            provider = StreamingProviderName.QQ_MUSIC,
            query = query.trim(),
            mediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) },
            page = page.coerceAtLeast(1),
            pageSize = pageSize.coerceIn(1, 50)
        )
    }

    private fun StreamingPlaylistRequest.normalizedLocal(): StreamingPlaylistRequest {
        return copy(
            provider = StreamingProviderName.QQ_MUSIC,
            providerPlaylistId = providerPlaylistId.trim(),
            page = page.coerceAtLeast(1),
            pageSize = pageSize.coerceIn(1, 2000)
        )
    }

    private fun JSONObject.optionalStringLocal(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name).trim().takeIf { it.isNotBlank() } else null
    }

    private fun JSONObject.hasPlaylistSongs(): Boolean {
        val playlistValue = optJSONArray("cdlist")?.optJSONObject(0) ?: return false
        val songList = playlistValue.optJSONArray("songlist")
            ?: playlistValue.optJSONArray("songList")
            ?: return false
        return songList.length() > 0
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

    private fun JSONObject.optionalLongLocal(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name) else null
    }

    private fun JSONObject.optionalIntLocal(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }
}
