package app.yukine.streaming

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface BilibiliTarget {
    val providerPlaylistId: String

    data class Video(
        val videoId: String,
        val page: Int? = null
    ) : BilibiliTarget {
        override val providerPlaylistId: String
            get() = buildString {
                append("video:")
                append(videoId)
                page?.let {
                    append(":p:")
                    append(it)
                }
            }
    }

    data class Favorite(val mediaId: Long) : BilibiliTarget {
        override val providerPlaylistId: String = "favorite:$mediaId"
    }

    data class ShortLink(val url: String) : BilibiliTarget {
        override val providerPlaylistId: String =
            "short:${URLEncoder.encode(url, StandardCharsets.UTF_8.name())}"
    }
}

/**
 * Pure parser for Bilibili video, multi-page video, favorite-folder and b23 short links.
 *
 * The encoded [BilibiliTarget.providerPlaylistId] is deliberately stable because it is persisted
 * in local playlist sync metadata.
 */
object BilibiliLinkParser {
    private val bvidPattern = Regex("""(?i)\b(BV[0-9A-Za-z]{10})\b""")
    private val aidPattern = Regex("""(?i)(?:^|[/\s])(av\d+)(?=$|[/?#&\s])""")
    private val pagePattern = Regex("""(?i)[?&]p=(\d+)""")
    private val favoritePattern = Regex("""(?i)[?&](?:fid|media_id)=(\d+)""")
    private val mediaListPattern = Regex("""(?i)/medialist/detail/ml(\d+)""")
    private val sharedUrlPattern = Regex("""https://(?:www\.)?(?:b23\.tv|bilibili\.com)/[^\s]+""", RegexOption.IGNORE_CASE)

    fun parse(input: String?): BilibiliTarget? {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return null

        decodeProviderPlaylistId(raw)?.let { return it }

        val sharedUrl = sharedUrlPattern.find(raw)?.value?.trimEnd('。', '，', ',', ')', '）', ']', '】')
        val candidate = sharedUrl ?: raw
        if (candidate.contains("b23.tv", ignoreCase = true)) {
            return BilibiliTarget.ShortLink(candidate)
        }

        favoritePattern.find(candidate)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
            return BilibiliTarget.Favorite(it)
        }
        mediaListPattern.find(candidate)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
            return BilibiliTarget.Favorite(it)
        }

        val page = pagePattern.find(candidate)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it > 0 }
        bvidPattern.find(candidate)?.groupValues?.getOrNull(1)?.let {
            return BilibiliTarget.Video(normalizeBvid(it), page)
        }
        aidPattern.find(candidate)?.groupValues?.getOrNull(1)?.let {
            return BilibiliTarget.Video(it.lowercase(), page)
        }

        return when {
            raw.matches(Regex("""(?i)^BV[0-9A-Za-z]{10}$""")) ->
                BilibiliTarget.Video(normalizeBvid(raw))
            raw.matches(Regex("""(?i)^av\d+$""")) ->
                BilibiliTarget.Video(raw.lowercase())
            raw.matches(Regex("""^\d+$""")) ->
                raw.toLongOrNull()?.let(BilibiliTarget::Favorite)
            else -> null
        }
    }

    fun decodeProviderPlaylistId(value: String): BilibiliTarget? {
        val raw = value.trim()
        if (raw.startsWith("favorite:", ignoreCase = true)) {
            return raw.substringAfter(':').toLongOrNull()?.let(BilibiliTarget::Favorite)
        }
        if (raw.startsWith("short:", ignoreCase = true)) {
            val encoded = raw.substringAfter(':')
            return runCatching {
                URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
            }.getOrNull()?.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?.let(BilibiliTarget::ShortLink)
        }
        if (raw.startsWith("video:", ignoreCase = true)) {
            val match = Regex("""(?i)^video:(BV[0-9A-Za-z]{10}|av\d+)(?::p:(\d+))?$""").matchEntire(raw)
                ?: return null
            val videoId = match.groupValues[1].let {
                if (it.startsWith("BV", ignoreCase = true)) normalizeBvid(it) else it.lowercase()
            }
            val page = match.groupValues.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }
            return BilibiliTarget.Video(videoId, page)
        }
        return null
    }

    private fun normalizeBvid(value: String): String = "BV" + value.substring(2)
}
