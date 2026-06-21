package app.yukine

import android.net.Uri
import app.yukine.model.PlaylistImportResult
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAuthKind
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingHeartbeatRequest
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingPlaylistDetail
import app.yukine.streaming.StreamingPlaylistRequest
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderCapabilities
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingProvider
import app.yukine.streaming.StreamingProviderRegistry
import app.yukine.streaming.StreamingProviderStatus
import app.yukine.streaming.RegistryStreamingGateway
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingSearchRequest
import app.yukine.streaming.StreamingSearchResult
import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingPlaybackRequest
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingAuthRequest
import app.yukine.streaming.StreamingAuthResult
import app.yukine.streaming.StreamingGateway
import app.yukine.streaming.cache.StreamingAuthMetadataEntity
import app.yukine.streaming.cache.StreamingCacheDao
import app.yukine.streaming.cache.StreamingCacheRepository
import app.yukine.streaming.cache.StreamingPlaybackCacheEntity
import app.yukine.streaming.cache.StreamingPlaylistCacheEntity
import app.yukine.streaming.cache.StreamingSearchCacheEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import kotlinx.coroutines.test.runTest

class StreamingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectProviderClearsSearchPlaybackAndLaunchState() {
        val viewModel = StreamingViewModel()
        viewModel.updateStreamingSearchResult(searchResult(StreamingProviderName.NETEASE, "echo", 1))
        viewModel.updateStreamingAuthLaunch(
            StreamingProviderName.NETEASE,
            StreamingAuthState(connected = false, kind = StreamingAuthKind.REMOTE_GATEWAY),
            "https://login"
        )

        viewModel.selectStreamingProvider(StreamingProviderName.QQ_MUSIC)

