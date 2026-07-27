package app.yukine.common

import app.yukine.streaming.StreamingProviderName
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

object StreamingDataPathMetadata {
    private const val DATA_PATH_PREFIX = "streaming:"
    private const val QUALITY_MARKER = "quality="
    private const val LUOXUE_MUSIC_INFO_QUERY = "lxmi"
    private const val PLAYBACK_MIME_TYPE_QUERY = "playbackMime"
    private const val MAX_LUOXUE_MUSIC_INFO_BYTES = 24 * 1024

    @JvmStatic
    fun isStreamingTrack(dataPath: String?): Boolean {
        return dataPath?.startsWith(DATA_PATH_PREFIX) == true
    }

    @JvmStatic
    fun provider(dataPath: String?): StreamingProviderName? {
        return parsedDataPath(dataPath)?.provider
    }

    @JvmStatic
    fun providerName(dataPath: String?): String? {
        return provider(dataPath)?.wireName
    }

    @JvmStatic
    fun providerTrackId(dataPath: String?): String {
        return parsedDataPath(dataPath)?.providerTrackId.orEmpty()
    }

    /**
     * Builds the canonical logical identity for a streaming track while keeping short-lived
     * playback URLs out of the persisted queue identity.
     */
    @JvmStatic
    fun streamingDataPath(
        provider: String,
        providerTrackId: String,
        quality: String = "",
        playbackMimeType: String = "",
        luoxueMusicInfoJson: String? = null
    ): String {
        val normalizedProvider = StreamingProviderName.fromWireName(provider.trim()) ?: return ""
        val normalizedTrackId = providerTrackId.trim()
            .takeIf { it.isNotBlank() && '?' !in it && '#' !in it }
            ?: return ""
        val parameters = linkedMapOf<String, String>()
        quality.trim().takeIf(String::isNotBlank)?.let { parameters["quality"] = it }
        normalizedLuoxueMusicInfoJson(luoxueMusicInfoJson)?.let { normalized ->
            parameters[LUOXUE_MUSIC_INFO_QUERY] = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalized.toByteArray(StandardCharsets.UTF_8))
        }
        normalizedMimeType(playbackMimeType)?.let { mimeType ->
            parameters[PLAYBACK_MIME_TYPE_QUERY] = mimeType
        }
        val base = "$DATA_PATH_PREFIX${normalizedProvider.wireName}:$normalizedTrackId"
        if (parameters.isEmpty()) {
            return base
        }
        return parameters.entries.joinToString(prefix = "$base?", separator = "&") { entry ->
            "${entry.key}=${URLEncoder.encode(entry.value, StandardCharsets.UTF_8.name())}"
        }
    }

    @JvmStatic
    fun quality(dataPath: String?): String {
        if (dataPath.isNullOrBlank()) {
            return ""
        }
        val markerStart = dataPath.indexOf(QUALITY_MARKER)
        if (markerStart < 0) {
            return ""
        }
        val valueStart = markerStart + QUALITY_MARKER.length
        val valueEnd = listOf(
            dataPath.indexOf(':', valueStart),
            dataPath.indexOf('|', valueStart),
            dataPath.indexOf('&', valueStart),
            dataPath.indexOf('#', valueStart)
        ).filter { it >= 0 }.minOrNull() ?: dataPath.length
        return dataPath.substring(valueStart, valueEnd).trim().lowercase()
    }

    @JvmStatic
    fun playbackMimeType(dataPath: String?): String {
        if (!isStreamingTrack(dataPath)) {
            return ""
        }
        val encoded = dataPath
            ?.substringAfter('?', "")
            ?.substringBefore('#')
            ?.split('&')
            ?.firstOrNull { parameter ->
                parameter.substringBefore('=') == PLAYBACK_MIME_TYPE_QUERY
            }
            ?.substringAfter('=', "")
            .orEmpty()
        if (encoded.isBlank()) {
            return ""
        }
        return normalizedMimeType(
            runCatching {
                URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()).trim()
            }.getOrDefault("")
        ).orEmpty()
    }

    /**
     * Restores the bounded LX musicInfo embedded in a queue-safe streaming dataPath.
     *
     * This payload remains host-private; callers use it only to refresh an expiring playback URL.
     */
    @JvmStatic
    fun luoxueMusicInfoJson(dataPath: String?): String? {
        if (!isStreamingTrack(dataPath)) {
            return null
        }
        val encoded = queryParameter(dataPath, LUOXUE_MUSIC_INFO_QUERY)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return normalizedLuoxueMusicInfoJson(
            runCatching {
                Base64.getUrlDecoder()
                    .decode(encoded)
                    .toString(StandardCharsets.UTF_8)
            }.getOrNull()
        )
    }

    /**
     * Returns a bounded identity for in-memory headers and media caches. Queue persistence still
     * keeps the original dataPath, including LX musicInfo, but cache keys retain only a digest of
     * that opaque payload.
     */
    @JvmStatic
    fun cacheIdentity(dataPath: String?): String? {
        val value = dataPath ?: return null
        if (!isStreamingTrack(value)) {
            return value
        }
        val fragmentStart = value.indexOf('#')
        val beforeFragment = if (fragmentStart >= 0) value.substring(0, fragmentStart) else value
        val fragment = if (fragmentStart >= 0) value.substring(fragmentStart) else ""
        val queryStart = beforeFragment.indexOf('?')
        if (queryStart < 0) {
            return value
        }
        val compactQuery = beforeFragment.substring(queryStart + 1)
            .split('&')
            .joinToString("&") { parameter ->
                val key = parameter.substringBefore('=')
                if (key == LUOXUE_MUSIC_INFO_QUERY) {
                    "${LUOXUE_MUSIC_INFO_QUERY}Hash=" + sha256(parameter.substringAfter('=', ""))
                } else {
                    parameter
                }
            }
        return beforeFragment.substring(0, queryStart) + "?" + compactQuery + fragment
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun normalizedLuoxueMusicInfoJson(value: String?): String? {
        val raw = value
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (raw.toByteArray(StandardCharsets.UTF_8).size > MAX_LUOXUE_MUSIC_INFO_BYTES) {
            return null
        }
        return runCatching { JSONObject(raw).toString() }
            .getOrNull()
            ?.takeIf {
                it.toByteArray(StandardCharsets.UTF_8).size <= MAX_LUOXUE_MUSIC_INFO_BYTES
            }
    }

    private fun normalizedMimeType(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        val separator = normalized.indexOf('/')
        return normalized.takeIf {
            it.length in 3..128 &&
                separator in 1 until it.lastIndex &&
                it.substring(0, separator).all(Char::isLetterOrDigit) &&
                it.substring(separator + 1).all { character ->
                    character.isLetterOrDigit() || character in ".+-"
                }
        }
    }

    private fun queryParameter(dataPath: String?, name: String): String? {
        val encoded = dataPath
            ?.substringAfter('?', "")
            ?.substringBefore('#')
            ?.split('&')
            ?.firstOrNull { parameter -> parameter.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?: return null
        return runCatching {
            URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()).trim()
        }.getOrNull()
    }

    private fun parsedDataPath(dataPath: String?): ParsedStreamingDataPath? {
        if (dataPath.isNullOrBlank()) {
            return null
        }
        val markerStart = dataPath.indexOf(DATA_PATH_PREFIX)
        if (markerStart < 0) {
            return null
        }
        val remainder = dataPath.substring(markerStart + DATA_PATH_PREFIX.length)
        val providerEnd = remainder.indexOf(':')
        if (providerEnd <= 0 || providerEnd >= remainder.length - 1) {
            return null
        }
        val provider = StreamingProviderName.fromWireName(remainder.substring(0, providerEnd)) ?: return null
        val rawTrackId = remainder.substring(providerEnd + 1)
        // Only strip URL-style delimiters appended by metadata query/fragment data.
        // QQ Music providerTrackId can contain '|', so it must be preserved.
        val trackId = rawTrackId.substringBefore('?')
            .substringBefore('#')
            .trim()
        if (trackId.isBlank()) {
            return null
        }
        return ParsedStreamingDataPath(provider, trackId)
    }

    private data class ParsedStreamingDataPath(
        val provider: StreamingProviderName,
        val providerTrackId: String
    )
}
