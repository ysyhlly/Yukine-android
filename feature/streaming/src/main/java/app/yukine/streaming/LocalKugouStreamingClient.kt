package app.yukine.streaming

/**
 * First-class Kugou adapter backed by the existing LX/KG transport implementation.
 *
 * LX identities are translated at this boundary. Existing `luoxue:kg:*` records remain valid,
 * while all new results use `kugou:<hash>.<albumId>.<albumAudioId>`.
 */
class LocalKugouStreamingClient(
    private val luoxueClient: LocalLuoxueStreamingClient = LocalLuoxueStreamingClient()
) {
    fun search(request: StreamingSearchRequest): StreamingSearchResult {
        val cleanQuery = request.query
            .trim()
            .removePrefixIgnoreCase("kugou:")
            .removePrefixIgnoreCase("kg:")
            .trim()
        val legacy = luoxueClient.search(
            request.copy(
                provider = StreamingProviderName.LUOXUE,
                query = if (cleanQuery.isBlank()) "" else "kg:$cleanQuery"
            )
        )
        return legacy.copy(
            provider = StreamingProviderName.KUGOU,
            query = request.query.trim(),
            tracks = legacy.tracks.map { it.asKugouTrack() },
            playlists = legacy.playlists.map { it.asKugouPlaylist() },
            items = emptyList()
        )
    }

    fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        val playlistId = KugouIdentity.canonicalPlaylistId(request.providerPlaylistId)
        val legacy = luoxueClient.playlist(
            request.copy(
                provider = StreamingProviderName.LUOXUE,
                providerPlaylistId = "kg:$playlistId"
            )
        )
        return legacy.copy(
            provider = StreamingProviderName.KUGOU,
            providerPlaylistId = playlistId,
            playlist = legacy.playlist?.asKugouPlaylist(),
            tracks = legacy.tracks.map { it.asKugouTrack() }
        )
    }

    fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        val trackId = KugouIdentity.canonicalTrackId(request.providerTrackId)
            ?: throw StreamingGatewayException(
                "酷狗歌曲 ID 无效",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        val legacy = luoxueClient.resolvePlayback(
            request.copy(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:$trackId"
            )
        )
        return legacy.copy(
            provider = StreamingProviderName.KUGOU,
            providerTrackId = trackId
        )
    }

    private fun StreamingTrack.asKugouTrack(): StreamingTrack {
        val trackId = KugouIdentity.canonicalTrackId(providerTrackId)
            ?: providerTrackId.removePrefixIgnoreCase("kg:")
        return copy(
            provider = StreamingProviderName.KUGOU,
            providerTrackId = trackId,
            artists = artists.map { artist ->
                artist.copy(
                    provider = StreamingProviderName.KUGOU,
                    providerArtistId = artist.providerArtistId.removePrefixIgnoreCase("kg:")
                )
            },
            description = description
                ?.replace("LX/洛雪 · 酷狗子源", "酷狗音乐")
                ?.replace("LX/酷狗", "酷狗"),
            lyricSources = lyricSources.map { source ->
                source.copy(
                    provider = StreamingProviderName.KUGOU,
                    name = source.name.replace("LX/", ""),
                    providerTrackId = source.providerTrackId
                        ?.let(KugouIdentity::canonicalTrackId)
                        ?: trackId
                )
            },
            playbackCandidates = playbackCandidates.map { candidate ->
                candidate.copy(
                    provider = StreamingProviderName.KUGOU,
                    label = candidate.label.replace("LX/", ""),
                    providerTrackId = candidate.providerTrackId
                        ?.let(KugouIdentity::canonicalTrackId)
                        ?: trackId,
                    luoxueMusicInfoJson = null
                )
            },
            luoxueMusicInfoJson = null
        )
    }

    private fun StreamingPlaylist.asKugouPlaylist(): StreamingPlaylist = copy(
        provider = StreamingProviderName.KUGOU,
        providerPlaylistId = KugouIdentity.canonicalPlaylistId(providerPlaylistId),
        title = title.removePrefix("LX/")
    )
}

object KugouIdentity {
    fun canonicalTrackId(value: String?): String? {
        val raw = value.orEmpty()
            .trim()
            .removePrefixIgnoreCase("kugou:")
            .removePrefixIgnoreCase("kg:")
            .trim()
        if (raw.isBlank()) return null
        val parts = raw.split('.')
        val hash = parts.getOrNull(0).orEmpty().trim().lowercase()
        if (hash.isBlank()) return null
        val albumId = parts.getOrNull(1).orEmpty().trim().ifBlank { "0" }
        val albumAudioId = parts.getOrNull(2).orEmpty().trim().ifBlank { "0" }
        return "$hash.$albumId.$albumAudioId"
    }

    fun canonicalPlaylistId(value: String?): String = value.orEmpty()
        .trim()
        .removePrefixIgnoreCase("kugou:")
        .removePrefixIgnoreCase("kg:")
        .trim()

    fun legacyLuoxueTrackId(value: String?): String? =
        canonicalTrackId(value)?.let { "kg:$it" }
}

private fun String.removePrefixIgnoreCase(prefix: String): String =
    if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
