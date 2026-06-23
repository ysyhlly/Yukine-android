package app.yukine.streaming

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
    private val httpClient: LuoxueHttpClient = DefaultLuoxueHttpClient(),
    private val neteaseClient: LocalNeteaseStreamingClient? = null,
    private val qqMusicClient: LocalQqMusicStreamingClient? = null
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
        val sourceQuery = sourcePrefixFromQuery(normalized.query)
        val searchQuery = sourceQuery?.second ?: normalized.query
        val sources = sourceQuery?.let { listOf(it.first) } ?: listOf("kw", "kg", "wy", "tx")
        val tracks = sources
            .flatMap { source ->
                runCatching {
                    searchBySource(source, normalized.copy(query = searchQuery))
                }.getOrDefault(emptyList())
            }
            .distinctBy { it.providerTrackId }
            .take(normalized.pageSize)
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
        return when (sourceId.source) {
            "kw" -> kuwoPlaylist(normalized, sourceId)
            "kg" -> kugouPlaylist(normalized, sourceId)
            "wy" -> delegateNeteasePlaylist(normalized, sourceId)
            "tx" -> delegateQqPlaylist(normalized, sourceId)
            else -> unsupportedSource(sourceId.source)
        }
    }

    fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        val sourceId = parseLuoxueId(request.providerTrackId)
        return when (sourceId.source) {
            "kw" -> resolveKuwoPlayback(request, sourceId)
            "kg" -> resolveKugouPlayback(request, sourceId)
            "wy" -> delegateNeteasePlayback(request, sourceId)
            "tx" -> delegateQqPlayback(request, sourceId)
            else -> unsupportedSource(sourceId.source)
        }
    }

    private fun searchBySource(source: String, request: StreamingSearchRequest): List<StreamingTrack> {
        return when (source) {
            "kw" -> searchKuwo(request)
            "kg" -> searchKugou(request)
            "wy" -> neteaseClient?.search(request.copy(provider = StreamingProviderName.NETEASE))
                ?.tracks
                ?.map { it.asLuoxueTrack("wy", "网易云") }
                .orEmpty()
            "tx" -> qqMusicClient?.search(request.copy(provider = StreamingProviderName.QQ_MUSIC))
                ?.tracks
                ?.map { it.asLuoxueTrack("tx", "QQ 音乐") }
                .orEmpty()
            else -> emptyList()
        }
    }

    private fun searchKuwo(request: StreamingSearchRequest): List<StreamingTrack> {
        val page = request.page - 1
        val text = httpClient.getText(
            "https://search.kuwo.cn/r.s?all=${encode(request.query)}&ft=music&itemset=web_2013" +
                "&client=kt&pn=$page&rn=${request.pageSize}&rformat=json&encoding=utf8",
            kuwoHeaders()
        )
        val body = parseRelaxedJson(text)
        val songs = body.optJSONArray("abslist") ?: JSONArray()
        return (0 until songs.length())
            .mapNotNull { songs.optJSONObject(it) }
            .map(::kuwoTrack)
    }

    private fun searchKugou(request: StreamingSearchRequest): List<StreamingTrack> {
        val body = httpClient.getText(
            "https://mobiles.kugou.com/api/v3/search/song?format=json&keyword=${encode(request.query)}" +
                "&page=${request.page}&pagesize=${request.pageSize.coerceIn(1, 50)}&showtype=1",
            kugouHeaders()
        )
        return jsonArrayInfo(parseRelaxedJson(body)).map(::kugouTrack)
    }

    private fun kuwoPlaylist(
        normalized: StreamingPlaylistRequest,
        sourceId: SourceId
    ): StreamingPlaylistDetail {
        val body = httpClient.getText(
            "https://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${encode(sourceId.id)}&pn=${normalized.page - 1}" +
                "&rn=${normalized.pageSize.coerceIn(1, 2000)}&encode=utf8&keyset=pl2012&vipver=MUSIC_9.1.1.2_W1",
            kuwoHeaders()
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

    private fun kugouPlaylist(
        normalized: StreamingPlaylistRequest,
        sourceId: SourceId
    ): StreamingPlaylistDetail {
        val body = httpClient.getText(
            "https://mobiles.kugou.com/api/v3/special/song?specialid=${encode(sourceId.id)}" +
                "&page=${normalized.page}&pagesize=${normalized.pageSize.coerceIn(1, 500)}",
            kugouHeaders()
        )
        val json = parseRelaxedJson(body)
        val data = json.optJSONObject("data") ?: json
        val tracks = jsonArrayInfo(json).map(::kugouTrack)
        val total = data.optionalIntLocal("total")
            ?: data.optionalIntLocal("totalnum")
            ?: data.optionalIntLocal("count")
            ?: tracks.size
        val cover = kugouImage(data.optionalStringLocal("imgurl") ?: data.optionalStringLocal("image"))
        return StreamingPlaylistDetail(
            provider = StreamingProviderName.LUOXUE,
            providerPlaylistId = "kg:${sourceId.id}",
            playlist = StreamingPlaylist(
                provider = StreamingProviderName.LUOXUE,
                providerPlaylistId = "kg:${sourceId.id}",
                title = data.optionalStringLocal("specialname")
                    ?: data.optionalStringLocal("name")
                    ?: "LX/酷狗歌单",
                description = data.optionalStringLocal("intro")
                    ?: data.optionalStringLocal("specialdesc")
                    ?: data.optionalStringLocal("description"),
                creator = data.optionalStringLocal("nickname") ?: data.optionalStringLocal("username"),
                coverUrl = cover,
                trackCount = total
            ),
            tracks = tracks,
            total = total,
            page = normalized.page,
            pageSize = normalized.pageSize,
            hasMore = normalized.page * normalized.pageSize < total
        )
    }

    private fun resolveKuwoPlayback(
        request: StreamingPlaybackRequest,
        sourceId: SourceId
    ): StreamingPlaybackSource {
        val body = httpClient.getText(
            "https://antiserver.kuwo.cn/anti.s?type=convert_url3&rid=MUSIC_${encode(sourceId.id)}" +
                "&format=${kuwoFormat(request.quality)}&response=url",
            kuwoHeaders()
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
            headers = kuwoHeaders(),
            supportsRange = true
        )
    }

    private fun resolveKugouPlayback(
        request: StreamingPlaybackRequest,
        sourceId: SourceId
    ): StreamingPlaybackSource {
        val trackKey = parseKugouProviderTrackId(sourceId.id)
        var lastMessage = ""
        for (quality in kugouQualityFallbacks(request.quality)) {
            runCatching {
                return resolveKugouPlaybackWithQuality(sourceId.id, trackKey, quality)
            }.onFailure { error ->
                lastMessage = error.message.orEmpty()
            }
        }
        throw StreamingGatewayException(
            lastMessage.ifBlank { "LX/酷狗未返回可播放链接，可能需要会员或地区不可用" },
            code = StreamingErrorCode.SOURCE_UNAVAILABLE
        )
    }

    private fun resolveKugouPlaybackWithQuality(
        providerTrackId: String,
        trackKey: KugouTrackKey,
        quality: KugouPlaybackQuality
    ): StreamingPlaybackSource {
        val dfid = stableKugouId("dfid:${trackKey.hash}", 24)
        val mid = stableKugouId("mid:${trackKey.hash}", 32)
        val clientTime = System.currentTimeMillis() / 1000L
        val params = linkedMapOf(
            "dfid" to dfid,
            "mid" to mid,
            "uuid" to "-",
            "appid" to KUGOU_APP_ID.toString(),
            "clientver" to KUGOU_CLIENT_VERSION.toString(),
            "clienttime" to clientTime.toString(),
            "album_id" to (trackKey.albumId ?: "0"),
            "area_code" to "1",
            "hash" to trackKey.hash.lowercase(),
            "ssa_flag" to "is_fromtrack",
            "version" to KUGOU_CLIENT_VERSION.toString(),
            "page_id" to "151369488",
            "quality" to quality.wireName,
            "album_audio_id" to (trackKey.albumAudioId ?: "0"),
            "behavior" to "play",
            "pid" to "2",
            "cmd" to "26",
            "pidversion" to "3001",
            "IsFreePart" to "0",
            "ppage_id" to "463467626,350369493,788954147",
            "cdnBackup" to "1",
            "module" to ""
        )
        params["key"] = md5("${params["hash"]}$KUGOU_PLAYBACK_KEY_SECRET$KUGOU_APP_ID$mid${0}")
        params["signature"] = kugouAndroidSignature(params)
        val body = httpClient.getText(
            "https://gateway.kugou.com/v5/url?${params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }}",
            kugouHeaders() + mapOf(
                "User-Agent" to KUGOU_PLAYBACK_USER_AGENT,
                "x-router" to "trackercdn.kugou.com",
                "dfid" to dfid,
                "mid" to mid,
                "clienttime" to clientTime.toString()
            )
        )
        val json = parseRelaxedJson(body)
        val url = collectUrls(json).firstOrNull()
        if (url.isNullOrBlank()) {
            val message = json.optionalStringLocal("error")
                ?: json.optionalStringLocal("msg")
                ?: json.optionalStringLocal("message")
                ?: json.optJSONObject("data")?.optionalStringLocal("message")
                ?: "LX/酷狗未返回可播放链接"
            throw StreamingGatewayException(message, code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        val codec = if (quality == KugouPlaybackQuality.FLAC || url.substringBefore('?').endsWith(".flac", true)) {
            "flac"
        } else {
            "mp3"
        }
        return StreamingPlaybackSource(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = "kg:$providerTrackId",
            url = url,
            expiresAtEpochMs = System.currentTimeMillis() + 4 * 60 * 1000L,
            mimeType = if (codec == "flac") "audio/flac" else "audio/mpeg",
            bitrate = when (quality) {
                KugouPlaybackQuality.FLAC -> 999
                KugouPlaybackQuality.HIGH -> 320
                KugouPlaybackQuality.STANDARD -> 128
            },
            codec = codec,
            headers = kugouHeaders(),
            supportsRange = true
        )
    }

    private fun delegateNeteasePlaylist(
        normalized: StreamingPlaylistRequest,
        sourceId: SourceId
    ): StreamingPlaylistDetail {
        val detail = (neteaseClient ?: throw StreamingGatewayException(
            "LX/网易云子源不可用，请先启用网易云本机音源",
            code = StreamingErrorCode.UNSUPPORTED_OPERATION
        )).playlist(normalized.copy(provider = StreamingProviderName.NETEASE, providerPlaylistId = sourceId.id))
        return detail.copy(
            provider = StreamingProviderName.LUOXUE,
            providerPlaylistId = "wy:${sourceId.id}",
            playlist = detail.playlist?.asLuoxuePlaylist("wy", "网易云"),
            tracks = detail.tracks.map { it.asLuoxueTrack("wy", "网易云") }
        )
    }

    private fun delegateQqPlaylist(
        normalized: StreamingPlaylistRequest,
        sourceId: SourceId
    ): StreamingPlaylistDetail {
        val detail = (qqMusicClient ?: throw StreamingGatewayException(
            "LX/QQ 子源不可用，请先启用 QQ 音乐本机音源",
            code = StreamingErrorCode.UNSUPPORTED_OPERATION
        )).playlist(normalized.copy(provider = StreamingProviderName.QQ_MUSIC, providerPlaylistId = sourceId.id))
        return detail.copy(
            provider = StreamingProviderName.LUOXUE,
            providerPlaylistId = "tx:${sourceId.id}",
            playlist = detail.playlist?.asLuoxuePlaylist("tx", "QQ 音乐"),
            tracks = detail.tracks.map { it.asLuoxueTrack("tx", "QQ 音乐") }
        )
    }

    private fun delegateNeteasePlayback(
        request: StreamingPlaybackRequest,
        sourceId: SourceId
    ): StreamingPlaybackSource {
        val source = (neteaseClient ?: throw StreamingGatewayException(
            "LX/网易云子源不可用，请先启用网易云本机音源",
            code = StreamingErrorCode.UNSUPPORTED_OPERATION
        )).resolvePlayback(request.copy(provider = StreamingProviderName.NETEASE, providerTrackId = sourceId.id))
        return source.copy(provider = StreamingProviderName.LUOXUE, providerTrackId = "wy:${sourceId.id}")
    }

    private fun delegateQqPlayback(
        request: StreamingPlaybackRequest,
        sourceId: SourceId
    ): StreamingPlaybackSource {
        val source = (qqMusicClient ?: throw StreamingGatewayException(
            "LX/QQ 子源不可用，请先启用 QQ 音乐本机音源",
            code = StreamingErrorCode.UNSUPPORTED_OPERATION
        )).resolvePlayback(request.copy(provider = StreamingProviderName.QQ_MUSIC, providerTrackId = sourceId.id))
        return source.copy(provider = StreamingProviderName.LUOXUE, providerTrackId = "tx:${sourceId.id}")
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

    private fun kugouTrack(value: JSONObject): StreamingTrack {
        val fileName = value.optionalStringLocal("FileName") ?: value.optionalStringLocal("filename")
        val parsed = titleAndArtistFromFilename(fileName)
        val hash = value.optionalStringLocal("hash")
            ?: value.optionalStringLocal("Hash")
            ?: value.optionalStringLocal("FileHash")
            ?: ""
        val albumId = value.optionalStringLocal("album_id")
            ?: value.optionalStringLocal("albumid")
            ?: value.optionalStringLocal("albumId")
            ?: "0"
        val albumAudioId = value.optionalStringLocal("album_audio_id")
            ?: value.optionalStringLocal("albumAudioId")
            ?: value.optionalStringLocal("audio_id")
            ?: value.optionalStringLocal("mixsongid")
            ?: "0"
        val providerTrackId = "kg:${kugouProviderTrackId(hash, albumId, albumAudioId)}"
        val title = htmlDecode(
            value.optionalStringLocal("songname")
                ?: value.optionalStringLocal("SongName")
                ?: value.optionalStringLocal("name")
                ?: parsed.first
                ?: ""
        )
        val artist = htmlDecode(
            value.optionalStringLocal("singername")
                ?: value.optionalStringLocal("SingerName")
                ?: value.optionalStringLocal("artist")
                ?: parsed.second
                ?: ""
        )
        val album = htmlDecode(
            value.optionalStringLocal("AlbumName")
                ?: value.optionalStringLocal("album_name")
                ?: value.optionalStringLocal("albumname")
                ?: ""
        )
        val cover = kugouImage(value.optionalStringLocal("imgurl") ?: value.optionalStringLocal("image"))
        val playable = hash.isNotBlank()
        val qualities = kugouQualities(value)
        return StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = providerTrackId,
            title = title,
            artist = artist,
            artists = artist.takeIf { it.isNotBlank() }?.let {
                listOf(StreamingArtistRef(StreamingProviderName.LUOXUE, "kg:$it", it))
            }.orEmpty(),
            album = album.takeIf { it.isNotBlank() },
            albumId = albumId.takeIf { it != "0" },
            durationMs = value.optionalLongLocal("duration")?.let { if (it > 1000) it else it * 1000L }
                ?: value.optionalLongLocal("Duration")?.let { if (it > 1000) it else it * 1000L },
            coverUrl = cover,
            coverThumbUrl = cover,
            qualities = qualities,
            playable = playable,
            unavailableReason = if (playable) null else "LX/酷狗歌曲 hash 为空",
            description = listOfNotNull(
                "LX/洛雪 · 酷狗子源",
                album.takeIf { it.isNotBlank() }?.let { "专辑：$it" }
            ).joinToString("\n"),
            lyricSources = listOf(
                StreamingLyricSource(
                    provider = StreamingProviderName.LUOXUE,
                    name = "LX/酷狗歌词",
                    providerTrackId = providerTrackId,
                    priority = 18
                )
            ),
            playbackCandidates = qualities.ifEmpty { setOf(StreamingAudioQuality.STANDARD) }
                .sortedBy { it.ordinal }
                .map { quality ->
                    StreamingPlaybackCandidate(
                        provider = StreamingProviderName.LUOXUE,
                        quality = quality,
                        label = "LX/酷狗",
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
            "kugou" -> "kg"
            "qq", "tencent", "tencentmusic" -> "tx"
            "netease", "neteasecloud", "163", "wy163" -> "wy"
            "migu" -> "mg"
            "lx", "lxmusic", "luoxue" -> "kw"
            else -> source
        }
        return SourceId(normalizedSource, id.trim())
    }

    private fun sourcePrefixFromQuery(value: String): Pair<String, String>? {
        val trimmed = value.trim()
        if (!trimmed.contains(':')) return null
        val prefix = trimmed.substringBefore(':')
        val source = parseLuoxueId("$prefix:placeholder").source
        if (source !in setOf("kw", "kg", "wy", "tx", "mg")) return null
        val query = trimmed.substringAfter(':').trim()
        return if (query.isBlank()) null else source to query
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

    private fun kugouQualities(value: JSONObject): Set<StreamingAudioQuality> {
        val qualities = linkedSetOf<StreamingAudioQuality>()
        if (!value.optionalStringLocal("SQFileHash").isNullOrBlank() ||
            !value.optionalStringLocal("sqhash").isNullOrBlank() ||
            value.optionalLongLocal("SQFileSize")?.let { it > 0 } == true
        ) {
            qualities += StreamingAudioQuality.LOSSLESS
        }
        if (!value.optionalStringLocal("HQFileHash").isNullOrBlank() ||
            !value.optionalStringLocal("hqhash").isNullOrBlank() ||
            value.optionalLongLocal("HQFileSize")?.let { it > 0 } == true
        ) {
            qualities += StreamingAudioQuality.HIGH
        }
        qualities += StreamingAudioQuality.STANDARD
        return qualities
    }

    private fun kuwoFormat(quality: StreamingAudioQuality): String = when (quality) {
        StreamingAudioQuality.HIRES,
        StreamingAudioQuality.LOSSLESS -> "flac|mp3|aac"
        StreamingAudioQuality.HIGH -> "mp3|aac"
        StreamingAudioQuality.STANDARD -> "aac|mp3"
    }

    private fun kuwoHeaders(): Map<String, String> = mapOf(
        "Referer" to "https://www.kuwo.cn/",
        "User-Agent" to "Mozilla/5.0 Yukine-Android"
    )

    private fun kugouHeaders(): Map<String, String> = mapOf(
        "Referer" to "https://www.kugou.com/",
        "Origin" to "https://www.kugou.com",
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

    private fun jsonArrayInfo(json: JSONObject): List<JSONObject> {
        val data = json.optJSONObject("data")
        val array = data?.optJSONArray("info")
            ?: data?.optJSONArray("list")
            ?: json.optJSONArray("info")
            ?: json.optJSONArray("list")
            ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun kugouImage(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalized = raw.replace("{size}", "400")
        return when {
            normalized.startsWith("https://") -> normalized
            normalized.startsWith("http://") -> "https://${normalized.removePrefix("http://")}"
            else -> null
        }
    }

    private fun titleAndArtistFromFilename(value: String?): Pair<String?, String?> {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null to null
        val parts = raw.split(Regex("\\s+-\\s+"), limit = 2)
        return if (parts.size < 2) {
            raw to null
        } else {
            parts[1].trim().takeIf { it.isNotBlank() } to parts[0].trim().takeIf { it.isNotBlank() }
        }
    }

    private fun kugouProviderTrackId(hash: String, albumId: String?, albumAudioId: String?): String {
        return listOf(
            hash.lowercase(),
            albumId?.takeIf { it.isNotBlank() } ?: "0",
            albumAudioId?.takeIf { it.isNotBlank() } ?: "0"
        ).joinToString(".")
    }

    private fun parseKugouProviderTrackId(value: String): KugouTrackKey {
        val parts = value.split('.')
        val hash = parts.firstOrNull()?.trim()?.lowercase().orEmpty()
        if (!Regex("^[a-f0-9]{16,64}$", RegexOption.IGNORE_CASE).matches(hash)) {
            throw StreamingGatewayException("LX/酷狗歌曲 ID 无效", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
        }
        return KugouTrackKey(
            hash = hash,
            albumId = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it != "0" },
            albumAudioId = parts.getOrNull(2)?.takeIf { it.isNotBlank() && it != "0" }
        )
    }

    private fun kugouQualityFallbacks(quality: StreamingAudioQuality): List<KugouPlaybackQuality> {
        return when (quality) {
            StreamingAudioQuality.HIRES,
            StreamingAudioQuality.LOSSLESS -> listOf(
                KugouPlaybackQuality.FLAC,
                KugouPlaybackQuality.HIGH,
                KugouPlaybackQuality.STANDARD
            )
            StreamingAudioQuality.HIGH -> listOf(KugouPlaybackQuality.HIGH, KugouPlaybackQuality.STANDARD)
            StreamingAudioQuality.STANDARD -> listOf(KugouPlaybackQuality.STANDARD)
        }
    }

    private fun kugouAndroidSignature(params: Map<String, String>): String {
        val body = params.entries.sortedBy { it.key }.joinToString("") { "${it.key}=${it.value}" }
        return md5("$KUGOU_ANDROID_SECRET$body$KUGOU_ANDROID_SECRET")
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun stableKugouId(value: String, length: Int): String {
        val digits = md5(value).filter(Char::isDigit).padEnd(length, '0')
        return digits.take(length)
    }

    private fun collectUrls(value: Any?, depth: Int = 0): List<String> {
        if (depth > 6 || value == null) return emptyList()
        return when (value) {
            is String -> value.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let(::listOf).orEmpty()
            is JSONArray -> (0 until value.length()).flatMap { collectUrls(value.opt(it), depth + 1) }
            is JSONObject -> {
                val keys = listOf("url", "play_url", "playUrl", "backup_url", "backupUrl", "urls", "data", "info")
                keys.flatMap { collectUrls(value.opt(it), depth + 1) }
            }
            else -> emptyList()
        }
    }

    private fun StreamingTrack.asLuoxueTrack(source: String, label: String): StreamingTrack {
        val luoxueId = "$source:$providerTrackId"
        return copy(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = luoxueId,
            artists = artists.map {
                StreamingArtistRef(StreamingProviderName.LUOXUE, "$source:${it.providerArtistId}", it.name)
            },
            description = listOfNotNull("LX/洛雪 · $label 子源", description).joinToString("\n"),
            lyricSources = lyricSources.map {
                it.copy(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = it.providerTrackId?.let { id -> "$source:$id" } ?: luoxueId
                )
            }.ifEmpty {
                listOf(StreamingLyricSource(StreamingProviderName.LUOXUE, "LX/$label 歌词", luoxueId, 15))
            },
            playbackCandidates = playbackCandidates.map {
                it.copy(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = it.providerTrackId?.let { id -> "$source:$id" } ?: luoxueId
                )
            }.ifEmpty {
                qualities.ifEmpty { setOf(StreamingAudioQuality.STANDARD) }.map { quality ->
                    StreamingPlaybackCandidate(StreamingProviderName.LUOXUE, quality, "LX/$label", luoxueId, playable)
                }
            }
        )
    }

    private fun StreamingPlaylist.asLuoxuePlaylist(source: String, label: String): StreamingPlaylist {
        return copy(
            provider = StreamingProviderName.LUOXUE,
            providerPlaylistId = "$source:$providerPlaylistId",
            description = listOfNotNull("LX/洛雪 · $label 子源", description).joinToString("\n")
        )
    }

    private fun <T> unsupportedSource(source: String): T {
        val message = when (source) {
            "mg" -> "LX/咪咕子源本机解析还未接入，请先用 kw/kg/wy/tx 或导入其它子源歌单"
            else -> "暂不支持 LX 子源：$source"
        }
        throw StreamingGatewayException(message, code = StreamingErrorCode.UNSUPPORTED_OPERATION)
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

    private data class KugouTrackKey(
        val hash: String,
        val albumId: String?,
        val albumAudioId: String?
    )

    private enum class KugouPlaybackQuality(val wireName: String) {
        FLAC("flac"),
        HIGH("320"),
        STANDARD("128")
    }

    private companion object {
        private const val KUGOU_APP_ID = 1005
        private const val KUGOU_CLIENT_VERSION = 11430
        private const val KUGOU_ANDROID_SECRET = "OIlwieks28dk2k092lksi2UIkp"
        private const val KUGOU_PLAYBACK_KEY_SECRET = "57ae12eb6890223e355ccfcb74edf70d"
        private const val KUGOU_PLAYBACK_USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
    }
}