        val state = viewModel.streaming.value
        assertEquals(StreamingProviderName.QQ_MUSIC, state.selectedProvider)
        assertNull(state.searchResult)
        assertNull(state.resolvedPlaybackSource)
        assertNull(state.resolvedPlaybackTrack)
        assertNull(state.pendingAuthLaunch)
        assertFalse(state.loadingMore)
        assertNull(state.errorMessage)
    }

    @Test
    fun appendSearchResultMergesLaterPagesAndUnifiedItems() {
        val viewModel = StreamingViewModel()
        viewModel.updateStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 1, listOf(streamingTrack("1")))
        )

        viewModel.appendStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 2, listOf(streamingTrack("2")))
        )

        val result = viewModel.streaming.value.searchResult
        assertEquals(listOf("1", "2"), result?.tracks?.map { it.providerTrackId })
        assertEquals(2, result?.unifiedItems?.size)
        assertFalse(viewModel.streaming.value.loadingMore)
    }

    @Test
    fun providerSelectionPrefersConnectedNonMockProvider() {
        val viewModel = StreamingViewModel()
        viewModel.updateStreamingAuthState(
            StreamingProviderName.QQ_MUSIC,
            StreamingAuthState(connected = true)
        )

        viewModel.updateStreamingProviders(
            providers = listOf(
                descriptor(StreamingProviderName.MOCK, "Mock", supportsAuth = false),
                descriptor(StreamingProviderName.NETEASE, "NetEase", connected = false),
                descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music", connected = true)
            ),
            capabilities = listOf(capability(StreamingProviderName.QQ_MUSIC)),
            health = emptyList()
        )

        assertEquals(StreamingProviderName.QQ_MUSIC, viewModel.streaming.value.selectedProvider)
        assertTrue(viewModel.streaming.value.authStates[StreamingProviderName.QQ_MUSIC]?.connected == true)
    }

    @Test
    fun configureStreamingRepositoryUsesInjectedSource() = runTest {
        val source = FakeStreamingRepositorySource(
            listOf(
                repositoryWithProviderName(StreamingProviderName.NETEASE),
                repositoryWithProviderName(StreamingProviderName.QQ_MUSIC)
            )
        )
        val viewModel = StreamingViewModel(source)

        viewModel.refreshStreamingProviders().join()

        assertEquals(1, source.currentCalls)
        assertEquals(StreamingProviderName.NETEASE, viewModel.streaming.value.providers.single().name)

        viewModel.configureStreamingRepository().join()
        viewModel.refreshStreamingProviders().join()

        assertEquals(2, source.currentCalls)
        assertEquals(StreamingProviderName.QQ_MUSIC, viewModel.streaming.value.providers.single().name)
    }

    @Test
    fun configureStreamingRepositoryClearsExpiredCache() = runTest {
        val firstDao = FakeStreamingCacheDao()
        val secondDao = FakeStreamingCacheDao()
        val source = FakeStreamingRepositorySource(
            listOf(
                repositoryWithCache(firstDao),
                repositoryWithCache(secondDao)
            )
        )
        val viewModel = StreamingViewModel(source)

        viewModel.configureStreamingRepository().join()

        assertEquals(0, firstDao.deleteExpiredCalls)
        assertEquals(1, secondDao.deleteExpiredCalls)
    }

    @Test
    fun searchStreamingStoresRequestedMediaTypesAndSearchResult() = runTest {
        val provider = FakeProvider()
        provider.searchResult = searchResult(
            StreamingProviderName.NETEASE,
            "echo",
            1,
            listOf(streamingTrack("song-1"))
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(repository(provider))

        viewModel.searchStreaming(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            mediaTypes = setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM)
        ).join()

        val state = viewModel.streaming.value
        assertEquals(listOf("echo"), provider.searchRequests.map { it.query })
        assertEquals(setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM), provider.searchRequests.single().mediaTypes)
        assertEquals("echo", state.searchQuery)
        assertEquals(setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM), state.searchMediaTypes)
        assertEquals(listOf("song-1"), state.searchResult?.tracks?.map { it.providerTrackId })
        assertFalse(state.loading)
    }

    @Test
    fun searchNextStreamingPageAppendsLaterPage() = runTest {
        val provider = FakeProvider()
        provider.searchResult = searchResult(
            StreamingProviderName.NETEASE,
            "echo",
            2,
            listOf(streamingTrack("song-2"))
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(repository(provider))
        viewModel.updateStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 1, listOf(streamingTrack("song-1")))
        )

        viewModel.searchNextStreamingPage()
        waitUntil { provider.searchRequests.isNotEmpty() }

        assertEquals(listOf(2), provider.searchRequests.map { it.page })
        assertEquals(listOf("song-1", "song-2"), viewModel.streaming.value.searchResult?.tracks?.map { it.providerTrackId })
        assertFalse(viewModel.streaming.value.loadingMore)
    }

    @Test
    fun startStreamingAuthStoresLaunchAndInvokesLaunchCallback() = runTest {
        val provider = FakeProvider()
        provider.startAuthResult = StreamingAuthResult(
            StreamingProviderName.NETEASE,
            StreamingAuthState(kind = StreamingAuthKind.REMOTE_GATEWAY),
            launchUrl = "https://login"
        )
        var launchReady = false
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(repository(provider))

        viewModel.startStreamingAuth(
            StreamingProviderName.NETEASE,
            redirectUri = "echo-next://streaming-auth?provider=netease",
            onLaunchReady = { launchReady = true }
        )
        waitUntil { launchReady }

        assertEquals(listOf("echo-next://streaming-auth?provider=netease"), provider.startAuthRedirectUris)
        assertEquals("https://login", viewModel.streaming.value.pendingAuthLaunch?.launchUrl)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun completeStreamingAuthUpdatesAuthStateAndInvokesSuccessCallback() = runTest {
        val provider = FakeProvider()
        provider.completeAuthState = StreamingAuthState(connected = true)
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(repository(provider))
        val successProviders = mutableListOf<StreamingProviderName>()

        viewModel.completeStreamingAuth(
            StreamingProviderName.NETEASE,
            callbackUri = "echo-next://streaming-auth?provider=netease",
            cookieHeader = "cookie=1"
        ) { successProviders += it }
        waitUntil { successProviders.isNotEmpty() }

        assertEquals(listOf("echo-next://streaming-auth?provider=netease|null"), provider.completeAuthCalls)
        assertEquals(listOf(StreamingProviderName.NETEASE), successProviders)
        assertTrue(viewModel.streaming.value.authStates[StreamingProviderName.NETEASE]?.connected == true)
    }

    @Test
    fun signOutStreamingUpdatesAuthState() = runTest {
        val provider = FakeProvider()
        provider.signOutState = StreamingAuthState(connected = false)
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(repository(provider))

        viewModel.signOutStreaming(StreamingProviderName.NETEASE).join()
        waitUntil { !viewModel.streaming.value.loading }

        assertEquals(1, provider.signOutCalls)
        assertFalse(viewModel.streaming.value.authStates[StreamingProviderName.NETEASE]?.connected == true)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun resolveStreamingPlaybackTrackStoresSourceAndTrack() = runTest {
        val provider = FakeProvider()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(repository(provider))
        val metadata = streamingTrack("play-1")

        viewModel.resolveStreamingPlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "play-1",
            metadata = metadata
        ).join()

        val state = viewModel.streaming.value
        assertEquals(listOf("play-1"), provider.playbackRequests.map { it.providerTrackId })
        assertEquals("https://example.test/play-1.mp3", state.resolvedPlaybackSource?.url)
        assertEquals("Track play-1", state.resolvedPlaybackTrack?.title)
        assertFalse(state.loading)
    }

    @Test
    fun preResolveNextStreamingTrackSchedulesResolveAndReturnsReplacement() = runTest {
        val provider = FakeProvider()
        val viewModel = StreamingViewModel()
        val taskQueue = FakeStreamingPlaybackTaskQueue()
        val local = localTrack(id = 1L)
        val next = streamingPlaceholderTrack(id = 2L, providerTrackId = "next-2")
        val resolved = mutableListOf<Pair<Long, Track?>>()
        viewModel.bindStreamingRepository(repository(provider))
        viewModel.bindStreamingPlaybackCoordinator(ResolveStreamingPlaybackUseCase(), taskQueue)

        val scheduled = viewModel.preResolveNextStreamingTrack(
            snapshot = playbackSnapshot(currentTrack = local, currentIndex = 0, queueSize = 2, playing = true),
            queue = listOf(local, next),
            quality = StreamingAudioQuality.HIGH
        ) { oldTrackId, track -> resolved += oldTrackId to track }

        assertTrue(scheduled)
        assertEquals(1, taskQueue.nextUrlResolveCount)
        waitUntil { resolved.isNotEmpty() }
        assertEquals(
            listOf(StreamingPlaybackRequest(StreamingProviderName.NETEASE, "next-2", StreamingAudioQuality.HIGH)),
            provider.playbackRequests.toList()
        )
        assertEquals(listOf(2L), resolved.map { it.first })
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun resolveStreamingTrackListForPlaybackSchedulesCurrentResolveAndReplacesTrack() = runTest {
        val provider = FakeProvider()
        val viewModel = StreamingViewModel()
        val taskQueue = FakeStreamingPlaybackTaskQueue()
        val local = localTrack(id = 1L)
        val unresolved = streamingPlaceholderTrack(id = 2L, providerTrackId = "play-2")
        val resolved = mutableListOf<ResolvedStreamingTrackList?>()
        viewModel.bindStreamingRepository(repository(provider))
        viewModel.bindStreamingPlaybackCoordinator(ResolveStreamingPlaybackUseCase(), taskQueue)

        val scheduled = viewModel.resolveStreamingTrackListForPlayback(
            tracks = listOf(local, unresolved),
            index = 1,
            quality = StreamingAudioQuality.HIGH
        ) { result -> resolved += result }

        assertTrue(scheduled)
        assertEquals(1, taskQueue.currentUrlResolveCount)
        waitUntil { resolved.isNotEmpty() }
        assertEquals(
            listOf(StreamingPlaybackRequest(StreamingProviderName.NETEASE, "play-2", StreamingAudioQuality.HIGH)),
            provider.playbackRequests.toList()
        )
        assertEquals(1, resolved.size)
        assertEquals("Streaming 2", resolved.single()?.tracks?.get(1)?.title)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun prepareCurrentStreamingQueueResolveTargetSelectsCurrentPlaceholder() {
        val viewModel = StreamingViewModel()
        val local = localTrack(id = 1L)
        val current = streamingPlaceholderTrack(id = 2L, providerTrackId = "play-2")
        val next = streamingPlaceholderTrack(id = 3L, providerTrackId = "play-3")

        val target = viewModel.prepareCurrentStreamingQueueResolveTarget(
            snapshot = playbackSnapshot(
                currentTrack = current,
                currentIndex = 5,
                queueSize = 3,
                playing = true
            ),
            queue = listOf(local, current, next)
        )

        assertEquals(listOf(local, current, next), target?.tracks)
        assertEquals(2, target?.index)
        assertNull(
            viewModel.prepareCurrentStreamingQueueResolveTarget(
                snapshot = playbackSnapshot(
                    currentTrack = local,
                    currentIndex = 0,
                    queueSize = 1,
                    playing = true
                ),
                queue = listOf(local)
            )
        )
    }

    @Test
    fun loadUserPlaylistsStoresAccountPlaylists() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.userPlaylistsResult = listOf(
            StreamingPlaylist(
                provider = StreamingProviderName.NETEASE,
                providerPlaylistId = "playlist-1",
                title = "Remote Playlist"
            )
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))

        viewModel.loadUserPlaylists(StreamingProviderName.NETEASE).join()

        val state = viewModel.streaming.value
        assertEquals(listOf("playlist-1"), state.userPlaylists.map { it.providerPlaylistId })
        assertEquals(StreamingProviderName.NETEASE, state.selectedProvider)
        assertFalse(state.userPlaylistsLoading)
    }

    @Test
    fun fetchRecommendationsDeliverTracksAndClearLoading() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.userLikedTracksResult = listOf(streamingTrack("liked-1"))
        gateway.dailyRecommendationsResult = listOf(streamingTrack("daily-1"))
        gateway.heartbeatRecommendationsResult = listOf(streamingTrack("heart-1"))
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        val liked = mutableListOf<List<StreamingTrack>>()
        val daily = mutableListOf<List<StreamingTrack>>()
        val heartbeat = mutableListOf<List<StreamingTrack>>()

        viewModel.fetchUserLikedTracks(StreamingProviderName.NETEASE) { tracks -> liked += tracks }.join()
        viewModel.fetchDailyRecommendations(StreamingProviderName.NETEASE) { tracks -> daily += tracks }.join()
        viewModel.fetchHeartbeatRecommendations(
            StreamingProviderName.NETEASE,
            providerTrackId = "seed-1",
            providerPlaylistId = "playlist-1"
        ) { tracks -> heartbeat += tracks }.join()

        assertEquals(listOf("liked-1"), liked.single().map { it.providerTrackId })
        assertEquals(listOf("daily-1"), daily.single().map { it.providerTrackId })
        assertEquals(listOf("heart-1"), heartbeat.single().map { it.providerTrackId })
        assertEquals(1, gateway.heartbeatRequests.size)
        assertEquals(60, gateway.heartbeatRequests.single().count)
        assertEquals("seed-1", gateway.heartbeatRequests.single().providerTrackId)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun fetchStreamingPlaylistTracksLoadsAllPages() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.playlistTitle = "Remote Playlist"
        gateway.playlistTrackIds = listOf("track-1", "track-2", "track-3")
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        val resolvedNames = mutableListOf<String>()
        val resolvedTrackIds = mutableListOf<List<String>>()

        viewModel.fetchStreamingPlaylistTracks(StreamingProviderName.NETEASE, "playlist-1") { name, tracks ->
            resolvedNames += name
            resolvedTrackIds += tracks.map { it.providerTrackId }
        }.join()

        assertEquals(listOf(1, 2), gateway.playlistRequests.map { it.page })
        assertEquals(listOf(2000, 2000), gateway.playlistRequests.map { it.pageSize })
        assertEquals(listOf("Remote Playlist"), resolvedNames)
        assertEquals(listOf(listOf("track-1", "track-2", "track-3")), resolvedTrackIds)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun fetchStreamingPlaylistTracksUsesReadableFallbackNameWhenRemoteTitleIsMissing() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.playlistTitle = ""
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        val resolvedNames = mutableListOf<String>()

        viewModel.fetchStreamingPlaylistTracks(StreamingProviderName.NETEASE, "playlist-42") { name, _ ->
            resolvedNames += name
        }.join()

        assertEquals(listOf("Streaming playlist playlist-42"), resolvedNames)
    }

    @Test
    fun importStreamingPlaylistToLocalUsesBoundOperations() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.playlistTrackIds = listOf("track-1", "track-2")
        val operations = FakeStreamingLocalPlaylistOperations()
        val imported = mutableListOf<StreamingLocalPlaylistImportResult>()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.bindStreamingLocalPlaylistOperations(operations)

        viewModel.importStreamingPlaylistToLocal(
            StreamingProviderName.NETEASE,
            "playlist-1"
        ) { result -> imported += result }.join()

        assertEquals(listOf("Remote Playlist"), operations.importPlaylistNames)
        assertEquals(listOf("playlist-1"), operations.importProviderPlaylistIds)
        assertEquals(listOf(false), operations.importLinkBlankFlags)
        assertEquals(listOf(listOf("track-1", "track-2")), operations.importTrackIds)
        assertEquals(2, imported.single().playlistAddedCount)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun importStreamingPlaylistToLocalSkipsImporterForEmptyRemotePlaylist() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.playlistTrackIds = emptyList()
        val operations = FakeStreamingLocalPlaylistOperations()
        val imported = mutableListOf<StreamingLocalPlaylistImportResult>()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.bindStreamingLocalPlaylistOperations(operations)

        viewModel.importStreamingPlaylistToLocal(StreamingProviderName.NETEASE, "playlist-empty") { result ->
            imported += result
        }.join()

        assertEquals(emptyList<String>(), operations.importPlaylistNames)
        assertEquals("Remote Playlist", imported.single().playlistName)
        assertTrue(imported.single().empty)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun importStreamingLikedTracksToLocalLinksProviderWithBlankPlaylistId() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.userLikedTracksResult = listOf(streamingTrack("liked-1"), streamingTrack("liked-2"))
        val operations = FakeStreamingLocalPlaylistOperations()
        val imported = mutableListOf<StreamingLocalPlaylistImportResult>()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.bindStreamingLocalPlaylistOperations(operations)

        viewModel.importStreamingLikedTracksToLocal(StreamingProviderName.NETEASE, "Liked from NetEase") { result ->
            imported += result
        }.join()

        assertEquals(listOf("Liked from NetEase"), operations.importPlaylistNames)
        assertEquals(listOf(""), operations.importProviderPlaylistIds)
        assertEquals(listOf(true), operations.importLinkBlankFlags)
        assertEquals(listOf(listOf("liked-1", "liked-2")), operations.importTrackIds)
        assertEquals(2, imported.single().playlistAddedCount)
        assertFalse(imported.single().empty)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun syncStreamingPlaylistToLocalUsesLikedTracksForBlankRemotePlaylistId() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.userLikedTracksResult = listOf(streamingTrack("liked-1"), streamingTrack("liked-2"))
        val operations = FakeStreamingLocalPlaylistOperations()
        val synced = mutableListOf<StreamingLocalPlaylistSyncResult>()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.bindStreamingLocalPlaylistOperations(operations)
        val link = StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = 42L,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "",
            lastSyncMs = 0L
        )

        viewModel.syncStreamingPlaylistToLocal(link) { result -> synced += result }.join()

        assertEquals(listOf(42L), operations.syncPlaylistIds)
        assertEquals(listOf(listOf("liked-1", "liked-2")), operations.syncTrackIds)
        assertEquals(42L, synced.single().playlistId)
        assertEquals(2, synced.single().syncedCount)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun syncStreamingPlaylistToLocalSyncsSpecificRemotePlaylist() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.playlistTrackIds = listOf("track-1", "track-2", "track-3")
        val operations = FakeStreamingLocalPlaylistOperations()
        val synced = mutableListOf<StreamingLocalPlaylistSyncResult>()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.bindStreamingLocalPlaylistOperations(operations)
        val link = StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = 15L,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-15",
            lastSyncMs = 0L
        )

        viewModel.syncStreamingPlaylistToLocal(link) { result -> synced += result }.join()

        assertEquals(listOf(1, 2), gateway.playlistRequests.map { it.page })
        assertEquals(listOf(15L), operations.syncPlaylistIds)
        assertEquals(listOf("playlist-15"), operations.syncProviderPlaylistIds)
        assertEquals(listOf(listOf("track-1", "track-2", "track-3")), operations.syncTrackIds)
        assertEquals(3, synced.single().syncedCount)
        assertFalse(synced.single().empty)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun ensureStreamingLoginPlaylistDelegatesToBoundOperations() = runTest {
        val operations = FakeStreamingLocalPlaylistOperations()
        val ensured = mutableListOf<StreamingLoginPlaylistResult>()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingLocalPlaylistOperations(operations)

        viewModel.ensureStreamingLoginPlaylist(
            "My NetEase Playlist",
            StreamingProviderName.NETEASE
        ) { result -> ensured += result }.join()

        assertEquals(listOf("My NetEase Playlist"), operations.ensurePlaylistNames)
        assertEquals(listOf(StreamingProviderName.NETEASE), operations.ensureProviders)
        assertEquals(listOf(StreamingLoginPlaylistResult(9L, "My NetEase Playlist")), ensured)
        assertEquals(StreamingProviderName.NETEASE, viewModel.streaming.value.selectedProvider)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun prepareStreamingPlaylistPresentationRequestsUseLanguageAndBoundOperations() {
        val viewModel = StreamingViewModel()
        val link = StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = 42L,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-42",
            lastSyncMs = 0L
        )
        val operations = FakeStreamingLocalPlaylistOperations().apply {
            linkedPlaylistResult = link
        }
        viewModel.bindStreamingLocalPlaylistOperations(operations)
        viewModel.updateStreamingProviders(listOf(descriptor(StreamingProviderName.NETEASE, "NetEase")))

        val tracks = listOf(localTrack(1L), localTrack(2L))
        val export = viewModel.prepareStreamingPlaylistExportRequest("Road Trip", tracks, AppLanguage.MODE_ENGLISH)
        val favorites = viewModel.prepareStreamingFavoritesExportRequest(tracks, AppLanguage.MODE_ENGLISH)
        val importStart = viewModel.prepareStreamingPlaylistImportStartRequest(
            "playlist-42",
            StreamingProviderName.NETEASE,
            AppLanguage.MODE_ENGLISH
        )
        val loginRequest = viewModel.prepareStreamingLoginPlaylistRequest(
            StreamingProviderName.NETEASE,
            AppLanguage.MODE_ENGLISH
        )
        val syncStart = viewModel.prepareStreamingPlaylistSyncStartRequest(42L, AppLanguage.MODE_ENGLISH)
        val importPresentation = viewModel.prepareStreamingPlaylistImportPresentation(
            StreamingLocalPlaylistImportResult("Remote", 3, empty = false),
            AppLanguage.MODE_ENGLISH
        )
        val syncPresentation = viewModel.prepareStreamingPlaylistSyncPresentation(
            StreamingLocalPlaylistSyncResult(42L, 2, empty = false),
            AppLanguage.MODE_ENGLISH
        )
        val loginPresentation = viewModel.prepareStreamingLoginPlaylistPresentation(
            loginRequest,
            StreamingLoginPlaylistResult(42L, loginRequest.playlistName),
            AppLanguage.MODE_ENGLISH
        )

        assertTrue(export.valid)
        assertEquals("Road Trip", export.playlistName)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "favorites"), favorites.playlistName)
        assertTrue(importStart.valid)
        assertEquals("playlist-42", importStart.providerPlaylistId)
        assertTrue(loginRequest.playlistName.contains("NetEase"))
        assertEquals(link, syncStart?.link)
        assertTrue(importPresentation.showLoadedDialog)
        assertTrue(importPresentation.status.contains("Remote"))
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.sync.complete") + " (2)",
            syncPresentation.status
        )
        assertEquals(42L, loginPresentation.playlistId)
    }

    @Test
    fun streamingProviderTrackIdLoadsAndSavesThroughBoundStore() = runTest {
        val viewModel = StreamingViewModel()
        val track = localTrack(id = 42L)
        val store = FakeStreamingTrackMatchStore()
        store.loadedProviderTrackId = " cached-42 "
        val loaded = mutableListOf<String>()
        viewModel.bindStreamingTrackMatchStore(store)

        viewModel.loadStreamingProviderTrackId(track, StreamingProviderName.NETEASE) { providerTrackId ->
            loaded += providerTrackId
        }.join()
        val directLoaded = viewModel.streamingProviderTrackIdFor(track, StreamingProviderName.NETEASE)
        viewModel.saveStreamingProviderTrackId(track, StreamingProviderName.NETEASE, " resolved-42 ").join()
        viewModel.saveStreamingProviderTrackId(track, StreamingProviderName.NETEASE, " ").join()

        assertEquals(listOf(" cached-42 "), loaded)
        assertEquals(" cached-42 ", directLoaded)
        assertEquals(listOf("load:42", "load:42", "save:42:resolved-42"), store.events)
    }

    @Test
    fun resolveHeartbeatRecommendationSeedSearchesAndSavesFirstMatch() = runTest {
        val provider = FakeProvider()
        provider.searchResult = searchResult(
            StreamingProviderName.NETEASE,
            "Local 88 Artist",
            1,
            listOf(
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "song-88",
                    title = "Local 88",
                    artist = "Artist"
                )
            )
        )
        val store = FakeStreamingTrackMatchStore()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(repository(provider))
        viewModel.bindStreamingTrackMatchStore(store)
        val resolvedTrackIds = mutableListOf<String>()

        viewModel.resolveHeartbeatRecommendationSeed(
            StreamingProviderName.NETEASE,
            listOf(localTrack(id = 88L))
        ) { providerTrackId -> resolvedTrackIds += providerTrackId }.join()

        assertEquals(listOf("song-88"), resolvedTrackIds)
        assertEquals(listOf("Local 88 Artist"), provider.searchRequests.map { it.query })
        assertEquals(listOf("direct:88", "load:88", "save:88:song-88"), store.events)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun prepareHeartbeatRecommendationSeedRequestCollectsCandidatesAndDirectSeed() {
        val viewModel = StreamingViewModel()
        val serviceTrack = localTrack(id = 10L)
        val queuedTrack = localTrack(id = 11L)
        val playlistTrack = localTrack(id = 12L)
        val store = FakeStreamingTrackMatchStore().apply {
            heartbeatCandidates = listOf(serviceTrack, queuedTrack)
            heartbeatQueueSnapshot = listOf(serviceTrack, queuedTrack)
            providerTrackIdFromCandidateResult = " seed-11 "
            heartbeatSeedMissLogMessage = "Heartbeat seed missing provider=netease"
        }
        viewModel.bindStreamingTrackMatchStore(store)

        val request = viewModel.prepareHeartbeatRecommendationSeedRequest(
            StreamingProviderName.NETEASE,
            playbackSnapshot(serviceTrack, 0, 2, true),
            listOf(serviceTrack),
            playbackSnapshot(queuedTrack, 1, 2, true),
            listOf(queuedTrack),
            listOf(playlistTrack)
        )

        assertEquals(setOf(serviceTrack.id, queuedTrack.id), request.candidates.map { it.id }.toSet())
        assertEquals(2, request.candidates.size)
        assertEquals("seed-11", request.seedTrackId)
        assertEquals("seed-11", request.playlistId)
        assertEquals("Heartbeat seed missing provider=netease", request.seedMissingMessage)
        assertEquals(listOf(12L, 10L), store.lastHeartbeatCandidateServiceQueue?.mapNotNull { it?.id })
        assertEquals(listOf(12L, 10L), store.lastHeartbeatQueueServiceQueue?.mapNotNull { it?.id })
        assertTrue(request.hasSeed)
        assertTrue(request.hasCandidates)
    }

    @Test
    fun prepareHeartbeatRecommendationSeedRequestVariesFirstCandidateAcrossClicks() {
        val viewModel = StreamingViewModel()
        val candidates = (1L..8L).map { id -> localTrack(id = id) }
        val store = FakeStreamingTrackMatchStore().apply {
            heartbeatCandidates = candidates
            heartbeatQueueSnapshot = candidates
            providerTrackIdFromCandidateResult = "seed"
        }
        viewModel.bindStreamingTrackMatchStore(store)

        val firstCandidateIds = (1..24).mapNotNull {
            viewModel.prepareHeartbeatRecommendationSeedRequest(
                StreamingProviderName.NETEASE,
                null,
                emptyList(),
                null,
                emptyList()
            ).candidates.firstOrNull()?.id
        }.toSet()

        assertTrue(firstCandidateIds.size > 1)
    }

    @Test
    fun prepareStreamingHeartbeatRecommendationRequestUsesNetEaseAndStartsLoading() {
        val viewModel = StreamingViewModel()
        viewModel.updateStreamingProviders(
            listOf(
                descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music"),
                descriptor(StreamingProviderName.NETEASE, "NetEase")
            )
        )

        val request = viewModel.prepareStreamingHeartbeatRecommendationRequest(
            StreamingProviderName.QQ_MUSIC,
            AppLanguage.MODE_ENGLISH
        )

        assertEquals(StreamingProviderName.NETEASE, request?.provider)
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.recommend.heartbeat.loading"),
            request?.loadingStatus
        )
        assertTrue(viewModel.canContinueHeartbeatRecommendationLoading(StreamingProviderName.NETEASE))
        assertFalse(viewModel.canContinueHeartbeatRecommendationLoading(StreamingProviderName.QQ_MUSIC))
    }

    @Test
    fun prepareHeartbeatRecommendationPresentationOwnsDedupAndAppendStatus() {
        val viewModel = StreamingViewModel()
        val playingStatus = AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.recommend.heartbeat.playing")
        val emptyStatus = AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.recommend.heartbeat.empty")

        val presentation = viewModel.prepareHeartbeatRecommendationPresentation(
            listOf(
                streamingTrack("heart-1"),
                streamingTrack("heart-1"),
                streamingTrack("heart-2"),
                streamingTrack("heart-3").copy(playable = false),
                streamingTrack("   "),
                streamingTrack("heart-4").copy(title = "", artist = "")
            ),
            emptyStatus,
            playingStatus
        )
        val append = viewModel.prepareHeartbeatRecommendationAppendPresentation(
            listOf(streamingTrack("heart-2"), streamingTrack("heart-3")),
            AppLanguage.MODE_ENGLISH
        )
        val empty = viewModel.prepareHeartbeatRecommendationAppendPresentation(emptyList(), AppLanguage.MODE_ENGLISH)

        assertEquals(2, presentation.tracks.size)
        assertEquals("$playingStatus (2)", presentation.readyStatus)
        assertEquals(1, append.tracks.size)
        assertEquals("$playingStatus (+1)", append.readyStatus)
        assertTrue(empty.empty)
        assertEquals(emptyStatus, empty.emptyStatus)
    }

    private fun searchResult(
        provider: StreamingProviderName,
        query: String,
        page: Int,
        tracks: List<StreamingTrack> = emptyList()
    ): StreamingSearchResult =
        StreamingSearchResult(
            provider = provider,
            query = query,
            page = page,
            pageSize = 20,
            hasMore = page == 1,
            tracks = tracks
        )

    private fun streamingTrack(id: String): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = "Track $id",
            artist = "Artist",
            album = "Album",
            durationMs = 1_000L
        )

    private fun localTrack(id: Long): Track =
        Track(id, "Local $id", "Artist", "Album", 1000L, Uri.EMPTY, "local:$id")

    private fun streamingPlaceholderTrack(id: Long, providerTrackId: String): Track =
        StreamingPlaybackAdapter.placeholderTrack(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = providerTrackId,
                title = "Streaming $id",
                artist = "Artist"
            )
        ).let { placeholder ->
            Track(
                id,
                placeholder.title,
                placeholder.artist,
                placeholder.album,
                placeholder.durationMs,
                placeholder.contentUri,
                placeholder.dataPath
            )
        }

    private fun playbackSnapshot(
        currentTrack: Track?,
        currentIndex: Int,
        queueSize: Int,
        playing: Boolean,
        positionMs: Long = 1_000L
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            currentTrack,
            currentIndex,
            queueSize,
            positionMs,
            120_000L,
            playing,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )

    private fun descriptor(
        name: StreamingProviderName,
        displayName: String,
        supportsAuth: Boolean = true,
        connected: Boolean = false
    ): StreamingProviderDescriptor =
        StreamingProviderDescriptor(
            name = name,
            displayName = displayName,
            enabled = true,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = true,
                supportsPlayback = true,
                supportsAuth = supportsAuth
            ),
            auth = StreamingAuthState(
                kind = if (supportsAuth) StreamingAuthKind.REMOTE_GATEWAY else StreamingAuthKind.NONE,
                connected = connected
            ),
            status = if (supportsAuth) StreamingProviderStatus.NEEDS_ACCOUNT else StreamingProviderStatus.READY
        )

    private fun capability(provider: StreamingProviderName): StreamingProviderCapability =
        StreamingProviderCapability(
            provider = provider,
            displayName = provider.wireName,
            enabled = true,
            supportsSearch = true,
            supportsPlayback = true,
            supportsAuth = true,
            supportedSearchMediaTypes = setOf(StreamingMediaType.TRACK)
        )

    private class FakeProvider : StreamingProvider {
        val searchRequests = mutableListOf<StreamingSearchRequest>()
        val playbackRequests = mutableListOf<StreamingPlaybackRequest>()
        val startAuthRedirectUris = mutableListOf<String?>()
        val completeAuthCalls = mutableListOf<String>()
        var signOutCalls = 0
        var searchResult: StreamingSearchResult = StreamingSearchResult(
            provider = StreamingProviderName.NETEASE,
            query = "",
            page = 1,
            pageSize = 20
        )
        var startAuthResult: StreamingAuthResult = StreamingAuthResult(
            StreamingProviderName.NETEASE,
            StreamingAuthState()
        )
        var completeAuthState: StreamingAuthState = StreamingAuthState()
        var signOutState: StreamingAuthState = StreamingAuthState()
        var currentAuthState: StreamingAuthState = StreamingAuthState()

        override val descriptor: StreamingProviderDescriptor =
            StreamingProviderDescriptor(
                name = StreamingProviderName.NETEASE,
                displayName = "NetEase",
                enabled = true,
                capabilities = StreamingProviderCapabilities(
                    supportsSearch = true,
                    supportsPlayback = true,
                    supportsAuth = true
                )
            )

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
            searchRequests += request
            return searchResult
        }

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
            playbackRequests += request
            return StreamingPlaybackSource(
                provider = request.provider,
                providerTrackId = request.providerTrackId,
                url = "https://example.test/${request.providerTrackId}.mp3"
            )
        }

        override suspend fun authState(): StreamingAuthState = currentAuthState

        override suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult {
            startAuthRedirectUris += request.redirectUri
            currentAuthState = startAuthResult.state
            return startAuthResult
        }

        override suspend fun completeAuth(callbackUri: String): StreamingAuthResult {
            completeAuthCalls += "$callbackUri|null"
            currentAuthState = completeAuthState
            return StreamingAuthResult(StreamingProviderName.NETEASE, completeAuthState)
        }

        override suspend fun signOut(): StreamingAuthState {
            signOutCalls += 1
            currentAuthState = signOutState
            return signOutState
        }
    }

    private fun repository(provider: StreamingProvider): StreamingRepository =
        StreamingRepository(RegistryStreamingGateway(StreamingProviderRegistry(listOf(provider))))

    private fun repositoryWithProviderName(name: StreamingProviderName): StreamingRepository =
        StreamingRepository(
            RegistryStreamingGateway(
                StreamingProviderRegistry(
                    listOf(
                        object : StreamingProvider {
                            override val descriptor: StreamingProviderDescriptor =
                                descriptor(name, name.wireName)

                            override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
                                searchResult(name, request.query, request.page)

                            override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
                                StreamingPlaybackSource(
                                    provider = request.provider,
                                    providerTrackId = request.providerTrackId,
                                    url = "https://example.test/${request.providerTrackId}.mp3"
                                )
                        }
                    )
                )
            )
        )

    private fun repositoryWithCache(dao: FakeStreamingCacheDao): StreamingRepository {
        return StreamingRepository(
            FakeGateway(FakeProvider()),
            StreamingCacheRepository(dao) { 1_000L }
        )
    }

    private class FakeStreamingRepositorySource(
        private val repositories: List<StreamingRepository>
    ) : StreamingRepositorySource {
        var currentCalls: Int = 0
            private set

        override fun current(): StreamingRepository {
            val index = currentCalls.coerceAtMost(repositories.lastIndex)
            currentCalls += 1
            return repositories[index]
        }
    }

    private class FakeGateway(private val provider: FakeProvider) : StreamingGateway {
        val playlistRequests = mutableListOf<StreamingPlaylistRequest>()
        val heartbeatRequests = mutableListOf<StreamingHeartbeatRequest>()
        var userPlaylistsResult: List<StreamingPlaylist> = emptyList()
        var userLikedTracksResult: List<StreamingTrack> = emptyList()
        var dailyRecommendationsResult: List<StreamingTrack> = emptyList()
        var heartbeatRecommendationsResult: List<StreamingTrack> = emptyList()
        var playlistTitle: String? = "Remote Playlist"
        var playlistTrackIds: List<String> = listOf("track-1", "track-2", "track-3")

        override suspend fun providers(): List<StreamingProviderDescriptor> = listOf(provider.descriptor)

        override suspend fun providerCapabilities(): List<StreamingProviderCapability> = emptyList()

        override suspend fun providersHealth(): List<app.yukine.streaming.StreamingProviderHealth> = emptyList()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
            provider.search(request)

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
            playlistRequests += request
            val tracks = if (request.page == 1) playlistTrackIds.take(2) else playlistTrackIds.drop(2)
            return StreamingPlaylistDetail(
                provider = provider.descriptor.name,
                providerPlaylistId = request.providerPlaylistId,
                playlist = StreamingPlaylist(
                    provider = provider.descriptor.name,
                    providerPlaylistId = request.providerPlaylistId,
                    title = playlistTitle.orEmpty(),
                    trackCount = playlistTrackIds.size
                ),
                tracks = tracks.map { trackId ->
                    StreamingTrack(
                        provider = provider.descriptor.name,
                        providerTrackId = trackId,
                        title = "Track $trackId",
                        artist = "Artist",
                        album = "Album",
                        durationMs = 1_000L
                    )
                },
                total = playlistTrackIds.size,
                page = request.page,
                pageSize = request.pageSize,
                hasMore = request.page == 1 && playlistTrackIds.size > 2
            )
        }

        override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> =
            userPlaylistsResult

        override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> =
            userLikedTracksResult

        override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> =
            dailyRecommendationsResult

        override suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack> {
            heartbeatRequests += request
            return heartbeatRecommendationsResult
        }

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
            provider.resolvePlayback(request)

        override suspend fun authState(provider: StreamingProviderName): StreamingAuthState =
            this.provider.authState()

        override suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult =
            provider.startAuth(request)

        override suspend fun completeAuth(
            provider: StreamingProviderName,
            callbackUri: String,
            cookieHeader: String?
        ): StreamingAuthResult = this.provider.completeAuth(callbackUri)

        override suspend fun signOut(provider: StreamingProviderName): StreamingAuthState =
            this.provider.signOut()
    }

    private class FakeStreamingPlaybackTaskQueue : StreamingPlaybackTaskQueue {
        var currentPlaybackRecoveryCount = 0
        var currentUrlResolveCount = 0
        var nextUrlResolveCount = 0

        override fun scheduleCurrentPlaybackRecovery(task: StreamingPlaybackTask) {
            currentPlaybackRecoveryCount += 1
            task.run(Runnable {})
        }

        override fun scheduleCurrentUrlResolve(task: StreamingPlaybackTask) {
            currentUrlResolveCount += 1
            task.run(Runnable {})
        }

        override fun scheduleNextUrlResolve(task: StreamingPlaybackTask) {
            nextUrlResolveCount += 1
            task.run(Runnable {})
        }
    }

    private class FakeStreamingLocalPlaylistOperations : StreamingLocalPlaylistOperations {
        val importPlaylistNames = mutableListOf<String>()
        val importProviderPlaylistIds = mutableListOf<String>()
        val importLinkBlankFlags = mutableListOf<Boolean>()
        val importTrackIds = mutableListOf<List<String>>()
        val syncPlaylistIds = mutableListOf<Long>()
        val syncProviderPlaylistIds = mutableListOf<String>()
        val syncTrackIds = mutableListOf<List<String>>()
        val ensurePlaylistNames = mutableListOf<String>()
        val ensureProviders = mutableListOf<StreamingProviderName>()
        var linkedPlaylistResult: StreamingPlaylistSyncStore.LinkedPlaylist? = null

        override fun importStreamingPlaylist(
            playlistName: String,
            provider: StreamingProviderName,
            providerPlaylistId: String,
            streamingTracks: List<StreamingTrack>,
            linkWhenProviderPlaylistIdBlank: Boolean
        ): PlaylistImportResult {
            importPlaylistNames += playlistName
            importProviderPlaylistIds += providerPlaylistId
            importLinkBlankFlags += linkWhenProviderPlaylistIdBlank
            importTrackIds += streamingTracks.map { it.providerTrackId }
            return PlaylistImportResult(
                7L,
                playlistName,
                streamingTracks.size,
                streamingTracks.size,
                streamingTracks.size,
                0
            )
        }

        override fun syncStreamingPlaylist(
            link: StreamingPlaylistSyncStore.LinkedPlaylist,
            streamingTracks: List<StreamingTrack>
        ): StreamingLocalPlaylistSyncResult {
            syncPlaylistIds += link.localPlaylistId
            syncProviderPlaylistIds += link.providerPlaylistId
            syncTrackIds += streamingTracks.map { it.providerTrackId }
            return StreamingLocalPlaylistSyncResult(
                playlistId = link.localPlaylistId,
                syncedCount = streamingTracks.size,
                empty = streamingTracks.isEmpty()
            )
        }

        override fun ensureStreamingLoginPlaylist(
            playlistName: String,
            provider: StreamingProviderName
        ): StreamingLoginPlaylistResult {
            ensurePlaylistNames += playlistName
            ensureProviders += provider
            return StreamingLoginPlaylistResult(playlistId = 9L, playlistName = playlistName)
        }

        override fun linkedPlaylist(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? =
            linkedPlaylistResult
    }

    private class FakeStreamingTrackMatchStore : StreamingTrackMatchStore {
        var loadedProviderTrackId: String = ""
        var providerTrackIdFromCandidateResult: String = ""
        var heartbeatCandidates: List<Track> = emptyList()
        var heartbeatQueueSnapshot: List<Track> = emptyList()
        var heartbeatSeedMissLogMessage: String = ""
        val events = mutableListOf<String>()
        var lastHeartbeatCandidateServiceQueue: List<Track?>? = null
        var lastHeartbeatQueueServiceQueue: List<Track?>? = null

        override fun directProviderTrackId(track: Track, provider: StreamingProviderName): String {
            events += "direct:${track.id}"
            return ""
        }

        override fun providerTrackIdFor(track: Track, provider: StreamingProviderName): String {
            events += "load:${track.id}"
            return loadedProviderTrackId
        }

        override fun saveProviderTrackId(
            track: Track,
            provider: StreamingProviderName,
            providerTrackId: String
        ) {
            events += "save:${track.id}:$providerTrackId"
        }

        override fun providerTrackIdFromCandidates(
            candidates: List<Track?>?,
            provider: StreamingProviderName?
        ): String = providerTrackIdFromCandidateResult

        override fun heartbeatSeedCandidates(
            serviceSnapshot: PlaybackStateSnapshot?,
            serviceQueue: List<Track?>?,
            storeSnapshot: PlaybackStateSnapshot?,
            viewModelQueue: List<Track?>?
        ): List<Track> {
            lastHeartbeatCandidateServiceQueue = serviceQueue
            return heartbeatCandidates
        }

        override fun snapshotQueueForHeartbeat(
            serviceQueue: List<Track?>?,
            viewModelQueue: List<Track?>?,
            storeSnapshot: PlaybackStateSnapshot?
        ): List<Track> {
            lastHeartbeatQueueServiceQueue = serviceQueue
            return heartbeatQueueSnapshot
        }

        override fun heartbeatSeedMissMessage(
            provider: StreamingProviderName?,
            snapshot: PlaybackStateSnapshot?,
            storeSnapshot: PlaybackStateSnapshot?,
            queue: List<Track?>?
        ): String = heartbeatSeedMissLogMessage
    }

    private class FakeStreamingCacheDao : StreamingCacheDao {
        var deleteExpiredCalls = 0
            private set

        override suspend fun search(provider: String, cacheKey: String, nowMs: Long): StreamingSearchCacheEntity? = null

        override suspend fun upsertSearch(entity: StreamingSearchCacheEntity) = Unit

        override suspend fun playlist(
            provider: String,
            providerPlaylistId: String,
            nowMs: Long
        ): StreamingPlaylistCacheEntity? = null

        override suspend fun upsertPlaylist(entity: StreamingPlaylistCacheEntity) = Unit

        override suspend fun playback(
            provider: String,
            providerTrackId: String,
            quality: String,
            nowMs: Long
        ): StreamingPlaybackCacheEntity? = null

        override fun playbackBlocking(
            provider: String,
            providerTrackId: String,
            nowMs: Long
        ): StreamingPlaybackCacheEntity? = null

        override suspend fun upsertPlayback(entity: StreamingPlaybackCacheEntity) = Unit

        override suspend fun auth(provider: String, nowMs: Long): StreamingAuthMetadataEntity? = null

        override suspend fun upsertAuth(entity: StreamingAuthMetadataEntity) = Unit

        override suspend fun deleteExpiredSearch(nowMs: Long): Int {
            deleteExpiredCalls += 1
            return 0
        }

        override suspend fun deleteExpiredPlaylists(nowMs: Long): Int = 0

        override suspend fun deleteExpiredPlayback(nowMs: Long): Int = 0

        override suspend fun deleteExpiredAuth(nowMs: Long): Int = 0
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 1_000L
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10L)
        }
    }
}
