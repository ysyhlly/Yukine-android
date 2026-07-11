package app.yukine.streaming

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
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
    private val qqMusicClient: LocalQqMusicStreamingClient? = null,
    private val scriptRuntime: LuoxueScriptRuntime = QuickJsLuoxueScriptRuntime()
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
        val sources = sourceQuery?.let { listOf(it.first) } ?: BUILT_IN_SEARCH_SOURCE_KEYS
        val sourceTracks = sources.map { source ->
            runCatching {
                searchBySource(source, normalized.copy(query = searchQuery))
            }.getOrDefault(emptyList())
        }
        val tracks = interleaveTrackGroups(sourceTracks, normalized.pageSize)
        val availableCount = sourceTracks.sumOf(List<StreamingTrack>::size)
        return StreamingSearchResult(
            provider = StreamingProviderName.LUOXUE,
            query = normalized.query,
            page = normalized.page,
            pageSize = normalized.pageSize,
            total = availableCount,
            hasMore = availableCount > tracks.size || sourceTracks.any { it.size >= normalized.pageSize },
            tracks = tracks
        )
    }

    /**
     * Lets search-capable imported scripts contribute results before the built-in LX providers.
     * Official playback-only scripts are skipped cheaply and retain the normal built-in behavior.
     */
    suspend fun search(
        request: StreamingSearchRequest,
        importedSources: List<LuoxueImportedSource>
    ): StreamingSearchResult {
        val normalized = request.normalizedLocal()
        if (normalized.query.isBlank() || !normalized.mediaTypes.contains(StreamingMediaType.TRACK)) {
            return search(normalized)
        }
        val sourceQuery = sourcePrefixFromQuery(normalized.query)
        val searchQuery = sourceQuery?.second ?: normalized.query
        val scriptTracks = mutableListOf<StreamingTrack>()
        var scriptTotal: Int? = null
        var scriptHasMore = false
        for (imported in activeImportedSources(importedSources)) {
            if (!imported.script.contains("search", ignoreCase = true)) {
                continue
            }
            for (sourceKey in scriptSearchSourceKeys(imported, sourceQuery?.first)) {
                val page = try {
                    scriptRuntime.search(
                        source = imported,
                        sourceKey = sourceKey,
                        query = searchQuery,
                        page = normalized.page,
                        pageSize = normalized.pageSize
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                } ?: continue
                page.items.mapNotNullTo(scriptTracks) { musicInfo ->
                    scriptSearchTrack(imported, sourceKey, musicInfo)
                }
                page.total?.let { total -> scriptTotal = maxOf(scriptTotal ?: 0, total) }
                scriptHasMore = scriptHasMore || page.hasMore == true
                if (sourceQuery != null && page.items.isNotEmpty()) {
                    break
                }
            }
        }
        val builtIn = search(normalized)
        val combined = (scriptTracks + builtIn.tracks)
            .distinctBy { it.providerTrackId }
        val tracks = balanceTracksBySource(combined, normalized.pageSize)
        return builtIn.copy(
            total = maxOf(
                tracks.size,
                scriptTotal ?: 0,
                builtIn.total ?: builtIn.tracks.size
            ),
            hasMore = scriptHasMore || builtIn.hasMore || combined.size > tracks.size,
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

    /**
     * Lets imported LX scripts resolve a URL before the built-in resolver is tried. The queue,
     * cache, and playback-service paths remain unchanged.
     */
    suspend fun resolvePlayback(
        request: StreamingPlaybackRequest,
        importedSources: List<LuoxueImportedSource>
    ): StreamingPlaybackSource {
        val sourceId = parseLuoxueId(request.providerTrackId)
        val scriptFailures = mutableListOf<String>()
        for (imported in importedSources) {
            if (!imported.enabled) {
                continue
            }
            for (quality in request.quality.toLuoxueScriptQualityCandidates()) {
                for (sourceKey in scriptSourceKeys(imported, sourceId.source)) {
                    val url = try {
                        scriptRuntime.resolveMusicUrl(
                            source = imported,
                            sourceKey = sourceKey,
                            musicInfo = scriptMusicInfo(sourceId, request.luoxueMusicInfoJson),
                            quality = quality
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        scriptFailures += "${imported.name}/$sourceKey：${safeScriptFailureMessage(error)}"
                        null
                    }?.trim()
                    if (url.isNullOrBlank()) continue
                    if (!url.isHttpUrl()) {
                        scriptFailures += "${imported.name}/$sourceKey/$quality：返回的不是 HTTP 播放地址"
                        continue
                    }
                    return StreamingPlaybackSource(
                        provider = StreamingProviderName.LUOXUE,
                        providerTrackId = request.providerTrackId,
                        url = url,
                        mimeType = mimeType(url),
                        bitrate = bitrate(quality.toStreamingAudioQuality()),
                        codec = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.isNotBlank() },
                        supportsRange = true
                    )
                }
            }
        }
        resolveShiqianjiangKugouCompatibility(request, sourceId, importedSources)?.let { return it }
        return try {
            resolvePlayback(request)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (scriptFailures.isEmpty()) throw error
            val scriptMessage = scriptFailures.distinct().take(3).joinToString("；")
            throw StreamingGatewayException(
                "LX 脚本解析失败（$scriptMessage）；本机兜底也不可用：${error.message ?: "未知错误"}",
                cause = error,
                code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                retryable = true
            )
        }
    }

    /**
     * Compatibility path for the sponsored source supplied by the user. Its generated script uses
     * the same public endpoint and key, but some Android QuickJS/cellular requests return a false
     * digital-album failure. Keep this strictly scoped to the script's own HTTPS host and key.
     */
    private fun resolveShiqianjiangKugouCompatibility(
        request: StreamingPlaybackRequest,
        sourceId: SourceId,
        importedSources: List<LuoxueImportedSource>
    ): StreamingPlaybackSource? {
        if (sourceId.source != "kg") return null
        val trackKey = runCatching { parseKugouProviderTrackId(sourceId.id) }.getOrNull() ?: return null
        val imported = importedSources.firstNotNullOfOrNull { source ->
            if (!source.enabled) return@firstNotNullOfOrNull null
            val origin = runCatching { URL(source.origin) }.getOrNull() ?: return@firstNotNullOfOrNull null
            if (origin.protocol != "https" || origin.host != SHIQIANJIANG_SOURCE_HOST) {
                return@firstNotNullOfOrNull null
            }
            val key = origin.queryValue("key")?.takeIf { it.isNotBlank() }
                ?: return@firstNotNullOfOrNull null
            source to key
        } ?: return null
        val key = imported.second
        for (quality in request.quality.toLuoxueScriptQualityCandidates()) {
            val requestUrl = "https://$SHIQIANJIANG_SOURCE_HOST/api/music/url" +
                "?source=kg&songId=${encode(trackKey.hash)}&quality=${encode(quality)}&key=${encode(key)}"
            val body = runCatching {
                httpClient.getText(
                    requestUrl,
                    mapOf(
                        "X-API-Key" to key,
                        "User-Agent" to "lx-music-mobile/1.0.0",
                        "Content-Type" to "application/json"
                    )
                )
            }.getOrNull() ?: continue
            val value = runCatching { parseRelaxedJson(body) }.getOrNull() ?: continue
            if (value.optInt("code", 0) != 200) continue
            val url = value.optionalStringLocal("url")?.takeIf { it.isHttpUrl() } ?: continue
            return StreamingPlaybackSource(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = request.providerTrackId,
                url = url,
                mimeType = mimeType(url),
                bitrate = bitrate(quality.toStreamingAudioQuality()),
                codec = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.isNotBlank() },
                supportsRange = true
            )
        }
        return null
    }

    private fun URL.queryValue(name: String): String? {
        return query.orEmpty().split('&').firstNotNullOfOrNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) return@firstNotNullOfOrNull null
            val key = URLDecoder.decode(entry.substring(0, separator), StandardCharsets.UTF_8.name())
            if (key != name) null else URLDecoder.decode(
                entry.substring(separator + 1),
                StandardCharsets.UTF_8.name()
            )
        }
    }

    private fun safeScriptFailureMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        if (raw.contains("数字专辑")) {
            return "该数字专辑暂不支持此音源"
        }
        val firstLine = raw
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .replace(Regex("https?://\\S+"), "远端脚本")
            .replace(Regex("(?i)(key|token|auth)=[^&\\s)]+"), "$1=<已隐藏>")
            .trim()
        val detail = when {
            "musicUrl 解析失败：" in firstLine -> firstLine.substringAfter("musicUrl 解析失败：")
            "musicUrl解析失败：" in firstLine -> firstLine.substringAfter("musicUrl解析失败：")
            else -> firstLine
        }
        return detail.take(160).ifBlank { "未知错误" }
    }

    /**
     * Resolves LX lyrics through the imported script protocol, independently from playback URL
     * resolution. Shared scripts are tried against their native sub-source first, then the LX
     * `local` sub-source convention used by lyric-capable scripts.
     */
    suspend fun resolveLyrics(
        providerTrackId: String,
        luoxueMusicInfoJson: String?,
        importedSources: List<LuoxueImportedSource>
    ): LuoxueScriptLyrics? {
        val sourceId = parseLuoxueId(providerTrackId)
        val musicInfo = scriptMusicInfo(sourceId, luoxueMusicInfoJson)
        for (imported in activeImportedSources(importedSources)) {
            for (sourceKey in scriptSourceKeys(imported, sourceId.source)) {
                val lyrics = try {
                    scriptRuntime.resolveLyrics(imported, sourceKey, musicInfo)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                }
                if (lyrics?.lyric?.isNotBlank() == true) {
                    return lyrics
                }
            }
        }
        return null
    }

    /** Resolves LX artwork through the imported script protocol without delaying playback. */
    suspend fun resolveCoverUrl(
        providerTrackId: String,
        luoxueMusicInfoJson: String?,
        importedSources: List<LuoxueImportedSource>
    ): String? {
        val sourceId = parseLuoxueId(providerTrackId)
        val musicInfo = scriptMusicInfo(sourceId, luoxueMusicInfoJson)
        for (imported in activeImportedSources(importedSources)) {
            for (sourceKey in scriptSourceKeys(imported, sourceId.source)) {
                val url = try {
                    scriptRuntime.resolveCoverUrl(imported, sourceKey, musicInfo)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                }?.trim()
                if (!url.isNullOrBlank() && url.isHttpUrl()) {
                    return url
                }
            }
        }
        return null
    }

    private fun activeImportedSources(sources: List<LuoxueImportedSource>): List<LuoxueImportedSource> {
        return sources.filter { source -> source.enabled && source.script.isNotBlank() }
    }

    private fun balanceTracksBySource(tracks: List<StreamingTrack>, limit: Int): List<StreamingTrack> {
        val groups = tracks
            .groupBy(::trackSourceKey)
            .values
            .map { it.distinctBy(StreamingTrack::providerTrackId) }
        return interleaveTrackGroups(groups, limit)
    }

    private fun interleaveTrackGroups(
        groups: List<List<StreamingTrack>>,
        limit: Int
    ): List<StreamingTrack> {
        if (limit <= 0 || groups.isEmpty()) return emptyList()
        val result = ArrayList<StreamingTrack>(limit)
        val seen = hashSetOf<String>()
        val maxSize = groups.maxOfOrNull(List<StreamingTrack>::size) ?: 0
        for (index in 0 until maxSize) {
            for (group in groups) {
                val track = group.getOrNull(index) ?: continue
                if (seen.add(track.providerTrackId)) result += track
                if (result.size >= limit) return result
            }
        }
        return result
    }

    private fun trackSourceKey(track: StreamingTrack): String {
        return track.providerTrackId.substringBefore(':', track.provider.wireName).trim().lowercase()
    }

    private fun scriptSourceKeys(source: LuoxueImportedSource, primarySource: String): List<String> {
        val supported = scriptSourceKinds(source).toSet()
        return linkedSetOf(primarySource, "local")
            .filter { candidate -> supported.isEmpty() || candidate in supported }
    }

    private fun scriptSearchSourceKeys(
        source: LuoxueImportedSource,
        requestedSource: String?
    ): List<String> {
        val supported = scriptSourceKinds(source)
        if (!requestedSource.isNullOrBlank()) {
            return linkedSetOf(normalizeScriptSourceKey(requestedSource), "local")
                .filter { candidate -> supported.isEmpty() || candidate in supported }
        }
        return supported.ifEmpty { listOf("tx", "kw", "kg", "wy", "mg", "local") }
    }

    private fun scriptSourceKinds(source: LuoxueImportedSource): List<String> {
        val persisted = source.sourceKinds.map(::normalizeScriptSourceKey)
        val currentScript = LuoxueSourceImporter.parseMany(source.script, source.origin)
            .firstOrNull()
            ?.sourceKinds
            .orEmpty()
            .map(::normalizeScriptSourceKey)
        return (persisted + currentScript).filter(String::isNotBlank).distinct()
    }

    private fun scriptSearchTrack(
        imported: LuoxueImportedSource,
        requestedSource: String,
        musicInfo: Map<String, Any?>
    ): StreamingTrack? {
        val value = runCatching { JSONObject(musicInfo) }.getOrNull() ?: return null
        val declaredSource = value.firstTextValue("source", "platform", "provider")
            ?.let(::normalizeScriptSourceKey)
            ?.takeIf { it in LUOXUE_SCRIPT_SOURCE_KEYS }
        val sourceKey = declaredSource ?: normalizeScriptSourceKey(requestedSource)
        val mapped = when (sourceKey) {
            "kw" -> kuwoTrack(value)
            "kg" -> kugouTrack(value)
            "tx" -> scriptQqTrack(value)
            else -> genericScriptTrack(value, sourceKey)
        }
        return mapped?.copy(
            description = listOfNotNull(
                "LX JS · ${imported.name}",
                mapped.description?.takeIf { it.isNotBlank() }
            ).joinToString("\n")
        )
    }

    private fun scriptQqTrack(value: JSONObject): StreamingTrack? {
        val file = value.optJSONObject("file") ?: JSONObject()
        val songMid = value.firstTextValue("songmid", "songMid", "mid", "id", "providerTrackId")
            ?.removePrefix("tx:")
            ?.substringBefore('|')
            .orEmpty()
        if (songMid.isBlank()) return null
        val mediaMid = file.firstTextValue("media_mid", "mediaMid")
            ?: value.firstTextValue("media_mid", "mediaMid", "strMediaMid", "str_media_mid")
        val resolvedId = if (!mediaMid.isNullOrBlank() && mediaMid != songMid) {
            "$songMid|$mediaMid"
        } else {
            songMid
        }
        val albumObject = value.optJSONObject("album")
        val albumMid = albumObject?.firstTextValue("mid", "albumMid", "albummid")
            ?: value.firstTextValue("albummid", "albumMid", "album_mid")
        val title = cleanDisplayText(value.firstTextValue("name", "title", "songname", "songName", "songorig"))
            .orEmpty()
        val artist = cleanDisplayText(scriptArtist(value)).orEmpty()
        val album = cleanDisplayText(
            albumObject?.firstTextValue("name", "title")
                ?: value.textValue("album")
                ?: value.firstTextValue("albumName", "albumname", "album_name")
        )
        val cover = scriptCover(value)
            ?: albumMid?.takeIf { it.isNotBlank() }?.let(::qqAlbumCover)
        val qualities = scriptQualities(value)
        val providerTrackId = "tx:$resolvedId"
        return StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = providerTrackId,
            title = title,
            artist = artist,
            artists = scriptArtists(value, "tx"),
            album = album,
            albumId = albumMid,
            durationMs = scriptDurationMs(value),
            coverUrl = cover,
            coverThumbUrl = cover,
            qualities = qualities,
            playable = true,
            description = listOfNotNull(
                "LX/洛雪 · QQ 音乐子源",
                album?.let { "专辑：$it" }
            ).joinToString("\n"),
            lyricSources = listOf(
                StreamingLyricSource(StreamingProviderName.LUOXUE, "LX/QQ 歌词", providerTrackId, 20)
            ),
            luoxueMusicInfoJson = sourceMusicInfo(value, "tx", songMid),
            playbackCandidates = qualities.map { quality ->
                StreamingPlaybackCandidate(
                    StreamingProviderName.LUOXUE,
                    quality,
                    "LX/QQ 音乐",
                    providerTrackId,
                    true
                )
            }
        )
    }

    private fun genericScriptTrack(value: JSONObject, sourceKey: String): StreamingTrack? {
        val rawId = when (sourceKey) {
            "wy" -> value.firstTextValue("id", "songId", "songid", "mid")
            "mg" -> value.firstTextValue("copyrightId", "copyright_id", "id", "mid")
            "local" -> value.firstTextValue("id", "songId", "songmid", "mid", "hash", "url")
            else -> value.firstTextValue("id", "songId", "songmid", "mid", "rid", "hash")
        }?.removePrefix("$sourceKey:").orEmpty()
        if (rawId.isBlank()) return null
        val title = cleanDisplayText(value.firstTextValue("name", "title", "songname", "songName"))
            .orEmpty()
        val artist = cleanDisplayText(scriptArtist(value)).orEmpty()
        val albumObject = value.optJSONObject("album")
        val album = cleanDisplayText(
            albumObject?.firstTextValue("name", "title")
                ?: value.textValue("album")
                ?: value.firstTextValue("albumName", "albumname", "album_name")
        )
        val albumId = albumObject?.firstTextValue("id", "mid")
            ?: value.firstTextValue("albumId", "album_id", "albumid", "albumMid", "albummid")
        val cover = scriptCover(value)
        val qualities = scriptQualities(value)
        val providerTrackId = "$sourceKey:$rawId"
        val label = scriptSourceLabel(sourceKey)
        return StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = providerTrackId,
            title = title,
            artist = artist,
            artists = scriptArtists(value, sourceKey),
            album = album,
            albumId = albumId,
            durationMs = scriptDurationMs(value),
            coverUrl = cover,
            coverThumbUrl = cover,
            qualities = qualities,
            playable = true,
            description = listOfNotNull(
                "LX/洛雪 · $label 子源",
                album?.let { "专辑：$it" }
            ).joinToString("\n"),
            lyricSources = listOf(
                StreamingLyricSource(StreamingProviderName.LUOXUE, "LX/$label 歌词", providerTrackId, 18)
            ),
            luoxueMusicInfoJson = sourceMusicInfo(value, sourceKey, rawId),
            playbackCandidates = qualities.map { quality ->
                StreamingPlaybackCandidate(
                    StreamingProviderName.LUOXUE,
                    quality,
                    "LX/$label",
                    providerTrackId,
                    true
                )
            }
        )
    }

    private fun scriptArtists(value: JSONObject, sourceKey: String): List<StreamingArtistRef> {
        val arrays = listOf("singer", "singers", "artist", "artists")
            .mapNotNull(value::optJSONArray)
        val names = arrays.firstOrNull { it.length() > 0 }
            ?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    when (val item = array.opt(index)) {
                        is JSONObject -> item.firstTextValue("name", "title")
                        is String -> item.trim().takeIf(String::isNotBlank)
                        else -> null
                    }
                }
            }
            .orEmpty()
            .ifEmpty { scriptArtist(value)?.let(::listOf).orEmpty() }
        return names.distinct().map { name ->
            StreamingArtistRef(StreamingProviderName.LUOXUE, "$sourceKey:$name", name)
        }
    }

    private fun scriptArtist(value: JSONObject): String? {
        value.firstTextValue("singerName", "singername", "artistName", "artist_name", "author")
            ?.let { return it }
        listOf("singer", "artist").forEach { name ->
            value.textValue(name)?.let { return it }
        }
        return listOf("singer", "singers", "artist", "artists")
            .mapNotNull(value::optJSONArray)
            .firstOrNull { it.length() > 0 }
            ?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    when (val item = array.opt(index)) {
                        is JSONObject -> item.firstTextValue("name", "title")
                        is String -> item.trim().takeIf(String::isNotBlank)
                        else -> null
                    }
                }.joinToString(" / ").takeIf(String::isNotBlank)
            }
    }

    private fun scriptCover(value: JSONObject): String? {
        return value.firstTextValue(
            "coverUrl",
            "cover",
            "picUrl",
            "picurl",
            "pic",
            "img",
            "image",
            "albumPic"
        ) ?: value.optJSONObject("album")?.firstTextValue("cover", "picUrl", "pic", "image")
    }

    private fun scriptDurationMs(value: JSONObject): Long? {
        value.firstLongValue("durationMs", "duration_ms", "dt")?.let { return it.coerceAtLeast(0L) }
        value.firstLongValue("interval")?.let { return it.coerceAtLeast(0L) * 1000L }
        val duration = value.firstLongValue("duration", "time")
        if (duration != null) {
            return if (duration > 10_000L) duration else duration.coerceAtLeast(0L) * 1000L
        }
        val clock = value.firstTextValue("playTime", "durationText") ?: return null
        val parts = clock.split(':').mapNotNull(String::toLongOrNull)
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> null
        }
    }

    private fun scriptQualities(value: JSONObject): Set<StreamingAudioQuality> {
        val result = linkedSetOf<StreamingAudioQuality>()
        listOf("qualitys", "qualities", "types")
            .mapNotNull(value::optJSONArray)
            .forEach { array ->
                (0 until array.length()).forEach { index ->
                    when (array.optString(index).lowercase()) {
                        "128k", "standard", "low" -> result += StreamingAudioQuality.STANDARD
                        "320k", "high", "hq" -> result += StreamingAudioQuality.HIGH
                        "flac", "lossless", "sq" -> result += StreamingAudioQuality.LOSSLESS
                        "flac24bit", "hires", "hi-res" -> result += StreamingAudioQuality.HIRES
                    }
                }
            }
        if (value.firstLongValue("size128", "size_128", "filesize")?.let { it > 0L } == true) {
            result += StreamingAudioQuality.STANDARD
        }
        if (value.firstLongValue("size320", "size_320")?.let { it > 0L } == true) {
            result += StreamingAudioQuality.HIGH
        }
        if (value.firstLongValue("sizeflac", "size_flac", "sizeape")?.let { it > 0L } == true) {
            result += StreamingAudioQuality.LOSSLESS
        }
        if (value.firstLongValue("size_hires", "sizeHiRes")?.let { it > 0L } == true) {
            result += StreamingAudioQuality.HIRES
        }
        if (result.isEmpty()) result += StreamingAudioQuality.STANDARD
        return result
    }

    private fun normalizeScriptSourceKey(value: String): String = when (value.trim().lowercase()) {
        "qq", "tencent", "tencentmusic" -> "tx"
        "kuwo" -> "kw"
        "kugou" -> "kg"
        "netease", "neteasecloud", "163" -> "wy"
        "migu" -> "mg"
        else -> value.trim().lowercase()
    }

    private fun scriptSourceLabel(sourceKey: String): String = when (sourceKey) {
        "tx" -> "QQ 音乐"
        "kw" -> "酷我"
        "kg" -> "酷狗"
        "wy" -> "网易云"
        "mg" -> "咪咕"
        "git" -> "Git"
        "local" -> "脚本本地"
        else -> sourceKey
    }

    private fun qqAlbumCover(albumMid: String): String {
        return "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumMid}.jpg?max_age=2592000"
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
            "mg" -> searchMigu(request)
            else -> emptyList()
        }
    }

    private fun searchMigu(request: StreamingSearchRequest): List<StreamingTrack> {
        val searchSwitch = encode(
            """{"song":1,"album":0,"singer":0,"tagSong":1,"mvSong":0,"songlist":0,"bestShow":1,"lyricSong":0}"""
        )
        val body = httpClient.getText(
            "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do" +
                "?isCopyright=1&isCorrect=1&pageNo=${request.page}&pageSize=${request.pageSize.coerceIn(1, 50)}" +
                "&sort=0&text=${encode(request.query)}&searchSwitch=$searchSwitch",
            mapOf(
                "Accept" to "*/*",
                "platform" to "Android",
                "User-Agent" to "MGMobileMusic/7.0.0 Yukine-Android",
                "Referer" to "https://music.migu.cn/"
            )
        )
        val root = JSONObject(body)
        if (root.optString("code") !in setOf("", "000000")) return emptyList()
        val songs = root.optJSONObject("songResultData")?.optJSONArray("resultList") ?: JSONArray()
        return (0 until songs.length())
            .mapNotNull(songs::optJSONObject)
            .mapNotNull(::miguTrack)
    }

    private fun miguTrack(value: JSONObject): StreamingTrack? {
        val copyrightId = value.firstTextValue("copyrightId", "copyright_id", "contentId", "id")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val title = cleanDisplayText(value.firstTextValue("name", "songName", "title")).orEmpty()
        val singers = value.optJSONArray("singers") ?: JSONArray()
        val artistNames = (0 until singers.length()).mapNotNull { index ->
            singers.optJSONObject(index)?.firstTextValue("name")
        }.filter(String::isNotBlank)
        val artist = artistNames.joinToString(" / ")
        val albums = value.optJSONArray("albums") ?: JSONArray()
        val albumObject = albums.optJSONObject(0)
        val album = cleanDisplayText(albumObject?.firstTextValue("name"))
        val albumId = albumObject?.firstTextValue("id")
        val images = value.optJSONArray("imgItems") ?: JSONArray()
        val cover = (0 until images.length()).mapNotNull { index ->
            images.optJSONObject(index)?.firstTextValue("img", "url")
        }.firstOrNull(String::isNotBlank)
        val qualities = linkedSetOf<StreamingAudioQuality>()
        val formats = value.optJSONArray("newRateFormats") ?: value.optJSONArray("rateFormats") ?: JSONArray()
        (0 until formats.length()).forEach { index ->
            when (formats.optJSONObject(index)?.optString("formatType")?.uppercase()) {
                "PQ", "LQ" -> qualities += StreamingAudioQuality.STANDARD
                "HQ" -> qualities += StreamingAudioQuality.HIGH
                "SQ" -> qualities += StreamingAudioQuality.LOSSLESS
                "ZQ", "Z3D", "Z3D24" -> qualities += StreamingAudioQuality.HIRES
            }
        }
        if (qualities.isEmpty()) qualities += StreamingAudioQuality.STANDARD
        val providerTrackId = "mg:$copyrightId"
        return StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = providerTrackId,
            title = title,
            artist = artist,
            artists = artistNames.mapIndexed { index, name ->
                val artistId = singers.optJSONObject(index)?.firstTextValue("id").orEmpty().ifBlank { name }
                StreamingArtistRef(StreamingProviderName.LUOXUE, "mg:$artistId", name)
            },
            album = album,
            albumId = albumId,
            coverUrl = cover,
            coverThumbUrl = cover,
            qualities = qualities,
            playable = true,
            description = listOfNotNull("LX/洛雪 · 咪咕子源", album?.let { "专辑：$it" }).joinToString("\n"),
            lyricSources = listOf(
                StreamingLyricSource(StreamingProviderName.LUOXUE, "LX/咪咕歌词", providerTrackId, 18)
            ),
            luoxueMusicInfoJson = sourceMusicInfo(value, "mg", copyrightId),
            playbackCandidates = qualities.map { quality ->
                StreamingPlaybackCandidate(
                    StreamingProviderName.LUOXUE,
                    quality,
                    "LX/咪咕",
                    providerTrackId,
                    true
                )
            }
        )
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
            title = cleanDisplayText(title).orEmpty(),
            artist = cleanDisplayText(artist).orEmpty(),
            artists = artist.takeIf { it.isNotBlank() }?.let {
                listOf(StreamingArtistRef(StreamingProviderName.LUOXUE, "kw:$it", it))
            }.orEmpty(),
            album = cleanDisplayText(album),
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
            luoxueMusicInfoJson = sourceMusicInfo(value, "kw", rawId),
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
        val cover = kugouImage(
            value.optionalStringLocal("imgurl")
                ?: value.optionalStringLocal("image")
                ?: value.optJSONObject("trans_param")?.optionalStringLocal("union_cover")
        )
        val playable = hash.isNotBlank()
        val qualities = kugouQualities(value)
        return StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = providerTrackId,
            title = cleanDisplayText(title).orEmpty(),
            artist = cleanDisplayText(artist).orEmpty(),
            artists = artist.takeIf { it.isNotBlank() }?.let {
                listOf(StreamingArtistRef(StreamingProviderName.LUOXUE, "kg:$it", it))
            }.orEmpty(),
            album = cleanDisplayText(album),
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
            luoxueMusicInfoJson = sourceMusicInfo(value, "kg", hash),
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
        if (source !in LUOXUE_SCRIPT_SOURCE_KEYS) return null
        val query = trimmed.substringAfter(':').trim()
        return if (query.isBlank()) null else source to query
    }

    private fun kuwoImage(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val relative = raw
            .substringAfter("albumcover/", raw)
            .replace(Regex("^\\d+/"), "")
            .trimStart('/')
        return relative.takeIf(String::isNotBlank)
            ?.let { "https://img1.kuwo.cn/star/albumcover/500/$it" }
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

    private fun sourceMusicInfo(value: JSONObject, source: String, sourceId: String): String? {
        val musicInfo = JSONObject(value.toString())
        musicInfo.put("source", source)
        musicInfo.put("type", source)
        if (!musicInfo.has("id")) {
            musicInfo.put("id", sourceId)
        }
        return normalizeLuoxueMusicInfoJson(musicInfo.toString())
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
            luoxueMusicInfoJson = luoxueMusicInfoJson ?: fallbackLuoxueMusicInfo(source),
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

    private fun StreamingTrack.fallbackLuoxueMusicInfo(source: String): String? {
        val info = JSONObject()
            .put("id", providerTrackId)
            .put("source", source)
            .put("type", source)
            .put("name", title)
            .put("title", title)
            .put("artist", artist)
            .put("album", album)
            .put("albumId", albumId)
            .put("duration", durationMs)
        when (source) {
            "tx" -> {
                info.put("songmid", providerTrackId)
                info.put("mid", providerTrackId)
            }
            "wy" -> info.put("songId", providerTrackId)
            "kw" -> {
                info.put("rid", providerTrackId)
                info.put("musicrid", "MUSIC_$providerTrackId")
            }
        }
        return normalizeLuoxueMusicInfoJson(info.toString())
    }

    private fun scriptMusicInfo(
        sourceId: SourceId,
        luoxueMusicInfoJson: String?
    ): Map<String, Any?> {
        val values = musicInfoFromJson(luoxueMusicInfoJson)
        values["source"] = sourceId.source
        values["type"] = sourceId.source
        values.putIfAbsent("id", sourceId.id)
        // Several shared LX scripts select hash first and songmid second regardless of the
        // platform key, so retain the legacy aliases whenever the source payload omits them.
        values.putIfAbsent("hash", sourceId.id)
        if (sourceId.source != "tx") {
            values.putIfAbsent("songmid", sourceId.id)
        }
        when (sourceId.source) {
            "kg" -> {
                val key = parseKugouProviderTrackId(sourceId.id)
                values["hash"] = key.hash
                key.albumId?.let { values.putIfAbsent("album_id", it) }
                key.albumAudioId?.let { values.putIfAbsent("album_audio_id", it) }
            }
            "tx" -> {
                val songMid = sourceId.id.substringBefore('|')
                val mediaMid = sourceId.id.substringAfter('|', "").takeIf { it.isNotBlank() }
                values.putIfAbsent("songmid", songMid)
                values.putIfAbsent("mid", songMid)
                mediaMid?.let {
                    values.putIfAbsent("mediaMid", it)
                    values.putIfAbsent("media_mid", it)
                    values.putIfAbsent("strMediaMid", it)
                }
            }
            "wy" -> values.putIfAbsent("songId", sourceId.id)
            "kw" -> {
                values.putIfAbsent("rid", sourceId.id)
                values.putIfAbsent("musicrid", "MUSIC_${sourceId.id}")
            }
            "mg" -> values.putIfAbsent("copyrightId", sourceId.id)
        }
        return values.filterValues { it != null }
    }

    private fun musicInfoFromJson(value: String?): LinkedHashMap<String, Any?> {
        val normalized = normalizeLuoxueMusicInfoJson(value) ?: return linkedMapOf()
        val json = runCatching { JSONObject(normalized) }.getOrNull() ?: return linkedMapOf()
        val result = linkedMapOf<String, Any?>()
        json.keys().asSequence().forEach { key ->
            result[key] = jsonValueToKotlin(json.opt(key))
        }
        return result
    }

    private fun jsonValueToKotlin(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL -> null
            is JSONObject -> {
                val objectValues = linkedMapOf<String, Any?>()
                value.keys().asSequence().forEach { key ->
                    objectValues[key] = jsonValueToKotlin(value.opt(key))
                }
                objectValues
            }
            is JSONArray -> (0 until value.length()).map { index -> jsonValueToKotlin(value.opt(index)) }
            else -> value
        }
    }

    private fun StreamingAudioQuality.toLuoxueScriptQualityCandidates(): List<String> = when (this) {
        StreamingAudioQuality.STANDARD -> listOf("128k")
        StreamingAudioQuality.HIGH -> listOf("320k", "128k")
        StreamingAudioQuality.LOSSLESS -> listOf("flac", "320k", "128k")
        StreamingAudioQuality.HIRES -> listOf("flac24bit", "flac", "320k", "128k")
    }

    private fun String.toStreamingAudioQuality(): StreamingAudioQuality = when (this) {
        "flac24bit", "hires", "hi-res" -> StreamingAudioQuality.HIRES
        "flac", "lossless" -> StreamingAudioQuality.LOSSLESS
        "320k", "high", "hq" -> StreamingAudioQuality.HIGH
        else -> StreamingAudioQuality.STANDARD
    }

    private fun String.isHttpUrl(): Boolean {
        return runCatching {
            val protocol = URL(this).protocol.lowercase()
            protocol == "http" || protocol == "https"
        }.getOrDefault(false)
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

    private fun JSONObject.firstTextValue(vararg names: String): String? {
        return names.firstNotNullOfOrNull { name -> textValue(name) }
    }

    private fun JSONObject.textValue(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return when (val raw = opt(name)) {
            is String -> raw.trim().takeIf(String::isNotBlank)
            is Number -> raw.toString()
            else -> null
        }
    }

    private fun JSONObject.firstLongValue(vararg names: String): Long? {
        return names.firstNotNullOfOrNull { name ->
            if (!has(name) || isNull(name)) {
                null
            } else {
                when (val raw = opt(name)) {
                    is Number -> raw.toLong()
                    else -> raw?.toString()?.trim()?.toDoubleOrNull()?.toLong()
                }
            }
        }
    }

    private fun cleanDisplayText(value: String?): String? {
        return value
            ?.replace(Regex("<[^>]+>"), " ")
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
        private const val SHIQIANJIANG_SOURCE_HOST = "source.shiqianjiang.cn"
        private val BUILT_IN_SEARCH_SOURCE_KEYS = listOf("tx", "wy", "kw", "kg", "mg")
        private val LUOXUE_SCRIPT_SOURCE_KEYS = setOf("kw", "kg", "tx", "wy", "mg", "git", "local")
    }
}
