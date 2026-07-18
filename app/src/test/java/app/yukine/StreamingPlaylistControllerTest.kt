package app.yukine

import app.yukine.model.PlaylistImportResult
import app.yukine.model.Track
import app.yukine.streaming.StreamingAuthResult
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingGateway
import app.yukine.streaming.StreamingHeartbeatRequest
import app.yukine.streaming.StreamingPlaybackRequest
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingPlaylistDetail
import app.yukine.streaming.StreamingPlaylistRequest
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingPlaylistSyncDirection
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProvider
import app.yukine.streaming.StreamingProviderCapabilities
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingSearchRequest
import app.yukine.streaming.StreamingSearchResult
import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingRepository
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StreamingPlaylistControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loginSuccessLoadsAccountPlaylistsAndShowsImportPicker() = runTest {
        val gateway = FakeGateway(
            userPlaylistsResult = listOf(
                StreamingPlaylist(
                    provider = StreamingProviderName.NETEASE,
                    providerPlaylistId = "100",
                    title = "每日推荐",
                    trackCount = 12
                )
            )
        )
        val operations = FakeStreamingLocalPlaylistOperations()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        val listener = FakeStreamingPlaylistListener()
        val controller = StreamingPlaylistController(
            viewModel,
            StreamingPlaylistController.LanguageProvider { AppLanguage.MODE_CHINESE },
            listener
        )

        controller.onStreamingLoginSuccess(StreamingProviderName.NETEASE)
        assertTrue(listener.awaitPicker())

        assertEquals(listOf(StreamingProviderName.NETEASE), operations.ensureProviders)
        assertEquals(listOf(StreamingProviderName.NETEASE), gateway.userPlaylistProviders)
        assertEquals(listOf("picker:netease:100"), listener.events.filter { it.startsWith("picker:") })
        assertTrue(listener.events.contains("refreshLibrary"))
        assertEquals(9L, listener.selectedPlaylistIdValue)
    }

    @Test
    fun syncSelectedPlaylistRefreshesLibraryAfterSuccessfulSync() = runTest {
        val gateway = FakeGateway(userPlaylistsResult = emptyList())
        val operations = FakeStreamingLocalPlaylistOperations()
        operations.linkedPlaylist = StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = 42L,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "100",
            lastSyncMs = 0L
        )
        gateway.playlistDetail = StreamingPlaylistDetail(
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "100",
            playlist = StreamingPlaylist(StreamingProviderName.NETEASE, "100", "每日推荐"),
            tracks = listOf(streamingTrack("1")),
            total = 1,
            hasMore = false
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        val listener = FakeStreamingPlaylistListener()
        listener.selectedPlaylistIdValue = 42L
        val controller = StreamingPlaylistController(
            viewModel,
            StreamingPlaylistController.LanguageProvider { AppLanguage.MODE_CHINESE },
            listener
        )

        controller.syncSelectedPlaylistFromStreaming()
        assertTrue(listener.awaitRefresh())

        assertEquals(listOf(42L), operations.syncPlaylistIds)
        assertTrue(listener.events.contains("refreshLibrary"))
    }

    @Test
    fun selectedAccountPlaylistImportRefreshesLibraryAfterImport() = runTest {
        val gateway = FakeGateway(userPlaylistsResult = emptyList())
        gateway.playlistDetail = StreamingPlaylistDetail(
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "100",
            playlist = StreamingPlaylist(StreamingProviderName.NETEASE, "100", "每日推荐"),
            tracks = listOf(streamingTrack("1")),
            total = 1,
            hasMore = false
        )
        val operations = FakeStreamingLocalPlaylistOperations()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        val listener = FakeStreamingPlaylistListener()
        val controller = StreamingPlaylistController(
            viewModel,
            StreamingPlaylistController.LanguageProvider { AppLanguage.MODE_CHINESE },
            listener
        )

        controller.importSelectedAccountPlaylists(
            StreamingProviderName.NETEASE,
            listOf(StreamingPlaylist(StreamingProviderName.NETEASE, "100", "每日推荐"))
        )
        assertTrue(listener.awaitRefresh())

        assertEquals(listOf("100"), operations.importProviderPlaylistIds)
        assertTrue(listener.events.contains("refreshLibrary"))
    }

    @Test
    fun bilibiliProviderReferenceLoadsPreviewBeforeImport() = runTest {
        val gateway = FakeGateway(userPlaylistsResult = emptyList())
        gateway.playlistDetail = StreamingPlaylistDetail(
            provider = StreamingProviderName.BILIBILI,
            providerPlaylistId = "favorite:42",
            playlist = StreamingPlaylist(
                StreamingProviderName.BILIBILI,
                "favorite:42",
                "音乐收藏"
            ),
            tracks = listOf(
                StreamingTrack(
                    provider = StreamingProviderName.BILIBILI,
                    providerTrackId = "video:BV1U64y1a785:cid:101",
                    title = "收藏视频",
                    artist = "测试 UP"
                )
            ),
            total = 1,
            hasMore = false
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        val listener = FakeStreamingPlaylistListener()
        val controller = StreamingPlaylistController(
            viewModel,
            StreamingPlaylistController.LanguageProvider { AppLanguage.MODE_CHINESE },
            listener
        )

        controller.importStreamingPlaylistFromProviderRef(
            StreamingProviderName.BILIBILI,
            "favorite:42"
        )
        assertTrue(listener.awaitPreview())

        assertEquals(
            listOf("preview:bilibili:favorite:42:音乐收藏:1"),
            listener.events.filter { it.startsWith("preview:") }
        )
    }

    @Test
    fun successfulLocalPlaylistExportLinksQqMirrorForFutureSync() = runTest {
        val matched = StreamingTrack(
            provider = StreamingProviderName.QQ_MUSIC,
            providerTrackId = "qq-1",
            title = "Local song",
            artist = "Artist"
        )
        val gateway = FakeGateway(
            userPlaylistsResult = emptyList(),
            searchTracksResult = listOf(matched),
            providerCapabilitiesResult = listOf(
                StreamingProviderCapability(
                    provider = StreamingProviderName.QQ_MUSIC,
                    displayName = "QQ 音乐",
                    enabled = true,
                    supportsSearch = true,
                    supportsPlayback = false,
                    supportsPlaylists = true,
                    supportsPlaylistCreate = true,
                    supportsPlaylistWrite = true
                )
            )
        )
        val operations = FakeStreamingLocalPlaylistOperations()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        val listener = FakeStreamingPlaylistListener().apply {
            selectedPlaylistIdValue = 42L
            selectedPlaylistTracksValue = listOf(
                Track(1L, "Local song", "Artist", "Album", 180_000L, null, "")
            )
        }
        val controller = StreamingPlaylistController(
            viewModel,
            StreamingPlaylistController.LanguageProvider { AppLanguage.MODE_CHINESE },
            listener
        )

        controller.importSelectedPlaylistToStreaming()
        controller.runStreamingPlaylistImport(
            StreamingProviderName.QQ_MUSIC,
            "Local",
            listener.selectedPlaylistTracksValue
        )

        assertTrue(operations.awaitLink())
        assertEquals(
            listOf("42:qqmusic:qq-playlist:LOCAL_TO_REMOTE"),
            operations.linkedMirrors
        )
    }

    private class FakeStreamingPlaylistListener : StreamingPlaylistController.Listener {
        val events = mutableListOf<String>()
        var selectedPlaylistIdValue = -1L
        var selectedPlaylistTracksValue: List<Track> = emptyList()
        private val pickerLatch = CountDownLatch(1)
        private val refreshLatch = CountDownLatch(1)
        private val previewLatch = CountDownLatch(1)

        fun awaitPicker(): Boolean = pickerLatch.await(2, TimeUnit.SECONDS)

        fun awaitRefresh(): Boolean = refreshLatch.await(2, TimeUnit.SECONDS)

        fun awaitPreview(): Boolean = previewLatch.await(2, TimeUnit.SECONDS)

        override fun selectedPlaylistId(): Long = selectedPlaylistIdValue

        override fun setSelectedPlaylistId(playlistId: Long) {
            selectedPlaylistIdValue = playlistId
            events += "setPlaylist:$playlistId"
        }

        override fun loadCollections() {
            events += "loadCollections"
        }

        override fun refreshLibraryAfterStreamingImport() {
            events += "refreshLibrary"
            refreshLatch.countDown()
        }

        override fun selectedPlaylistName(): String = "Local"

        override fun selectedPlaylistTracks(): List<Track> = selectedPlaylistTracksValue

        override fun favoriteTracks(): List<Track> = emptyList()

        override fun selectedStreamingProvider(): StreamingProviderName? = StreamingProviderName.NETEASE

        override fun showStreamingProviderPicker(playlistName: String, tracks: List<Track>) {
            events += "providerPicker:$playlistName:${tracks.size}"
        }

        override fun navigateToStreaming() {
            events += "navigate"
        }

        override fun showStreamingPlaylistLoadedDialog(message: String) {
            events += "dialog:$message"
        }

        override fun showStreamingPlaylistImportPreview(
            provider: StreamingProviderName,
            providerPlaylistId: String,
            playlistName: String,
            tracks: List<StreamingTrack>
        ) {
            events += "preview:${provider.wireName}:$providerPlaylistId:$playlistName:${tracks.size}"
            previewLatch.countDown()
        }

        override fun showAccountPlaylistImportPicker(provider: StreamingProviderName, playlists: List<StreamingPlaylist>) {
            events += "picker:${provider.wireName}:${playlists.joinToString("|") { it.providerPlaylistId }}"
            pickerLatch.countDown()
        }

        override fun setStatus(status: String) {
            events += "status:$status"
        }

    }

    private class FakeStreamingLocalPlaylistOperations : StreamingLocalPlaylistOperations {
        val ensureProviders = mutableListOf<StreamingProviderName>()
        val importProviderPlaylistIds = mutableListOf<String>()
        val syncPlaylistIds = mutableListOf<Long>()
        val linkedMirrors = mutableListOf<String>()
        private val linkLatch = CountDownLatch(1)
        var linkedPlaylist: StreamingPlaylistSyncStore.LinkedPlaylist? = null

        override fun playlistExists(localPlaylistId: Long): Boolean =
            linkedPlaylist?.localPlaylistId == localPlaylistId

        override fun importStreamingPlaylist(
            playlistName: String,
            provider: StreamingProviderName,
            providerPlaylistId: String,
            streamingTracks: List<StreamingTrack>,
            linkWhenProviderPlaylistIdBlank: Boolean
        ): PlaylistImportResult {
            importProviderPlaylistIds += providerPlaylistId
            return PlaylistImportResult(1L, playlistName, streamingTracks.size, streamingTracks.size, 0, 0)
        }

        override fun syncStreamingPlaylist(
            link: StreamingPlaylistSyncStore.LinkedPlaylist,
            streamingTracks: List<StreamingTrack>
        ): StreamingLocalPlaylistSyncResult {
            syncPlaylistIds += link.localPlaylistId
            return StreamingLocalPlaylistSyncResult(link.localPlaylistId, streamingTracks.size, streamingTracks.isEmpty())
        }

        override fun ensureStreamingLoginPlaylist(
            playlistName: String,
            provider: StreamingProviderName
        ): StreamingLoginPlaylistResult {
            ensureProviders += provider
            return StreamingLoginPlaylistResult(9L, playlistName)
        }

        override fun linkedPlaylist(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? =
            linkedPlaylist?.takeIf { it.localPlaylistId == localPlaylistId }

        override fun linkedPlaylist(
            provider: StreamingProviderName,
            providerPlaylistId: String
        ): StreamingPlaylistSyncStore.LinkedPlaylist? =
            linkedPlaylist?.takeIf { it.provider == provider && it.providerPlaylistId == providerPlaylistId }

        override fun linkPlaylist(
            localPlaylistId: Long,
            provider: StreamingProviderName,
            providerPlaylistId: String,
            direction: StreamingPlaylistSyncDirection
        ) {
            linkedMirrors += "$localPlaylistId:${provider.wireName}:$providerPlaylistId:${direction.name}"
            linkLatch.countDown()
        }

        fun awaitLink(): Boolean = linkLatch.await(2, TimeUnit.SECONDS)
    }

    private class FakeGateway(
        private val userPlaylistsResult: List<StreamingPlaylist>,
        private val searchTracksResult: List<StreamingTrack> = emptyList(),
        private val providerCapabilitiesResult: List<StreamingProviderCapability> = emptyList()
    ) : StreamingGateway {
        val userPlaylistProviders = mutableListOf<StreamingProviderName>()
        private val provider = FakeProvider()
        var playlistDetail: StreamingPlaylistDetail? = null

        override suspend fun providers(): List<StreamingProviderDescriptor> = listOf(provider.descriptor)

        override suspend fun providerCapabilities() = providerCapabilitiesResult

        override suspend fun providersHealth() = emptyList<app.yukine.streaming.StreamingProviderHealth>()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
            StreamingSearchResult(
                provider = request.provider,
                query = request.query,
                page = request.page,
                pageSize = request.pageSize,
                tracks = searchTracksResult
            )

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail =
            playlistDetail ?: StreamingPlaylistDetail(
                provider = request.provider,
                providerPlaylistId = request.providerPlaylistId,
                tracks = emptyList()
            )

        override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> {
            userPlaylistProviders += provider
            return userPlaylistsResult
        }

        override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> = emptyList()

        override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> = emptyList()

        override suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack> = emptyList()

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
            provider.resolvePlayback(request)

        override suspend fun authState(provider: StreamingProviderName): StreamingAuthState = this.provider.authState()

        override suspend fun startAuth(request: app.yukine.streaming.StreamingAuthRequest): StreamingAuthResult =
            provider.startAuth(request)

        override suspend fun completeAuth(
            provider: StreamingProviderName,
            callbackUri: String,
            cookieHeader: String?
        ): StreamingAuthResult = this.provider.completeAuth(callbackUri)

        override suspend fun signOut(provider: StreamingProviderName): StreamingAuthState =
            this.provider.signOut()

        override suspend fun createUserPlaylist(
            provider: StreamingProviderName,
            title: String
        ): StreamingPlaylist = StreamingPlaylist(provider, "qq-playlist", title)

        override suspend fun mutateUserPlaylistTracks(
            provider: StreamingProviderName,
            providerPlaylistId: String,
            providerTrackIds: List<String>,
            add: Boolean
        ) = Unit
    }

    private class FakeProvider : StreamingProvider {
        override val descriptor: StreamingProviderDescriptor =
            StreamingProviderDescriptor(
                name = StreamingProviderName.NETEASE,
                displayName = "网易云音乐",
                enabled = true,
                capabilities = StreamingProviderCapabilities(
                    supportsSearch = true,
                    supportsPlayback = true,
                    supportsAuth = true,
                    supportsPlaylists = true
                ),
                auth = StreamingAuthState(connected = true)
            )

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
            StreamingSearchResult(
                provider = request.provider,
                query = request.query,
                page = request.page,
                pageSize = request.pageSize
            )

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
            StreamingPlaybackSource(
                provider = request.provider,
                providerTrackId = request.providerTrackId,
                url = "https://example.test/${request.providerTrackId}.mp3"
            )

        override suspend fun authState(): StreamingAuthState = StreamingAuthState(connected = true)
    }

    private fun streamingTrack(id: String): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            durationMs = 180000L
        )
}
