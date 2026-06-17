package app.yukine.streaming

class MockStreamingProvider : StreamingProvider {
    private val demoTracks = listOf(
        StreamingTrack(
            provider = StreamingProviderName.MOCK,
            providerTrackId = "mock-track-echo",
            title = "回声测试",
            artist = "本地演示",
            album = "离线样例",
            durationMs = 184_000L,
            coverUrl = "https://picsum.photos/seed/echo-mock-track/512/512",
            qualities = setOf(StreamingAudioQuality.STANDARD, StreamingAudioQuality.HIGH),
            playable = true
        ),
        StreamingTrack(
            provider = StreamingProviderName.MOCK,
            providerTrackId = "mock-track-night",
            title = "夜航片段",
            artist = "ECHO Demo",
            album = "离线样例",
            durationMs = 211_000L,
            coverUrl = "https://picsum.photos/seed/echo-mock-night/512/512",
            qualities = setOf(StreamingAudioQuality.STANDARD),
            playable = true
        ),
        StreamingTrack(
            provider = StreamingProviderName.MOCK,
            providerTrackId = "mock-track-rain",
            title = "雨声循环",
            artist = "环境采样",
            album = "专注播放",
            durationMs = 300_000L,
            coverUrl = "https://picsum.photos/seed/echo-mock-rain/512/512",
            qualities = setOf(StreamingAudioQuality.STANDARD),
            playable = true
        )
    )

    private val demoPlaylist = StreamingPlaylist(
        provider = StreamingProviderName.MOCK,
        providerPlaylistId = "mock-playlist-daily",
        title = "离线试听歌单",
        description = "无需网关即可验证搜索、歌单和播放链路",
        creator = "ECHO",
        coverUrl = "https://picsum.photos/seed/echo-mock-playlist/512/512",
        trackCount = demoTracks.size
    )

    override val descriptor: StreamingProviderDescriptor = StreamingProviderDescriptor(
        name = StreamingProviderName.MOCK,
        displayName = "离线演示",
        enabled = true,
        capabilities = StreamingProviderCapabilities(
            supportsSearch = true,
            supportsPlayback = true,
            supportsLyrics = false,
            supportsMv = false,
            supportsAuth = false,
            supportsFavorites = false,
            supportsPlaylists = true,
            supportedMediaTypes = setOf(
                StreamingMediaType.TRACK,
                StreamingMediaType.ALBUM,
                StreamingMediaType.ARTIST,
                StreamingMediaType.PLAYLIST
            )
        ),
        auth = StreamingAuthState(kind = StreamingAuthKind.NONE, connected = true),
        status = StreamingProviderStatus.READY,
        statusMessage = "离线演示数据可用"
    )

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
        val query = request.query.trim()
        val tracks = if (request.mediaTypes.contains(StreamingMediaType.TRACK)) {
            demoTracks.filter { track ->
                query.isBlank() ||
                    track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.orEmpty().contains(query, ignoreCase = true)
            }
        } else {
            emptyList()
        }
        val albums = if (request.mediaTypes.contains(StreamingMediaType.ALBUM)) {
            listOf(
                StreamingAlbum(
                    provider = StreamingProviderName.MOCK,
                    providerAlbumId = "mock-album-offline",
                    title = "离线样例",
                    artist = "本地演示",
                    coverUrl = "https://picsum.photos/seed/echo-mock-album/512/512",
                    trackCount = 2
                )
            ).filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
        } else {
            emptyList()
        }
        val artists = if (request.mediaTypes.contains(StreamingMediaType.ARTIST)) {
            listOf(
                StreamingArtist(
                    provider = StreamingProviderName.MOCK,
                    providerArtistId = "mock-artist-demo",
                    name = "本地演示",
                    avatarUrl = "https://picsum.photos/seed/echo-mock-artist/512/512"
                )
            ).filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        } else {
            emptyList()
        }
        val playlists = if (request.mediaTypes.contains(StreamingMediaType.PLAYLIST)) {
            listOf(demoPlaylist).filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.description.orEmpty().contains(query, ignoreCase = true)
            }
        } else {
            emptyList()
        }
        val total = tracks.size + albums.size + artists.size + playlists.size
        return StreamingSearchResult(
            provider = StreamingProviderName.MOCK,
            query = query,
            page = request.page,
            pageSize = request.pageSize,
            total = total,
            hasMore = false,
            tracks = tracks,
            albums = albums,
            artists = artists,
            playlists = playlists
        )
    }

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        return StreamingPlaylistDetail(
            provider = StreamingProviderName.MOCK,
            providerPlaylistId = request.providerPlaylistId,
            playlist = demoPlaylist.copy(providerPlaylistId = request.providerPlaylistId),
            tracks = demoTracks,
            total = demoTracks.size
        )
    }

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        val track = demoTracks.firstOrNull { it.providerTrackId == request.providerTrackId }
        return StreamingPlaybackSource(
            provider = StreamingProviderName.MOCK,
            providerTrackId = request.providerTrackId,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            mimeType = "audio/mpeg",
            bitrate = 128,
            codec = "mp3",
            headers = mapOf("X-ECHO-Mock-Track" to (track?.title ?: request.providerTrackId)),
            supportsRange = true
        )
    }
}
