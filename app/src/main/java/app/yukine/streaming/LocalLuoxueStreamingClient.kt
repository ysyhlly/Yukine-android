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

interface LuoxueHttpClient {
    fun getText(url: String, headers: Map<String, String> = emptyMap()): String
}

class DefaultLuoxueHttpClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000
) : LuoxueHttpClient {
    override fun getText(url: String, headers: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Referer", "https://www.kuwo.cn/")
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Yukine-Android")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        try {
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
                    "LX/洛雪音源请求失败 ($status): ${response.ifBlank { url }}",
                    code = StreamingErrorCode.SOURCE_UNAVAILABLE
                )
            }
            return response
        } catch (error: IOException) {
            throw StreamingGatewayException(
                "LX/洛雪音源请求失败: ${error.message ?: url}",
                cause = error,
                code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                retryable = true
            )
        } finally {
            connection.disconnect()
        }
    }
}

class LocalLuoxueStreamingClient(
    private val httpClient: LuoxueHttpClient = DefaultLuoxueHttpClient()
) {
    fun search(request: StreamingSearchRequest): StreamingSearchResult {
        val normalized = request.normalizedLocal()
        if (normalized.query.isBlank() || !normalized.mediaTypes.contains(StreamingMediaType.TRACK)) {
            return StreamingSearchResult(
                provider = StreamingProviderName.LUOXUE,
                query = normalized.query,
                page = normalized.page,
                pageSize = normalized.pageSize,
                total = 0
            )
        }
        val tracks = searchKuwo(normalized)
        return StreamingSearchResult(
            provider = StreamingProviderName.LUOXUE,
            query = normalized.query,
            page = normalized.page,
            pageSize = normalized.pageSize,
            total = tracks.size,
            hasMore = tracks.size >= normalized.pageSize,
            tracks = tracks
        )
    }

    fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        val normalized = request.normalizedLocal()
        val sourceId = parseLuoxueId(normalized.providerPlaylistId)
        if (sourceId.source != "kw") {
            throw StreamingGatewayException(
                "当前 LX 本机版先支持酷我子源歌单，其他子源后续接入",
                code = StreamingErrorCode.UNSUPPORTED_OPERATION
            )
        }
        val body = httpClient.getText(
            "https://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${encode(sourceId.id)}&pn=${normalized.page - 1}" +
                "&rn=${normalized.pageSize.coerceIn(1, 2000)}&encode=utf8&keyset=pl2012&vipver=MUSIC_9.1.1.2_W1",
            defaultHeaders()
        )
        val json = parseRelaxedJson(body)
        val musicList = json.optJSONArray("musiclist") ?: json.optJSONArray("abslist") ?: JSONArray()
        val tracks = (0 until musicList.length())
            .mapNotNull { musicList.optJSONObject(it) }
            .map(::kuwoTrack)
        val playlist = StreamingPlaylist(
            provider = StreamingProviderName.LUOXUE,
            providerPlaylistId = "kw:${sourceId.id}",
            title = json.optionalStringLocal("title")
                ?: json.optionalStringLocal("name")
                ?: "LX/酷我歌单",
            description = json.optionalStringLocal("info"),
            creator = json.optionalStringLocal("uname"),
            coverUrl = kuwoImage(json.optionalStringLocal("pic") ?: json.optionalStringLocal("picUrl")),
            trackCount = json.optionalIntLocal("total") ?: tracks.size
        )
        val total = playlist.trackCount ?: tracks.size
        return StreamingPlaylistDetail(
            provider = StreamingProviderName.LUOXUE,
            providerPlaylistId = playlist.providerPlaylistId,
            playlist = playlist,
            tracks = tracks,
            total = total,
            page = normalized.page,
            pageSize = normalized.pageSize,
            hasMore = normalized.page * normalized.pageSize < total
        )
    }

    fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        val sourceId = parseLuoxueId(request.providerTrackId)
        if (sourceId.source != "kw") {
            throw StreamingGatewayException(
                "当前 LX 本机播放先支持酷我子源，其他子源后续接入",
                code = StreamingErrorCode.UNSUPPORTED_OPERATION
            )
        }
        val body = httpClient.getText(
            "https://antiserver.kuwo.cn/anti.s?type=convert_url3&rid=MUSIC_${encode(sourceId.id)}" +
                "&format=${kuwoFormat(request.quality)}&response=url",
            defaultHeaders()
        )
        val url = parsePlaybackUrl(body)
        if (url.isBlank()) {
            throw StreamingGatewayException(
                "LX/酷我未返回可播放链接，可能需要会员或音源不可用",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }
        return StreamingPlaybackSource(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = "kw:${sourceId.id}",
            url = url,
            expiresAtEpochMs = System.currentTimeMillis() + 50 * 60 * 1000L,
            mimeType = mimeType(url),
            bitrate = bitrate(request.quality),
            codec = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.isNotBlank() },
            headers = mapOf(
                "Referer" to "https://www.kuwo.cn/",
                "User-Agent" to "Mozilla/5.0 Yukine-Android"
            ),
            supportsRange = true
        )
    }

    private fun searchKuwo(request: StreamingSearchRequest): List<StreamingTrack> {
        val page = request.page - 1
        val text = httpClient.getText(
            "https://search.kuwo.cn/r.s?all=${encode(request.query)}&ft=music&itemset=web_2013" +
                "&client=kt&pn=$page&rn=${request.pageSize}&rformat=json&encoding=utf8",
            defaultHeaders()
        )
        val body = parseRelaxedJson(text)
        val songs = body.optJSONArray("abslist") ?: JSONArray()
        return (0 until songs.length())
            .mapNotNull { songs.optJSONObject(it) }
            .map(::kuwoTrack)
    }

    private fun kuwoTrack(value: JSONObject): StreamingTrack {
        val rawId = value.optionalStringLocal("DC_TARGETID")
            ?: value.optionalStringLocal("MUSICRID")?.removePrefix("MUSIC_")
            ?: value.optionalStringLocal("rid")?.removePrefix("MUSIC_")
            ?: value.optionalStringLocal("id")
            ?: ""
        val providerTrackId = if (rawId.startsWith("kw:")) rawId else "kw:$rawId"
        val title = htmlDecode(
            value.optionalStringLocal("NAME")
                ?: value.optionalStringLocal("name")
                ?: value.optionalStringLocal("title")
                ?: ""
        )
        val artist = htmlDecode(value.optionalStringLocal("ARTIST") ?: value.optionalStringLocal("artist") ?: "")
        val album = htmlDecode(value.optionalStringLocal("ALBUM") ?: value.optionalStringLocal("album") ?: "")
        val albumId = value.optionalStringLocal("ALBUMID") ?: value.optionalStringLocal("albumId")
        val cover = kuwoImage(
            value.optionalStringLocal("web_albumpic_short")
                ?: value.optionalStringLocal("albumpic")
                ?: value.optionalStringLocal("pic")
        )
        val qualities = kuwoQualities(value)
        val playable = rawId.isNotBlank()
        return StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = providerTrackId,
            title = title,
            artist = artist,
            artists = artist.takeIf { it.isNotBlank() }?.let {
                listOf(StreamingArtistRef(StreamingProviderName.LUOXUE, "kw:$it", it))
            }.orEmpty(),
            album = album.takeIf { it.isNotBlank() },
            albumId = albumId,
            durationMs = value.optionalLongLocal("DURATION")?.times(1000L)
                ?: value.optionalLongLocal("duration")?.times(1000L),
            coverUrl = cover,
            coverThumbUrl = cover,
            qualities = qualities,
            playable = playable,
            unavailableReason = if (playable) null else "LX/酷我歌曲 ID 为空",
            description = listOfNotNull(
                "LX/洛雪 · 酷我子源",
                album.takeIf { it.isNotBlank() }?.let { "专辑：$it" }
            ).joinToString("\n"),
            lyricSources = listOf(
                StreamingLyricSource(
                    provider = StreamingProviderName.LUOXUE,
                    name = "LX/酷我歌词",
                    providerTrackId = providerTrackId,
                    priority = 20
                )
            ),
            playbackCandidates = qualities.ifEmpty { setOf(StreamingAudioQuality.STANDARD) }
                .sortedBy { it.ordinal }
                .map { quality ->
                    StreamingPlaybackCandidate(
                        provider = StreamingProviderName.LUOXUE,
                        quality = quality,
                        label = "LX/酷我",
                        providerTrackId = providerTrackId,
                        available = playable
                    )
                }
        )
    }

    private fun parsePlaybackUrl(body: String): String {
        val trimmed = body.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return runCatching {
            val json = parseRelaxedJson(trimmed)
            json.optionalStringLocal("url")
                ?: json.optJSONObject("data")?.optionalStringLocal("url")
                ?: ""
        }.getOrDefault("")
    }

    private fun parseRelaxedJson(value: String): JSONObject {
        val trimmed = value.trim()
        return try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            // Kuwo's legacy search endpoint returns Python-like object text.
            JSONObject(
                trimmed
                    .replace('\'', '"')
                    .replace(":None", ":null")
                    .replace(":True", ":true")
                    .replace(":False", ":false")
            )
        }
    }

    private fun parseLuoxueId(value: String): SourceId {
        val raw = value.trim()
        val source = raw.substringBefore(':', "kw").lowercase()
        val id = if (raw.contains(':')) raw.substringAfter(':') else raw
        val normalizedSource = when (source) {
            "kuwo" -> "kw"
            "lx", "luoxue" -> "kw"
            else -> source
        }
        return SourceId(normalizedSource, id.trim())
    }

    private fun kuwoImage(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("albumcover/") -> "https://img4.kuwo.cn/star/albumcover/500/$raw"
            else -> "https://img4.kuwo.cn/star/albumcover/500/$raw"
        }
    }

    private fun kuwoQualities(value: JSONObject): Set<StreamingAudioQuality> {
        val formats = value.optionalStringLocal("FORMATS").orEmpty().uppercase()
        val minfo = value.optionalStringLocal("MINFO").orEmpty().lowercase()
        val qualities = linkedSetOf<StreamingAudioQuality>()
        if (formats.contains("MP3128") || minfo.contains("bitrate:128") || formats.contains("AAC48")) {
            qualities += StreamingAudioQuality.STANDARD
        }
        if (formats.contains("MP3H") || minfo.contains("bitrate:320")) {
            qualities += StreamingAudioQuality.HIGH
        }
        if (formats.contains("ALFLAC") || formats.contains("FLAC") || minfo.contains("format:flac")) {
            qualities += StreamingAudioQuality.LOSSLESS
        }
        if (formats.contains("HIR") || formats.contains("ZPGA") || formats.contains("DTSX")) {
            qualities += StreamingAudioQuality.HIRES
        }
        return qualities.ifEmpty { setOf(StreamingAudioQuality.STANDARD, StreamingAudioQuality.HIGH) }
    }

    private fun kuwoFormat(quality: StreamingAudioQuality): String = when (quality) {
        StreamingAudioQuality.HIRES,
        StreamingAudioQuality.LOSSLESS -> "flac|mp3|aac"
        StreamingAudioQuality.HIGH -> "mp3|aac"
        StreamingAudioQuality.STANDARD -> "aac|mp3"
    }

    private fun defaultHeaders(): Map<String, String> = mapOf(
        "Referer" to "https://www.kuwo.cn/",
        "User-Agent" to "Mozilla/5.0 Yukine-Android"
    )

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun bitrate(quality: StreamingAudioQuality): Int = when (quality) {
        StreamingAudioQuality.STANDARD -> 128
        StreamingAudioQuality.HIGH -> 320
        StreamingAudioQuality.LOSSLESS -> 900
        StreamingAudioQuality.HIRES -> 1600
    }

    private fun mimeType(url: String): String = when {
        url.substringBefore('?').endsWith(".flac", ignoreCase = true) -> "audio/flac"
        url.substringBefore('?').endsWith(".aac", ignoreCase = true) -> "audio/aac"
        url.substringBefore('?').endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
        else -> "audio/mpeg"
    }

    private fun htmlDecode(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun StreamingSearchRequest.normalizedLocal(): StreamingSearchRequest {
        return copy(
            provider = StreamingProviderName.LUOXUE,
            query = query.trim(),
            mediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) },
            page = page.coerceAtLeast(1),
            pageSize = pageSize.coerceIn(1, 50)
        )
    }

    private fun StreamingPlaylistRequest.normalizedLocal(): StreamingPlaylistRequest {
        return copy(
            provider = StreamingProviderName.LUOXUE,
            providerPlaylistId = providerPlaylistId.trim(),
            page = page.coerceAtLeast(1),
            pageSize = pageSize.coerceIn(1, 2000)
        )
    }

    private fun JSONObject.optionalStringLocal(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name).trim().takeIf { it.isNotBlank() } else null
    }

    private fun JSONObject.optionalIntLocal(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    private fun JSONObject.optionalLongLocal(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name) else null
    }

    private data class SourceId(
        val source: String,
        val id: String
    )
}
