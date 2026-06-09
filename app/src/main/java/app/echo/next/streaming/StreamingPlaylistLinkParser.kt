package app.echo.next.streaming

/**
 * Parses a playlist share link or raw id pasted by the user into a [StreamingProviderName] +
 * providerPlaylistId pair, so ECHO Next can fetch that playlist's detail through the gateway and
 * import it locally.
 *
 * Supports the common share-link formats for the providers ECHO Next integrates with, and falls
 * back to treating bare numeric / alphanumeric input as a raw playlist id for the currently
 * selected provider.
 */
object StreamingPlaylistLinkParser {

    data class ParsedPlaylistRef(
        val provider: StreamingProviderName,
        val providerPlaylistId: String
    )

    /**
     * @param input the pasted text (share URL or raw id).
     * @param fallbackProvider provider to assume when the input is a bare id with no recognizable host.
     */
    fun parse(input: String?, fallbackProvider: StreamingProviderName): ParsedPlaylistRef? {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) {
            return null
        }
        val lower = raw.lowercase()

        // NetEase Cloud Music: music.163.com/#/playlist?id=123 or .../playlist/123
        if (lower.contains("music.163.com") || lower.contains("163cn.tv")) {
            extractId(raw, listOf("id="), listOf("/playlist/"))?.let {
                return ParsedPlaylistRef(StreamingProviderName.NETEASE, it)
            }
        }
        // QQ Music: y.qq.com/.../playlist/123 or ...?id=123 or dissid
        if (lower.contains("y.qq.com") || lower.contains("c.y.qq.com")) {
            extractId(raw, listOf("id=", "dissid=", "disstid="), listOf("/playlist/", "/playsquare/"))?.let {
                return ParsedPlaylistRef(StreamingProviderName.QQ_MUSIC, it)
            }
        }
        // Kugou: kugou.com/.../special/single/123 or ...?global_collection_id=
        if (lower.contains("kugou.com")) {
            extractId(raw, listOf("global_collection_id=", "id="), listOf("/single/", "/special/single/"))?.let {
                return ParsedPlaylistRef(StreamingProviderName.KUGOU, it)
            }
        }
        // Bilibili: space favlist / medialist ml123
        if (lower.contains("bilibili.com") || lower.contains("b23.tv")) {
            extractId(raw, listOf("fid=", "media_id=", "id="), listOf("/medialist/detail/ml", "/favlist?fid="))?.let {
                return ParsedPlaylistRef(StreamingProviderName.BILIBILI, it)
            }
        }
        // YouTube / YouTube Music: ...?list=PLxxxx
        if (lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("music.youtube.com")) {
            extractId(raw, listOf("list="), emptyList())?.let {
                return ParsedPlaylistRef(StreamingProviderName.YOUTUBE, it)
            }
        }
        // SoundCloud: soundcloud.com/user/sets/playlist-name
        if (lower.contains("soundcloud.com")) {
            extractAfterSegment(raw, "/sets/")?.let {
                return ParsedPlaylistRef(StreamingProviderName.SOUNDCLOUD, it)
            }
        }
        // Spotify: open.spotify.com/playlist/xxxx or spotify:playlist:xxxx
        if (lower.contains("spotify.com") || lower.startsWith("spotify:")) {
            extractAfterSegment(raw, "/playlist/")?.let {
                return ParsedPlaylistRef(StreamingProviderName.SPOTIFY, stripQuery(it))
            }
            if (lower.startsWith("spotify:playlist:")) {
                return ParsedPlaylistRef(StreamingProviderName.SPOTIFY, raw.substringAfterLast(":"))
            }
        }
        // TIDAL: tidal.com/playlist/uuid or .../browse/playlist/uuid
        if (lower.contains("tidal.com")) {
            extractAfterSegment(raw, "/playlist/")?.let {
                return ParsedPlaylistRef(StreamingProviderName.TIDAL, stripQuery(it))
            }
        }

        // Bare id fallback for the currently selected provider (no recognizable host).
        if (!raw.contains("://") && !raw.contains(" ")) {
            val cleaned = raw.removePrefix("ml") // bilibili medialist often prefixed with ml
            if (cleaned.isNotEmpty()) {
                return ParsedPlaylistRef(fallbackProvider, cleaned)
            }
        }
        return null
    }

    /** Extract an id from query parameters (e.g. "id=") or path segments (e.g. "/playlist/"). */
    private fun extractId(
        raw: String,
        queryKeys: List<String>,
        pathSegments: List<String>
    ): String? {
        for (key in queryKeys) {
            val idx = raw.indexOf(key)
            if (idx >= 0) {
                val after = raw.substring(idx + key.length)
                val value = after.takeWhile { it.isLetterOrDigit() || it == '_' || it == '-' }
                if (value.isNotEmpty()) {
                    return value
                }
            }
        }
        for (segment in pathSegments) {
            extractAfterSegment(raw, segment)?.let { return stripQuery(it) }
        }
        return null
    }

    private fun extractAfterSegment(raw: String, segment: String): String? {
        val idx = raw.indexOf(segment)
        if (idx < 0) {
            return null
        }
        val after = raw.substring(idx + segment.length)
        val value = after.takeWhile { it != '/' && it != '?' && it != '#' && it != '&' && !it.isWhitespace() }
        return value.takeIf { it.isNotEmpty() }
    }

    private fun stripQuery(value: String): String {
        return value.substringBefore('?').substringBefore('#').substringBefore('&')
    }
}
