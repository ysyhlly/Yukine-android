package app.yukine.streaming

data class StreamingGatewayLogEntry(
    val operation: String,
    val provider: StreamingProviderName? = null,
    val durationMs: Long,
    val cacheHit: Boolean = false,
    val errorCode: StreamingErrorCode? = null,
    val message: String? = null,
    val timestampMs: Long
)

data class StreamingGatewayDiagnostics(
    val totalRequests: Int = 0,
    val cacheHits: Int = 0,
    val recentLogs: List<StreamingGatewayLogEntry> = emptyList()
) {
    val cacheHitRate: Int
        get() = if (totalRequests <= 0) 0 else (cacheHits * 100 / totalRequests)
}

data class StreamingArtistRef(
    val provider: StreamingProviderName,
    val providerArtistId: String,
    val name: String
)

data class StreamingLyricSource(
    val provider: StreamingProviderName,
    val name: String,
    val providerTrackId: String? = null,
    val priority: Int = 0
)

data class StreamingPlaybackCandidate(
    val provider: StreamingProviderName,
    val quality: StreamingAudioQuality? = null,
    val label: String = provider.wireName,
    val providerTrackId: String? = null,
    val available: Boolean = true,
    /** Complete LX musicInfo for this specific alternate source, when applicable. */
    val luoxueMusicInfoJson: String? = null
)

data class StreamingTrack(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val title: String,
    val artist: String,
    val artists: List<StreamingArtistRef> = emptyList(),
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long? = null,
    val coverUrl: String? = null,
    val coverThumbUrl: String? = null,
    val qualities: Set<StreamingAudioQuality> = emptySet(),
    val explicit: Boolean = false,
    val playable: Boolean = true,
    val unavailableReason: String? = null,
    val description: String? = null,
    val lyricSources: List<StreamingLyricSource> = emptyList(),
    val playbackCandidates: List<StreamingPlaybackCandidate> = emptyList(),
    /**
     * Source-specific LX musicInfo object, serialized as a JSON object. It is retained only for
     * LX tracks so imported JS sources can receive the original fields after cache/queue restore.
     */
    val luoxueMusicInfoJson: String? = null,
    /** International recording code used as the strongest cross-provider identity signal. */
    val isrc: String? = null
) {
    val stableKey: String = "streaming:${provider.wireName}:$providerTrackId"

    val playbackSourceCount: Int
        get() {
            val identities = linkedSetOf(stableKey)
            playbackCandidates.forEach { candidate ->
                val candidateTrackId = candidate.providerTrackId?.trim().orEmpty()
                when {
                    candidateTrackId.isNotBlank() -> identities +=
                        "streaming:${candidate.provider.wireName}:$candidateTrackId"
                    candidate.provider == provider -> identities += stableKey
                }
            }
            return identities.size
        }
}

data class StreamingAlbum(
    val provider: StreamingProviderName,
    val providerAlbumId: String,
    val title: String,
    val artist: String,
    val coverUrl: String? = null,
    val trackCount: Int? = null
)

data class StreamingArtist(
    val provider: StreamingProviderName,
    val providerArtistId: String,
    val name: String,
    val avatarUrl: String? = null
)

data class StreamingPlaylist(
    val provider: StreamingProviderName,
    val providerPlaylistId: String,
    val title: String,
    val description: String? = null,
    val creator: String? = null,
    val coverUrl: String? = null,
    val trackCount: Int? = null
)

data class StreamingMvItem(
    val provider: StreamingProviderName,
    val providerMvId: String,
    val providerTrackId: String? = null,
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null
)

data class StreamingSearchRequest(
    val provider: StreamingProviderName,
    val query: String,
    val mediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
    val page: Int = 1,
    val pageSize: Int = 20
)

data class StreamingSearchError(
    val code: StreamingErrorCode,
    val message: String? = null
)

