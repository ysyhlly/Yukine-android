package app.yukine.common

import app.yukine.streaming.StreamingProviderName
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object StreamingDataPathMetadata {
    private const val DATA_PATH_PREFIX = "streaming:"
    private const val QUALITY_MARKER = "quality="
    private const val LUOXUE_MUSIC_INFO_QUERY = "lxmi"

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