data class StreamingSearchItem(
    val type: StreamingMediaType,
    val provider: StreamingProviderName,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val playable: Boolean = true,
    val track: StreamingTrack? = null,
    val album: StreamingAlbum? = null,
    val artist: StreamingArtist? = null,
    val playlist: StreamingPlaylist? = null,
    val mv: StreamingMvItem? = null
) {
    companion object {
        fun fromTrack(track: StreamingTrack): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.TRACK,
                provider = track.provider,
                id = track.providerTrackId,
                title = track.title,
                subtitle = listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album).joinToString(" - ")
                    .takeIf { it.isNotBlank() },
                imageUrl = track.coverThumbUrl ?: track.coverUrl,
                playable = track.playable,
                track = track
            )
        }

        fun fromAlbum(album: StreamingAlbum): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.ALBUM,
                provider = album.provider,
                id = album.providerAlbumId,
                title = album.title,
                subtitle = album.artist,
                imageUrl = album.coverUrl,
                album = album
            )
        }

        fun fromArtist(artist: StreamingArtist): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.ARTIST,
                provider = artist.provider,
                id = artist.providerArtistId,
                title = artist.name,
                imageUrl = artist.avatarUrl,
                artist = artist
            )
        }

        fun fromPlaylist(playlist: StreamingPlaylist): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.PLAYLIST,
                provider = playlist.provider,
                id = playlist.providerPlaylistId,
                title = playlist.title,
                subtitle = playlist.creator ?: playlist.description,
                imageUrl = playlist.coverUrl,
                playlist = playlist
            )
        }

        fun fromMv(mv: StreamingMvItem): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.MV,
                provider = mv.provider,
                id = mv.providerMvId,
                title = mv.title,
                subtitle = mv.artist,
                imageUrl = mv.thumbnailUrl,
                mv = mv
            )
        }
    }
}

data class StreamingSearchResult(
    val provider: StreamingProviderName,
    val query: String,
    val page: Int,
    val pageSize: Int,
    val total: Int? = null,
    val hasMore: Boolean = false,
    val tracks: List<StreamingTrack> = emptyList(),
    val albums: List<StreamingAlbum> = emptyList(),
    val artists: List<StreamingArtist> = emptyList(),
    val playlists: List<StreamingPlaylist> = emptyList(),
    val mvs: List<StreamingMvItem> = emptyList(),
    val cached: Boolean = false,
    val items: List<StreamingSearchItem> = emptyList(),
    val error: StreamingSearchError? = null
) {
    val unifiedItems: List<StreamingSearchItem>
        get() = items.ifEmpty {
            tracks.map { StreamingSearchItem.fromTrack(it) } +
                albums.map { StreamingSearchItem.fromAlbum(it) } +
                artists.map { StreamingSearchItem.fromArtist(it) } +
                playlists.map { StreamingSearchItem.fromPlaylist(it) } +
                mvs.map { StreamingSearchItem.fromMv(it) }
        }
}

data class StreamingPlaylistRequest(
    val provider: StreamingProviderName,
    val providerPlaylistId: String,
    val page: Int = 1,
    val pageSize: Int = 500
)

data class StreamingPlaylistDetail(
    val provider: StreamingProviderName,
    val providerPlaylistId: String,
    val playlist: StreamingPlaylist? = null,
    val tracks: List<StreamingTrack> = emptyList(),
    val total: Int? = null,
    val page: Int = 1,
    val pageSize: Int = 500,
    val hasMore: Boolean = false,
    val cached: Boolean = false
)

data class StreamingHeartbeatRequest(
    val provider: StreamingProviderName,
    val providerTrackId: String? = null,
    val providerPlaylistId: String? = null,
    val count: Int = 30
)

data class StreamingPlaybackRequest(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
    val luoxueMusicInfoJson: String? = null
)

data class StreamingPlaybackSource(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val url: String,
    val expiresAtEpochMs: Long? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val codec: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val requiresProxy: Boolean = false,
    val supportsRange: Boolean = true
)

data class StreamingAuthRequest(
    val provider: StreamingProviderName,
    val redirectUri: String? = null,
    val forceRefresh: Boolean = false
)

data class StreamingAuthResult(
    val provider: StreamingProviderName,
    val state: StreamingAuthState,
    val launchUrl: String? = null,
    val statusMessage: String? = null
)
