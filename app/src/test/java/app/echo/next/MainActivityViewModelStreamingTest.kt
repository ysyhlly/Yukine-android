package app.echo.next

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.echo.next.model.PlaylistImportResult
import app.echo.next.model.Track
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.streaming.RegistryStreamingGateway
import app.echo.next.streaming.StreamingAudioQuality
import app.echo.next.streaming.StreamingAuthState
import app.echo.next.streaming.StreamingGateway
import app.echo.next.streaming.StreamingPlaybackRequest
import app.echo.next.streaming.StreamingPlaybackSource
import app.echo.next.streaming.StreamingProvider
import app.echo.next.streaming.StreamingProviderCapabilities
import app.echo.next.streaming.StreamingProviderDescriptor
import app.echo.next.streaming.StreamingMediaType
import app.echo.next.streaming.StreamingPlaylist
import app.echo.next.streaming.StreamingPlaylistDetail
import app.echo.next.streaming.StreamingPlaylistRequest
import app.echo.next.streaming.StreamingPlaybackAdapter
import app.echo.next.streaming.StreamingPlaylistSyncStore
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingProviderRegistry
import app.echo.next.streaming.StreamingRepository
import app.echo.next.streaming.StreamingSearchRequest
import app.echo.next.streaming.StreamingSearchResult
import app.echo.next.streaming.StreamingTrack
import app.echo.next.streaming.StreamingPlaybackTrackAdapter
import app.echo.next.streaming.cache.StreamingCacheRepository
import app.echo.next.streaming.cache.StreamingCacheDao
import app.echo.next.streaming.cache.StreamingSearchCacheEntity
import app.echo.next.streaming.cache.StreamingPlaylistCacheEntity
import app.echo.next.streaming.cache.StreamingPlaybackCacheEntity
import app.echo.next.streaming.cache.StreamingAuthMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.rules.TestRule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModelStreamingTest {
    @org.junit.Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectStreamingProviderClearsPreviousSearchAndPlaybackState() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.updateStreamingSearchResult(searchResult(StreamingProviderName.NETEASE, "echo", 1))
        viewModel.updateStreamingPlaybackSource(
            app.echo.next.streaming.StreamingPlaybackSource(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "track-1",
                url = "https://example.test/audio.m3u8"
            )
        )

        viewModel.selectStreamingProvider(StreamingProviderName.QQ_MUSIC)

        val state = viewModel.streaming.value
        assertEquals(StreamingProviderName.QQ_MUSIC, state.selectedProvider)
        assertNull(state.searchResult)
        assertNull(state.resolvedPlaybackSource)
        assertNull(state.resolvedPlaybackTrack)
        assertNull(state.pendingAuthLaunch)
        assertNull(state.errorMessage)
        assertFalse(state.loadingMore)
    }

    @Test
    fun appendStreamingSearchResultMergesLaterPages() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.updateStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 1, "track-1")
        )

        viewModel.appendStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 2, "track-2")
        )

        val result = viewModel.streaming.value.searchResult
        assertEquals(2, result?.page)
        assertEquals(listOf("track-1", "track-2"), result?.tracks?.map { it.providerTrackId })
        assertEquals(listOf("track-1", "track-2"), result?.unifiedItems?.map { it.id })
        assertFalse(viewModel.streaming.value.loadingMore)
    }

    @Test
    fun appendStreamingSearchResultReplacesMismatchedProviderOrQuery() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.updateStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 1, "track-1")
        )

        viewModel.appendStreamingSearchResult(
            searchResult(StreamingProviderName.QQ_MUSIC, "other", 2, "track-2")
        )

        val result = viewModel.streaming.value.searchResult
        assertEquals(StreamingProviderName.QQ_MUSIC, result?.provider)
        assertEquals("other", result?.query)
        assertEquals(listOf("track-2"), result?.tracks?.map { it.providerTrackId })
    }

    @Test
    fun searchStreamingStoresRequestedMediaTypesBeforeAsyncResult() = runTest {
        val viewModel = MainActivityViewModel(SavedStateHandle())

        val searchJob = viewModel.searchStreaming(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            mediaTypes = setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM)
        )

        val state = viewModel.streaming.value
        assertEquals("echo", state.searchQuery)
        assertEquals(setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM), state.searchMediaTypes)
        assertTrue(state.loading)
        searchJob.cancel()
        searchJob.join()
    }

    @Test
    fun configureStreamingRepositoryUsesInjectedSource() = runTest {
        val source = FakeStreamingRepositorySource(
            listOf(
                repositoryWithProvider(StreamingProviderName.NETEASE),
                repositoryWithProvider(StreamingProviderName.QQ_MUSIC)
            )
        )
        val viewModel = MainActivityViewModel(SavedStateHandle(), source)

        viewModel.refreshStreamingProviders().join()

        assertEquals(1, source.currentCalls)
        assertEquals(StreamingProviderName.NETEASE, viewModel.streaming.value.providers.single().name)

        viewModel.configureStreamingRepository()
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
        val viewModel = MainActivityViewModel(SavedStateHandle(), source)

        viewModel.configureStreamingRepository().join()

        assertEquals(0, firstDao.deleteExpiredCalls)
        assertEquals(1, secondDao.deleteExpiredCalls)
    }

    @Test
    fun fetchStreamingPlaylistTracksLoadsAllPages() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        val viewModel = MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(
                listOf(
                    StreamingRepository(
                        RegistryStreamingGateway(
                            StreamingProviderRegistry(listOf(provider))
                        )
                    )
                )
            )
        )
        val resolvedNames = mutableListOf<String>()
        val resolvedTrackIds = mutableListOf<List<String>>()

        viewModel.fetchStreamingPlaylistTracks(StreamingProviderName.NETEASE, "playlist-1") { name, tracks ->
            resolvedNames += name
            resolvedTrackIds += tracks.map { it.providerTrackId }
        }.join()

        assertEquals(listOf(1, 2), provider.playlistRequests.map { it.page })
        assertEquals(listOf(2000, 2000), provider.playlistRequests.map { it.pageSize })
        assertEquals(listOf("Remote Playlist"), resolvedNames)
        assertEquals(listOf(listOf("track-1", "track-2", "track-3")), resolvedTrackIds)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun fetchStreamingPlaylistTracksUsesReadableFallbackNameWhenRemoteTitleIsMissing() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        provider.playlistTitle = ""
        val viewModel = MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(
                listOf(
                    StreamingRepository(
                        RegistryStreamingGateway(
                            StreamingProviderRegistry(listOf(provider))
                        )
                    )
                )
            )
        )
        val resolvedNames = mutableListOf<String>()

        viewModel.fetchStreamingPlaylistTracks(StreamingProviderName.NETEASE, "playlist-42") { name, _ ->
            resolvedNames += name
        }.join()

        assertEquals(listOf("Streaming playlist playlist-42"), resolvedNames)
    }

    @Test
    fun importStreamingPlaylistToLocalImportsFetchedTracks() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        val viewModel = MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(
                listOf(
                    StreamingRepository(
                        RegistryStreamingGateway(
                            StreamingProviderRegistry(listOf(provider))
                        ),
                        playbackTrackAdapter = FakePlaybackTrackAdapter()
                    )
                )
            )
        )
        val importCalls = mutableListOf<StreamingLocalImportCall>()
        val imported = mutableListOf<StreamingLocalPlaylistImportResult>()
        viewModel.bindStreamingLocalPlaylistOperations(FakeStreamingLocalPlaylistOperations(importer = { playlistName, importProvider, providerPlaylistId, tracks, linkBlank ->
            importCalls += StreamingLocalImportCall(
                playlistName = playlistName,
                provider = importProvider,
                providerPlaylistId = providerPlaylistId,
                trackIds = tracks.map { it.providerTrackId },
                linkWhenProviderPlaylistIdBlank = linkBlank
            )
            PlaylistImportResult(7L, playlistName, tracks.size, tracks.size, tracks.size, 0)
        }))

        viewModel.importStreamingPlaylistToLocal(StreamingProviderName.NETEASE, "playlist-1") { result ->
            imported += result
        }.join()

        assertEquals(listOf(1, 2), provider.playlistRequests.map { it.page })
        assertEquals(1, importCalls.size)
        assertEquals("Remote Playlist", importCalls.single().playlistName)
        assertEquals(StreamingProviderName.NETEASE, importCalls.single().provider)
        assertEquals("playlist-1", importCalls.single().providerPlaylistId)
        assertEquals(listOf("track-1", "track-2", "track-3"), importCalls.single().trackIds)
        assertFalse(importCalls.single().linkWhenProviderPlaylistIdBlank)
        assertEquals("Remote Playlist", imported.single().playlistName)
        assertEquals(3, imported.single().playlistAddedCount)
        assertFalse(imported.single().empty)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun importStreamingPlaylistToLocalSkipsImporterForEmptyRemotePlaylist() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        provider.playlistTrackIds = emptyList()
        val viewModel = MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(
                listOf(
                    StreamingRepository(
                        RegistryStreamingGateway(
                            StreamingProviderRegistry(listOf(provider))
                        ),
                        playbackTrackAdapter = FakePlaybackTrackAdapter()
                    )
                )
            )
        )
        var importCalls = 0
        val imported = mutableListOf<StreamingLocalPlaylistImportResult>()
        viewModel.bindStreamingLocalPlaylistOperations(FakeStreamingLocalPlaylistOperations(importer = { playlistName, _, _, tracks, _ ->
            importCalls += 1
            PlaylistImportResult(7L, playlistName, tracks.size, tracks.size, tracks.size, 0)
        }))

        viewModel.importStreamingPlaylistToLocal(StreamingProviderName.NETEASE, "playlist-empty") { result ->
            imported += result
        }.join()

        assertEquals(0, importCalls)
        assertEquals("Remote Playlist", imported.single().playlistName)
        assertTrue(imported.single().empty)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun importStreamingLikedTracksToLocalLinksProviderWithBlankPlaylistId() = runTest {
        val likedTracks = listOf(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "liked-1",
                title = "Liked One",
                artist = "artist"
            ),
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "liked-2",
                title = "Liked Two",
                artist = "artist"
            )
        )
        val viewModel = MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(
                listOf(
                    StreamingRepository(
                        FakeLikedTracksGateway(StreamingProviderName.NETEASE, likedTracks)
                    )
                )
            )
        )
        val importCalls = mutableListOf<StreamingLocalImportCall>()
        val imported = mutableListOf<StreamingLocalPlaylistImportResult>()
        viewModel.bindStreamingLocalPlaylistOperations(FakeStreamingLocalPlaylistOperations(importer = { playlistName, importProvider, providerPlaylistId, tracks, linkBlank ->
            importCalls += StreamingLocalImportCall(
                playlistName = playlistName,
                provider = importProvider,
                providerPlaylistId = providerPlaylistId,
                trackIds = tracks.map { it.providerTrackId },
                linkWhenProviderPlaylistIdBlank = linkBlank
            )
            PlaylistImportResult(9L, playlistName, tracks.size, tracks.size, tracks.size, 0)
        }))

        viewModel.importStreamingLikedTracksToLocal(StreamingProviderName.NETEASE, "Liked from NetEase") { result ->
            imported += result
        }.join()

        assertEquals(1, importCalls.size)
        assertEquals("Liked from NetEase", importCalls.single().playlistName)
        assertEquals("", importCalls.single().providerPlaylistId)
        assertEquals(listOf("liked-1", "liked-2"), importCalls.single().trackIds)
        assertTrue(importCalls.single().linkWhenProviderPlaylistIdBlank)
        assertEquals(2, imported.single().playlistAddedCount)
        assertFalse(imported.single().empty)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun prepareStreamingPlaylistSyncTargetResolvesLinkedPlaylist() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val linked = link(15L)
        val resolvedIds = mutableListOf<Long>()
        viewModel.bindStreamingLocalPlaylistOperations(FakeStreamingLocalPlaylistOperations(linkResolver = { playlistId ->
            resolvedIds += playlistId
            if (playlistId == linked.localPlaylistId) linked else null
        }))

        val missing = viewModel.prepareStreamingPlaylistSyncTarget(12L)
        val target = viewModel.prepareStreamingPlaylistSyncTarget(15L)

        assertEquals(listOf(12L, 15L), resolvedIds)
        assertTrue(missing?.missingLink == true)
        assertEquals(linked, target?.link)
        assertFalse(target?.missingLink == true)
        assertNull(viewModel.prepareStreamingPlaylistSyncTarget(-1L))
    }

    @Test
    fun syncStreamingPlaylistToLocalSyncsSpecificRemotePlaylist() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        val viewModel = MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(
                listOf(
                    StreamingRepository(
                        RegistryStreamingGateway(
                            StreamingProviderRegistry(listOf(provider))
                        )
                    )
                )
            )
        )
        val syncCalls = mutableListOf<StreamingLocalSyncCall>()
        val synced = mutableListOf<StreamingLocalPlaylistSyncResult>()
        viewModel.bindStreamingLocalPlaylistOperations(FakeStreamingLocalPlaylistOperations(syncer = { link, tracks ->
            syncCalls += StreamingLocalSyncCall(
                playlistId = link.localPlaylistId,
                providerPlaylistId = link.providerPlaylistId,
                trackIds = tracks.map { it.providerTrackId }
            )
            StreamingLocalPlaylistSyncResult(link.localPlaylistId, tracks.size, false)
        }))

        viewModel.syncStreamingPlaylistToLocal(link(15L)) { result ->
            synced += result
        }.join()

        assertEquals(listOf(1, 2), provider.playlistRequests.map { it.page })
        assertEquals(1, syncCalls.size)
        assertEquals(15L, syncCalls.single().playlistId)
        assertEquals("playlist-15", syncCalls.single().providerPlaylistId)
        assertEquals(listOf("track-1", "track-2", "track-3"), syncCalls.single().trackIds)
        assertEquals(3, synced.single().syncedCount)
        assertFalse(synced.single().empty)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun syncStreamingPlaylistToLocalUsesLikedTracksWhenLinkHasNoRemotePlaylistId() = runTest {
        val likedTracks = listOf(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "liked-1",
                title = "Liked One",
                artist = "artist"
            ),
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "liked-2",
                title = "Liked Two",
                artist = "artist"
            )
        )
        val viewModel = MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(
                listOf(
                    StreamingRepository(
                        FakeLikedTracksGateway(StreamingProviderName.NETEASE, likedTracks)
                    )
                )
            )
        )
        val syncCalls = mutableListOf<StreamingLocalSyncCall>()
        val synced = mutableListOf<StreamingLocalPlaylistSyncResult>()
        viewModel.bindStreamingLocalPlaylistOperations(FakeStreamingLocalPlaylistOperations(syncer = { link, tracks ->
            syncCalls += StreamingLocalSyncCall(
                playlistId = link.localPlaylistId,
                providerPlaylistId = link.providerPlaylistId,
                trackIds = tracks.map { it.providerTrackId }
            )
            StreamingLocalPlaylistSyncResult(link.localPlaylistId, tracks.size, false)
        }))

        viewModel.syncStreamingPlaylistToLocal(
            StreamingPlaylistSyncStore.LinkedPlaylist(
                localPlaylistId = 19L,
                provider = StreamingProviderName.NETEASE,
                providerPlaylistId = "",
                lastSyncMs = 0L
            )
        ) { result ->
            synced += result
        }.join()

        assertEquals(1, syncCalls.size)
        assertEquals(19L, syncCalls.single().playlistId)
        assertEquals("", syncCalls.single().providerPlaylistId)
        assertEquals(listOf("liked-1", "liked-2"), syncCalls.single().trackIds)
        assertEquals(2, synced.single().syncedCount)
        assertFalse(synced.single().empty)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun ensureStreamingLoginPlaylistDelegatesToBoundEnsurer() = runTest {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val ensureCalls = mutableListOf<StreamingLoginPlaylistCall>()
        val ensured = mutableListOf<StreamingLoginPlaylistResult>()
        viewModel.bindStreamingLocalPlaylistOperations(FakeStreamingLocalPlaylistOperations(ensurer = { playlistName, provider ->
            ensureCalls += StreamingLoginPlaylistCall(playlistName, provider)
            StreamingLoginPlaylistResult(23L, playlistName)
        }))

        viewModel.ensureStreamingLoginPlaylist(
            playlistName = "My NetEase Playlist",
            provider = StreamingProviderName.NETEASE
        ) { result ->
            ensured += result
        }.join()

        assertEquals(
            listOf(StreamingLoginPlaylistCall("My NetEase Playlist", StreamingProviderName.NETEASE)),
            ensureCalls
        )
        assertEquals(listOf(StreamingLoginPlaylistResult(23L, "My NetEase Playlist")), ensured)
        assertEquals(StreamingProviderName.NETEASE, viewModel.streaming.value.selectedProvider)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun prepareStreamingLoginPlaylistRequestUsesProviderDisplayName() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })
        viewModel.updateStreamingProviders(
            listOf(
                providerDescriptor(
                    provider = StreamingProviderName.NETEASE,
                    displayName = "NetEase Cloud",
                    supportsSearch = true
                )
            )
        )

        val request = viewModel.prepareStreamingLoginPlaylistRequest(StreamingProviderName.NETEASE)

        assertEquals(StreamingProviderName.NETEASE, request.provider)
        assertEquals("My NetEase Cloud Playlist", request.playlistName)
    }

    @Test
    fun prepareStreamingLoginPlaylistRequestFallsBackToProviderWireName() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })

        val request = viewModel.prepareStreamingLoginPlaylistRequest(StreamingProviderName.SPOTIFY)

        assertEquals(StreamingProviderName.SPOTIFY, request.provider)
        assertEquals("My spotify Playlist", request.playlistName)
    }

    @Test
    fun prepareStreamingLikedPlaylistNameUsesProviderDisplayName() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })
        viewModel.updateStreamingProviders(
            listOf(
                providerDescriptor(
                    provider = StreamingProviderName.NETEASE,
                    displayName = "NetEase Cloud",
                    supportsSearch = true
                )
            )
        )

        assertEquals(
            "NetEase Cloud Favorites",
            viewModel.prepareStreamingLikedPlaylistName(StreamingProviderName.NETEASE)
        )
    }

    @Test
    fun prepareStreamingPlaybackStatusTextUsesGatewayLanguageAndQualityLabel() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val languageMode = AppLanguage.MODE_ENGLISH
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = languageMode
        })

        val statusText = viewModel.prepareStreamingPlaybackStatusText(StreamingAudioQuality.HIGH)
        val qualityLabel = SettingsPageRenderController.streamingQualityLabel(
            StreamingQualityPreference.valueFor(StreamingAudioQuality.HIGH),
            languageMode
        )

        assertEquals(AppLanguage.text(languageMode, "streaming.resolving"), statusText.resolving)
        assertEquals(AppLanguage.text(languageMode, "streaming.resolve.failed"), statusText.resolveFailed)
        assertEquals(
            AppLanguage.text(languageMode, "streaming.quality.downgrading") + qualityLabel,
            statusText.qualityDowngrading
        )
        assertEquals(
            AppLanguage.text(languageMode, "streaming.quality.downgraded") + qualityLabel,
            statusText.qualityDowngraded
        )
    }

    @Test
    fun prepareStreamingStatusTextBuildsSettingsAndDialogLabels() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val languageMode = AppLanguage.MODE_ENGLISH
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = languageMode
        })

        val statusText = viewModel.prepareStreamingStatusText(StreamingQualityPreference.HIGH)
        val qualityLabel = SettingsPageRenderController.streamingQualityLabel(
            StreamingQualityPreference.HIGH,
            languageMode
        )

        assertEquals(
            AppLanguage.text(languageMode, "streaming.quality.applied") + qualityLabel,
            statusText.streamingQualityApplied
        )
        assertEquals(
            AppLanguage.text(languageMode, "streaming.playlist.load.success.title"),
            viewModel.streamingPlaylistLoadedDialogTitle()
        )
    }

    @Test
    fun streamingProviderTrackIdLoadsAndSavesThroughBoundStore() = runTest {
        val viewModel = MainActivityViewModel(SavedStateHandle())
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
        assertEquals(
            listOf(
                StreamingTrackMatchLoadCall(42L, StreamingProviderName.NETEASE),
                StreamingTrackMatchLoadCall(42L, StreamingProviderName.NETEASE),
                StreamingTrackMatchSaveCall(42L, StreamingProviderName.NETEASE, "resolved-42")
            ),
            store.events
        )
    }

    @Test
    fun resolveStreamingTrackMatchSearchesLocalSongAndReturnsProviderSongId() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        provider.searchTracks = listOf(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "song-1",
                title = "Other",
                artist = "Artist"
            ),
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "song-2",
                title = "Echo",
                artist = "Singer"
            )
        )
        val viewModel = MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(
                listOf(
                    StreamingRepository(
                        RegistryStreamingGateway(
                            StreamingProviderRegistry(listOf(provider))
                        )
                    )
                )
            )
        )
        val resolvedTrackIds = mutableListOf<String?>()

        viewModel.resolveStreamingTrackMatch(
            StreamingProviderName.NETEASE,
            Track(1L, "Echo", "Singer", "Album", 120_000L, Uri.EMPTY, "/music/echo.flac")
        ) { track ->
            resolvedTrackIds += track?.providerTrackId
        }.join()

        assertEquals(listOf("Echo Singer"), provider.searchRequests.map { it.query })
        assertEquals(setOf(StreamingMediaType.TRACK), provider.searchRequests.single().mediaTypes)
        assertEquals(listOf("song-2"), resolvedTrackIds)
        assertNull(viewModel.streaming.value.searchResult)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun resolveHeartbeatRecommendationSeedUsesDirectCandidateAndSavesMatch() = runTest {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val first = localTrack(id = 1L)
        val second = localTrack(id = 2L)
        val store = FakeStreamingTrackMatchStore()
        store.directProviderTrackIds[2L] = " direct-2 "
        val resolvedTrackIds = mutableListOf<String>()
        viewModel.bindStreamingTrackMatchStore(store)

        viewModel.resolveHeartbeatRecommendationSeed(
            StreamingProviderName.NETEASE,
            listOf(first, second)
        ) { providerTrackId ->
            resolvedTrackIds += providerTrackId
        }.join()

        assertEquals(listOf("direct-2"), resolvedTrackIds)
        assertEquals(
            listOf(
                StreamingTrackMatchDirectCall(1L, StreamingProviderName.NETEASE),
                StreamingTrackMatchLoadCall(1L, StreamingProviderName.NETEASE),
                StreamingTrackMatchDirectCall(2L, StreamingProviderName.NETEASE),
                StreamingTrackMatchSaveCall(2L, StreamingProviderName.NETEASE, "direct-2")
            ),
            store.events
        )
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun resolveHeartbeatRecommendationSeedUsesCachedCandidateBeforeSearch() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        val viewModel = viewModelWithProvider(provider)
        val store = FakeStreamingTrackMatchStore()
        store.loadedProviderTrackId = " cached-42 "
        val resolvedTrackIds = mutableListOf<String>()
        viewModel.bindStreamingTrackMatchStore(store)

        viewModel.resolveHeartbeatRecommendationSeed(
            StreamingProviderName.NETEASE,
            listOf(localTrack(id = 42L))
        ) { providerTrackId ->
            resolvedTrackIds += providerTrackId
        }.join()

        assertEquals(listOf("cached-42"), resolvedTrackIds)
        assertEquals(emptyList<StreamingSearchRequest>(), provider.searchRequests)
        assertEquals(
            listOf(
                StreamingTrackMatchDirectCall(42L, StreamingProviderName.NETEASE),
                StreamingTrackMatchLoadCall(42L, StreamingProviderName.NETEASE)
            ),
            store.events
        )
    }

    @Test
    fun resolveHeartbeatRecommendationSeedSearchesAndSavesFirstMatch() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        provider.searchTracks = listOf(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "song-88",
                title = "Local 88",
                artist = "Artist"
            )
        )
        val viewModel = viewModelWithProvider(provider)
        val store = FakeStreamingTrackMatchStore()
        val resolvedTrackIds = mutableListOf<String>()
        viewModel.bindStreamingTrackMatchStore(store)

        viewModel.resolveHeartbeatRecommendationSeed(
            StreamingProviderName.NETEASE,
            listOf(localTrack(id = 88L))
        ) { providerTrackId ->
            resolvedTrackIds += providerTrackId
        }.join()

        assertEquals(listOf("song-88"), resolvedTrackIds)
        assertEquals(listOf("Local 88 Artist"), provider.searchRequests.map { it.query })
        assertEquals(
            listOf(
                StreamingTrackMatchDirectCall(88L, StreamingProviderName.NETEASE),
                StreamingTrackMatchLoadCall(88L, StreamingProviderName.NETEASE),
                StreamingTrackMatchSaveCall(88L, StreamingProviderName.NETEASE, "song-88")
            ),
            store.events
        )
    }

    @Test
    fun prepareHeartbeatRecommendationSeedRequestCollectsCandidatesAndDirectSeed() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val serviceTrack = localTrack(id = 10L)
        val queuedTrack = localTrack(id = 11L)
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
            listOf(queuedTrack)
        )

        assertEquals(listOf(serviceTrack, queuedTrack), request.candidates)
        assertEquals("seed-11", request.seedTrackId)
        assertEquals("seed-11", request.playlistId)
        assertEquals("Heartbeat seed missing provider=netease", request.seedMissingMessage)
        assertTrue(request.hasSeed)
        assertTrue(request.hasCandidates)
    }

    @Test
    fun prepareHeartbeatRecommendationSeedRequestFallsBackToStoreSnapshotForDiagnostics() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val storeTrack = localTrack(id = 12L)
        val store = FakeStreamingTrackMatchStore().apply {
            heartbeatQueueSnapshot = listOf(storeTrack)
            heartbeatSeedMissLogMessage = "Heartbeat seed missing provider=netease"
        }
        viewModel.bindStreamingTrackMatchStore(store)
        val storeSnapshot = playbackSnapshot(storeTrack, 0, 1, true)

        val request = viewModel.prepareHeartbeatRecommendationSeedRequest(
            StreamingProviderName.NETEASE,
            null,
            null,
            storeSnapshot,
            null
        )

        assertFalse(request.hasSeed)
        assertFalse(request.hasCandidates)
        assertEquals(storeSnapshot, store.lastHeartbeatSeedMissSnapshot)
        assertEquals(storeSnapshot, store.lastHeartbeatSeedMissStoreSnapshot)
        assertEquals(listOf(storeTrack), store.lastHeartbeatSeedMissQueue)
    }

    @Test
    fun prepareStreamingDailyRecommendationRequestPrefersNetEaseAndStopsHeartbeatMode() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.updateStreamingProviders(
            listOf(
                providerDescriptor(StreamingProviderName.NETEASE, "NetEase", true),
                providerDescriptor(StreamingProviderName.SPOTIFY, "Spotify", true)
            ),
            emptyList(),
            emptyList()
        )
        viewModel.startHeartbeatRecommendationLoading(StreamingProviderName.NETEASE)

        val request = viewModel.prepareStreamingDailyRecommendationRequest(StreamingProviderName.SPOTIFY)

        assertEquals(StreamingProviderName.NETEASE, request?.provider)
        assertEquals(AppLanguage.text(AppLanguage.MODE_SYSTEM, "streaming.recommend.daily.loading"), request?.loadingStatus)
        assertEquals(AppLanguage.text(AppLanguage.MODE_SYSTEM, "streaming.recommend.daily.empty"), request?.emptyStatus)
        assertEquals(AppLanguage.text(AppLanguage.MODE_SYSTEM, "streaming.recommend.daily"), request?.title)
        assertFalse(viewModel.canContinueHeartbeatRecommendationLoading(StreamingProviderName.NETEASE))
    }

    @Test
    fun prepareStreamingHeartbeatRecommendationRequestUsesNetEaseAndStartsLoading() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.updateStreamingProviders(
            listOf(providerDescriptor(StreamingProviderName.NETEASE, "NetEase", true)),
            emptyList(),
            emptyList()
        )

        val request = viewModel.prepareStreamingHeartbeatRecommendationRequest(StreamingProviderName.QQ_MUSIC)

        assertEquals(StreamingProviderName.NETEASE, request?.provider)
        assertEquals(AppLanguage.text(AppLanguage.MODE_SYSTEM, "streaming.recommend.heartbeat.loading"), request?.loadingStatus)
        assertEquals(AppLanguage.text(AppLanguage.MODE_SYSTEM, "streaming.recommend.heartbeat.empty"), request?.emptyStatus)
        assertEquals(AppLanguage.text(AppLanguage.MODE_SYSTEM, "streaming.recommend.heartbeat.playing"), request?.playingStatus)
        assertTrue(viewModel.canContinueHeartbeatRecommendationLoading(StreamingProviderName.NETEASE))
    }

    @Test
    fun streamingRecommendationFallbackStatusesUseGatewayLanguage() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val languageMode = AppLanguage.MODE_ENGLISH
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = languageMode
        })

        assertEquals(
            AppLanguage.text(languageMode, "streaming.recommend.daily.empty"),
            viewModel.streamingDailyRecommendationEmptyStatus()
        )
        assertEquals(
            AppLanguage.text(languageMode, "streaming.recommend.heartbeat.empty"),
            viewModel.streamingHeartbeatRecommendationEmptyStatus()
        )
    }

    @Test
    fun prepareStreamingRecommendationPresentationBuildsReadyStatus() {
        val viewModel = MainActivityViewModel(SavedStateHandle())

        val presentation = viewModel.prepareStreamingRecommendationPresentation(
            tracks = listOf(
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "daily-1",
                    title = "Daily 1",
                    artist = "Artist"
                ),
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "daily-2",
                    title = "Daily 2",
                    artist = "Artist"
                )
            ),
            emptyStatus = "empty",
            title = "Daily"
        )

        assertFalse(presentation.empty)
        assertEquals("Daily", presentation.title)
        assertEquals("Daily (2)", presentation.readyStatus)
        assertEquals(listOf("streaming:netease:daily-1", "streaming:netease:daily-2"), presentation.tracks.map { it.dataPath })
    }

    @Test
    fun prepareHeartbeatRecommendationPresentationBuildsReadyStatus() {
        val viewModel = MainActivityViewModel(SavedStateHandle())

        val presentation = viewModel.prepareHeartbeatRecommendationPresentation(
            tracks = listOf(
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "heart-1",
                    title = "Heart 1",
                    artist = "Artist"
                )
            ),
            emptyStatus = "empty",
            playingStatus = "Playing"
        )

        assertFalse(presentation.empty)
        assertEquals("Playing", presentation.title)
        assertEquals("Playing (1)", presentation.readyStatus)
        assertEquals(listOf("streaming:netease:heart-1"), presentation.tracks.map { it.dataPath })
    }

    @Test
    fun prepareHeartbeatRecommendationAppendPresentationBuildsAppendStatus() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val languageMode = AppLanguage.MODE_ENGLISH
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = languageMode
        })

        val presentation = viewModel.prepareHeartbeatRecommendationAppendPresentation(
            listOf(
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "heart-2",
                    title = "Heart 2",
                    artist = "Artist"
                )
            )
        )
        val empty = viewModel.prepareHeartbeatRecommendationAppendPresentation(emptyList())
        val playingStatus = AppLanguage.text(languageMode, "streaming.recommend.heartbeat.playing")

        assertFalse(presentation.empty)
        assertEquals(playingStatus, presentation.title)
        assertEquals("$playingStatus (+1)", presentation.readyStatus)
        assertEquals(listOf("streaming:netease:heart-2"), presentation.tracks.map { it.dataPath })
        assertTrue(empty.empty)
        assertEquals(
            AppLanguage.text(languageMode, "streaming.recommend.heartbeat.empty"),
            empty.emptyStatus
        )
    }

    @Test
    fun prepareStreamingHeartbeatRecommendationRequestReturnsNullWithoutNetEaseProvider() {
        val viewModel = MainActivityViewModel(SavedStateHandle())

        assertNull(viewModel.prepareStreamingHeartbeatRecommendationRequest(StreamingProviderName.SPOTIFY))
        assertNull(viewModel.prepareStreamingDailyRecommendationRequest(StreamingProviderName.SPOTIFY))
    }

    @Test
    fun prepareStreamingPlaylistImportPresentationBuildsStatus() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })

        val success = viewModel.prepareStreamingPlaylistImportPresentation(
            StreamingLocalPlaylistImportResult(
                playlistName = "Remote Mix",
                playlistAddedCount = 12,
                empty = false
            )
        )
        val empty = viewModel.prepareStreamingPlaylistImportPresentation(
            StreamingLocalPlaylistImportResult(empty = true)
        )

        assertFalse(success.empty)
        assertEquals("Imported playlist: Remote Mix (12)", success.status)
        assertTrue(success.showLoadedDialog)
        assertTrue(empty.empty)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.playlist.empty"), empty.status)
    }

    @Test
    fun prepareStreamingLikedImportPresentationBuildsStatus() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })

        val success = viewModel.prepareStreamingLikedImportPresentation(
            StreamingLocalPlaylistImportResult(
                playlistName = "Liked Songs",
                playlistAddedCount = 8,
                empty = false
            )
        )
        val empty = viewModel.prepareStreamingLikedImportPresentation(
            StreamingLocalPlaylistImportResult(empty = true)
        )

        assertFalse(success.empty)
        assertEquals("Imported favorites: Liked Songs (8)", success.status)
        assertTrue(success.showLoadedDialog)
        assertTrue(empty.empty)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.liked.empty"), empty.status)
    }

    @Test
    fun prepareStreamingPlaylistSyncPresentationBuildsStatus() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })

        val success = viewModel.prepareStreamingPlaylistSyncPresentation(
            StreamingLocalPlaylistSyncResult(playlistId = 7L, syncedCount = 5, empty = false)
        )
        val empty = viewModel.prepareStreamingPlaylistSyncPresentation(
            StreamingLocalPlaylistSyncResult(empty = true)
        )

        assertFalse(success.empty)
        assertEquals("Streaming playlist synced (5)", success.status)
        assertTrue(empty.empty)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.playlist.empty"), empty.status)
    }

    @Test
    fun prepareStreamingLoginPlaylistPresentationBuildsStatusAndPlaylistId() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })
        val request = StreamingLoginPlaylistRequest(
            provider = StreamingProviderName.NETEASE,
            playlistName = "My NetEase Cloud Playlist"
        )

        val success = viewModel.prepareStreamingLoginPlaylistPresentation(
            request,
            StreamingLoginPlaylistResult(playlistId = 42L, playlistName = request.playlistName)
        )
        val fallback = viewModel.prepareStreamingLoginPlaylistPresentation(request, null)

        assertEquals("Created streaming playlist: My NetEase Cloud Playlist", success.status)
        assertEquals(42L, success.playlistId)
        assertEquals("Created streaming playlist: My NetEase Cloud Playlist", fallback.status)
        assertEquals(-1L, fallback.playlistId)
    }

    @Test
    fun prepareStreamingPlaylistExportRequestValidatesTracksAndBuildsStatus() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })
        val tracks = listOf(localTrack(1L), localTrack(2L))

        val valid = viewModel.prepareStreamingPlaylistExportRequest("Road Trip", tracks)
        val empty = viewModel.prepareStreamingPlaylistExportRequest("Road Trip", emptyList())
        val missingName = viewModel.prepareStreamingPlaylistExportRequest("", tracks)

        assertTrue(valid.valid)
        assertEquals("Road Trip", valid.playlistName)
        assertEquals(tracks, valid.tracks)
        assertEquals("Matched ...", valid.status)
        assertFalse(empty.valid)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.no.tracks.to.import"), empty.status)
        assertFalse(missingName.valid)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.no.tracks.to.import"), missingName.status)
    }

    @Test
    fun prepareStreamingFavoritesExportRequestBuildsFavoritesRequest() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })
        val tracks = listOf(localTrack(7L))

        val valid = viewModel.prepareStreamingFavoritesExportRequest(tracks)
        val empty = viewModel.prepareStreamingFavoritesExportRequest(emptyList())

        assertTrue(valid.valid)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "favorites"), valid.playlistName)
        assertEquals(tracks, valid.tracks)
        assertEquals("Matched ...", valid.status)
        assertFalse(empty.valid)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.no.tracks.to.import"), empty.status)
    }

    @Test
    fun prepareStreamingImportProviderPickerRequestBuildsTitleAndEmptyStatus() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })
        val searchable = providerDescriptor(
            provider = StreamingProviderName.NETEASE,
            displayName = "NetEase",
            supportsSearch = true
        )

        val valid = viewModel.prepareStreamingImportProviderPickerRequest(listOf(searchable), true)
        val empty = viewModel.prepareStreamingImportProviderPickerRequest(emptyList(), true)

        assertTrue(valid.valid)
        assertEquals("Choose streaming provider", valid.title)
        assertEquals(listOf(StreamingProviderName.NETEASE), valid.pickerState.providers.map { it.name })
        assertFalse(empty.valid)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.no.providers"), empty.emptyStatus)
    }

    @Test
    fun prepareStreamingPlaylistExportPresentationBuildsMatchedStatus() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })

        val full = viewModel.prepareStreamingPlaylistExportPresentation(
            StreamingPlaylistImportStatus(matchedCount = 8, totalRequested = 10, unresolvedCount = 2)
        )
        val exact = viewModel.prepareStreamingPlaylistExportPresentation(
            StreamingPlaylistImportStatus(matchedCount = 4, totalRequested = 4, unresolvedCount = 0)
        )

        assertEquals("Matched 8 / 10 (2 unresolved)", full.status)
        assertEquals("Matched 4 / 4", exact.status)
    }

    @Test
    fun prepareStreamingPlaylistSyncStartRequestBuildsMissingAndStartedStates() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })
        viewModel.bindStreamingLocalPlaylistOperations(
            FakeStreamingLocalPlaylistOperations(linkResolver = { localPlaylistId ->
                if (localPlaylistId == 9L) link(localPlaylistId) else null
            })
        )

        val valid = viewModel.prepareStreamingPlaylistSyncStartRequest(9L)
        val missing = viewModel.prepareStreamingPlaylistSyncStartRequest(5L)
        val invalid = viewModel.prepareStreamingPlaylistSyncStartRequest(-1L)

        assertTrue(valid?.valid == true)
        assertEquals(link(9L), valid?.link)
        assertEquals("Syncing streaming playlists", valid?.status)
        assertFalse(missing?.valid == true)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.not.linked"), missing?.status)
        assertNull(invalid)
    }

    @Test
    fun streamingSearchActionUsesSelectedProviderCapabilities() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        provider.searchTracks = listOf(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "song-1",
                title = "Song",
                artist = "Artist"
            )
        )
        val viewModel = viewModelWithProvider(provider)

        viewModel.refreshStreamingProviders().join()
        viewModel.selectProvider(StreamingProviderName.NETEASE)
        viewModel.search("echo")
        waitUntil { provider.searchRequests.isNotEmpty() }

        assertEquals(listOf("echo"), provider.searchRequests.map { it.query })
        assertEquals(setOf(StreamingMediaType.TRACK), provider.searchRequests.single().mediaTypes)
        assertEquals(listOf("song-1"), viewModel.streaming.value.searchResult?.tracks?.map { it.providerTrackId })
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun streamingPlaybackActionResolvesPlayableTrackWithGatewayQuality() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        val viewModel = viewModelWithProvider(provider)
        val gateway = FakeStreamingActionGateway()
        gateway.quality = StreamingAudioQuality.HIRES
        viewModel.bindStreamingActionGateway(gateway)
        val streamingTrack = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "play-1",
            title = "Playable",
            artist = "Artist"
        )

        viewModel.refreshStreamingProviders().join()
        viewModel.playStreamingTrack(streamingTrack)
        waitUntil { provider.playbackRequests.isNotEmpty() }
        viewModel.playResolvedTrack(localTrack(99L))

        assertEquals(listOf(StreamingPlaybackRequest(StreamingProviderName.NETEASE, "play-1", StreamingAudioQuality.HIRES)), provider.playbackRequests)
        assertEquals(listOf(99L), gateway.playedTrackIds)
    }

    @Test
    fun preResolveNextStreamingTrackSchedulesResolveAndReturnsReplacement() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        provider.playbackUrl = { trackId -> "mock://playback/$trackId.mp3" }
        val viewModel = viewModelWithProvider(provider)
        val taskQueue = FakeStreamingPlaybackTaskQueue()
        val local = localTrack(id = 1L)
        val next = streamingPlaceholderTrack(id = 2L, providerTrackId = "next-2")
        val resolved = mutableListOf<Pair<Long, Track?>>()
        viewModel.bindStreamingPlaybackCoordinator(ResolveStreamingPlaybackUseCase(), taskQueue)

        val scheduled = viewModel.preResolveNextStreamingTrack(
            snapshot = playbackSnapshot(currentTrack = local, currentIndex = 0, queueSize = 2, playing = true),
            queue = listOf(local, next),
            quality = StreamingAudioQuality.HIGH
        ) { oldTrackId, track ->
            resolved += oldTrackId to track
        }

        assertTrue(scheduled)
        assertEquals(1, taskQueue.nextUrlResolveCount)
        waitUntil { resolved.isNotEmpty() }
        assertEquals(
            listOf(StreamingPlaybackRequest(StreamingProviderName.NETEASE, "next-2", StreamingAudioQuality.HIGH)),
            provider.playbackRequests.toList()
        )
        assertEquals(listOf(2L), resolved.map { it.first })
        assertEquals(1, resolved.size)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun resolveStreamingTrackListForPlaybackSchedulesCurrentResolveAndReplacesTrack() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        provider.playbackUrl = { trackId -> "mock://playback/$trackId.mp3" }
        val viewModel = viewModelWithProvider(provider)
        val taskQueue = FakeStreamingPlaybackTaskQueue()
        val local = localTrack(id = 1L)
        val unresolved = streamingPlaceholderTrack(id = 2L, providerTrackId = "play-2")
        val resolved = mutableListOf<ResolvedStreamingTrackList?>()
        viewModel.bindStreamingPlaybackCoordinator(ResolveStreamingPlaybackUseCase(), taskQueue)

        val scheduled = viewModel.resolveStreamingTrackListForPlayback(
            tracks = listOf(local, unresolved),
            index = 1,
            quality = StreamingAudioQuality.HIGH
        ) { result ->
            resolved += result
        }

        assertTrue(scheduled)
        assertEquals(1, taskQueue.currentUrlResolveCount)
        waitUntil { resolved.isNotEmpty() }
        assertEquals(
            listOf(StreamingPlaybackRequest(StreamingProviderName.NETEASE, "play-2", StreamingAudioQuality.HIGH)),
            provider.playbackRequests.toList()
        )
        assertEquals(1, resolved.size)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun prepareCurrentStreamingQueueResolveTargetSelectsCurrentPlaceholder() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
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
    fun prepareCurrentStreamingQueueResolveTargetFallsBackToSnapshotTrack() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val current = streamingPlaceholderTrack(id = 2L, providerTrackId = "play-2")

        val target = viewModel.prepareCurrentStreamingQueueResolveTarget(
            snapshot = playbackSnapshot(
                currentTrack = current,
                currentIndex = 7,
                queueSize = 0,
                playing = true
            ),
            queue = emptyList()
        )

        assertEquals(listOf(current), target?.tracks)
        assertEquals(0, target?.index)
    }

    @Test
    fun recoverStreamingBufferingSchedulesRecoveryAndReturnsResolvedTrack() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        provider.playbackUrl = { trackId -> "mock://playback/$trackId.mp3" }
        val viewModel = viewModelWithProvider(provider)
        val taskQueue = FakeStreamingPlaybackTaskQueue()
        val planner = FakeStreamingPlaybackResolvePlanner()
        val current = resolvedStreamingTrack(id = 3L, providerTrackId = "recover-3")
        val resolved = mutableListOf<StreamingRecoveryResolution?>()
        planner.recoveryRequest = StreamingRecoveryRequest(
            key = "recover-3:high",
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "recover-3",
            quality = StreamingAudioQuality.HIGH,
            metadata = StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "recover-3",
                title = "Recover 3",
                artist = "Artist",
                album = "Streaming",
                durationMs = 120_000L
            )
        )
        viewModel.bindStreamingPlaybackCoordinator(planner, taskQueue)

        val recoveryQuality = viewModel.recoverStreamingBuffering(
            snapshot = playbackSnapshot(
                currentTrack = current,
                currentIndex = 0,
                queueSize = 1,
                playing = true,
                positionMs = 34_000L
            ),
            selectedQuality = StreamingAudioQuality.LOSSLESS,
            adaptiveQuality = StreamingAudioQuality.LOSSLESS
        ) { result ->
            resolved += result
        }

        assertEquals(StreamingAudioQuality.HIGH, recoveryQuality)
        assertEquals(1, taskQueue.currentPlaybackRecoveryCount)
        waitUntil { resolved.isNotEmpty() }
        assertEquals(
            listOf(StreamingPlaybackRequest(StreamingProviderName.NETEASE, "recover-3", StreamingAudioQuality.HIGH)),
            provider.playbackRequests.toList()
        )
        assertEquals(listOf(34_000L), resolved.map { it?.positionMs })
        assertEquals(listOf(StreamingAudioQuality.HIGH), resolved.map { it?.quality })
        assertEquals(listOf("recover-3:high"), planner.clearedRecoveryKeys)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun prepareRecommendationTrackListMapsStreamingTracksToPlaceholders() {
        val viewModel = MainActivityViewModel(SavedStateHandle())

        val target = viewModel.prepareRecommendationTrackList(
            listOf(
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "daily-1",
                    title = "Daily 1",
                    artist = "Artist"
                )
            )
        )

        assertEquals(1, target.tracks.size)
        assertEquals("Daily 1", target.tracks.first().title)
        assertEquals("streaming:netease:daily-1", target.tracks.first().dataPath)
    }

    @Test
    fun prepareHeartbeatRecommendationListsDeduplicateAcrossPlaylistAndAppend() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val first = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "heart-1",
            title = "Heart 1",
            artist = "Artist"
        )
        val duplicate = first.copy(title = "Heart 1 duplicate")
        val second = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "heart-2",
            title = "Heart 2",
            artist = "Artist"
        )

        val playlist = viewModel.prepareHeartbeatRecommendationPlaylist(listOf(first, duplicate))
        val append = viewModel.prepareHeartbeatRecommendationAppend(listOf(first, second))

        assertEquals(listOf("streaming:netease:heart-1"), playlist.tracks.map { it.dataPath })
        assertEquals(listOf("streaming:netease:heart-2"), append.tracks.map { it.dataPath })
    }

    @Test
    fun streamingImportProviderPickerFiltersMockAndOptionallyRequiresSearch() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val searchable = providerDescriptor(
            provider = StreamingProviderName.NETEASE,
            displayName = "NetEase",
            supportsSearch = true
        )
        val accountOnly = providerDescriptor(
            provider = StreamingProviderName.SPOTIFY,
            displayName = "Spotify",
            supportsSearch = false
        )
        val mock = providerDescriptor(
            provider = StreamingProviderName.MOCK,
            displayName = "Mock",
            supportsSearch = true
        )

        val playlistImport = viewModel.streamingImportProviderPickerState(
            listOf(searchable, accountOnly, mock),
            requireSearch = true
        )
        val likedImport = viewModel.streamingImportProviderPickerState(
            listOf(searchable, accountOnly, mock),
            requireSearch = false
        )

        assertEquals(listOf(StreamingProviderName.NETEASE), playlistImport.providers.map { it.name })
        assertEquals(listOf("NetEase"), playlistImport.labels.toList())
        assertEquals(
            listOf(StreamingProviderName.NETEASE, StreamingProviderName.SPOTIFY),
            likedImport.providers.map { it.name }
        )
        assertEquals(listOf("NetEase", "Spotify"), likedImport.labels.toList())
    }

    @Test
    fun streamingPlaylistImportStatusSummarizesImportResult() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val summary = app.echo.next.streaming.StreamingPlaylistImporter.StreamingPlaylistImportSummary(
            provider = StreamingProviderName.NETEASE,
            playlistName = "Remote",
            matchedTracks = listOf(
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "matched-1",
                    title = "Matched 1",
                    artist = "Artist"
                ),
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "matched-2",
                    title = "Matched 2",
                    artist = "Artist"
                )
            ),
            unresolvedTracks = listOf(localTrack(7L)),
            errors = emptyList()
        )

        val status = viewModel.streamingPlaylistImportStatus(summary)

        assertEquals(2, status.matchedCount)
        assertEquals(3, status.totalRequested)
        assertEquals(1, status.unresolvedCount)
    }

    @Test
    fun prepareStreamingPlaylistImportTargetParsesLinksAndFallbackIds() {
        val viewModel = MainActivityViewModel(SavedStateHandle())

        val link = viewModel.prepareStreamingPlaylistImportTarget(
            "https://music.163.com/#/playlist?id=123456",
            StreamingProviderName.QQ_MUSIC
        )
        val rawId = viewModel.prepareStreamingPlaylistImportTarget(
            "abc-123",
            StreamingProviderName.SPOTIFY
        )
        val invalid = viewModel.prepareStreamingPlaylistImportTarget(
            "not a playlist link",
            StreamingProviderName.NETEASE
        )

        assertEquals(StreamingProviderName.NETEASE, link.provider)
        assertEquals("123456", link.providerPlaylistId)
        assertFalse(link.invalid)
        assertEquals(StreamingProviderName.SPOTIFY, rawId.provider)
        assertEquals("abc-123", rawId.providerPlaylistId)
        assertTrue(invalid.invalid)
    }

    @Test
    fun prepareStreamingPlaylistImportDialogStateBuildsTitleAndHint() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })

        val state = viewModel.prepareStreamingPlaylistImportDialogState()

        assertEquals("Import playlist from streaming", state.title)
        assertEquals("Paste playlist link or ID", state.hint)
    }

    @Test
    fun prepareStreamingPlaylistImportStartRequestBuildsStatusAndTarget() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })

        val valid = viewModel.prepareStreamingPlaylistImportStartRequest(
            "https://music.163.com/#/playlist?id=123456",
            StreamingProviderName.QQ_MUSIC
        )
        val invalid = viewModel.prepareStreamingPlaylistImportStartRequest(
            "not a playlist link",
            StreamingProviderName.NETEASE
        )

        assertTrue(valid.valid)
        assertEquals(StreamingProviderName.NETEASE, valid.provider)
        assertEquals("123456", valid.providerPlaylistId)
        assertEquals("Resolving streaming track", valid.resolvingStatus)
        assertEquals("Could not recognize playlist link", valid.invalidStatus)
        assertFalse(invalid.valid)
        assertEquals("Could not recognize playlist link", invalid.invalidStatus)
    }

    @Test
    fun streamingAuthCallbackCompletesAuthAndNotifiesGateway() = runTest {
        val gatewaySource = FakeActionStreamingGateway(StreamingProviderName.SPOTIFY)
        gatewaySource.authConnected = true
        val viewModel = MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(listOf(StreamingRepository(gatewaySource)))
        )
        val gateway = FakeStreamingActionGateway()
        viewModel.bindStreamingActionGateway(gateway)

        viewModel.refreshStreamingProviders().join()
        val handled = viewModel.handleAuthCallback(
            "echo-next://streaming-auth?provider=spotify",
            "cookie=1"
        )
        waitUntil { gateway.loginSuccessProviders.isNotEmpty() }

        assertTrue(handled)
        assertEquals(listOf("echo-next://streaming-auth?provider=spotify|cookie=1"), gatewaySource.completeAuthCalls)
        assertEquals(listOf(StreamingProviderName.SPOTIFY), gateway.loginSuccessProviders)
        assertTrue(viewModel.streaming.value.authStates[StreamingProviderName.SPOTIFY]?.connected == true)
    }

    @Test
    fun prepareManualCookieDialogStateRejectsNonAccountProviders() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })

        assertTrue(viewModel.prepareManualCookieDialogState(StreamingProviderName.MOCK).unavailable)
        assertTrue(viewModel.prepareManualCookieDialogState(StreamingProviderName.M3U8).unavailable)
        assertTrue(viewModel.prepareManualCookieDialogState(StreamingProviderName.PLUGIN).unavailable)
        val valid = viewModel.prepareManualCookieDialogState(StreamingProviderName.NETEASE)
        assertFalse(valid.unavailable)
        assertEquals("Enter account info manually", valid.title)
        assertEquals("MUSIC_U=...; os=pc; appver=...", valid.hint)
        assertEquals("Choose a streaming source to sign in", valid.unavailableStatus)
    }

    @Test
    fun prepareManualCookieAuthRequestTrimsCookieAndBuildsCallback() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        viewModel.bindStreamingActionGateway(FakeStreamingActionGateway().apply {
            language = AppLanguage.MODE_ENGLISH
        })

        val request = viewModel.prepareManualCookieAuthRequest(
            StreamingProviderName.NETEASE,
            "  MUSIC_U=abc; os=pc  "
        )

        assertEquals(StreamingProviderName.NETEASE, request?.provider)
        assertEquals(
            "echo-next://streaming-auth?provider=netease&manualCookie=1",
            request?.callbackUri
        )
        assertEquals("MUSIC_U=abc; os=pc", request?.cookieHeader)
        assertEquals("Account info is empty", viewModel.manualCookieEmptyStatus())
        assertEquals("Account info is empty", request?.emptyStatus)
        assertEquals("Account info saved", request?.savedStatus)
        assertNull(viewModel.prepareManualCookieAuthRequest(StreamingProviderName.NETEASE, "   "))
        assertNull(viewModel.prepareManualCookieAuthRequest(StreamingProviderName.MOCK, "cookie=1"))
    }

    private fun searchResult(
        provider: StreamingProviderName,
        query: String,
        page: Int,
        vararg trackIds: String
    ): StreamingSearchResult {
        return StreamingSearchResult(
            provider = provider,
            query = query,
            page = page,
            pageSize = 20,
            hasMore = true,
            tracks = trackIds.map { trackId ->
                StreamingTrack(
                    provider = provider,
                    providerTrackId = trackId,
                    title = trackId,
                    artist = "artist"
                )
            }
        )
    }

    private fun repositoryWithProvider(provider: StreamingProviderName): StreamingRepository {
        return StreamingRepository(
            RegistryStreamingGateway(
                StreamingProviderRegistry(
                    listOf(FakeStreamingProvider(provider))
                )
            )
        )
    }

    private fun repositoryWithCache(dao: FakeStreamingCacheDao): StreamingRepository {
        return StreamingRepository(
            RegistryStreamingGateway(StreamingProviderRegistry()),
            StreamingCacheRepository(dao) { 1_000L }
        )
    }

    private fun waitUntil(timeoutMs: Long = 2_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10L)
        }
    }

    private fun viewModelWithProvider(provider: FakeStreamingProvider): MainActivityViewModel {
        return MainActivityViewModel(
            SavedStateHandle(),
            FakeStreamingRepositorySource(
                listOf(
                    StreamingRepository(
                        RegistryStreamingGateway(
                            StreamingProviderRegistry(listOf(provider))
                        ),
                        playbackTrackAdapter = FakePlaybackTrackAdapter()
                    )
                )
            )
        )
    }

    private data class StreamingLocalImportCall(
        val playlistName: String,
        val provider: StreamingProviderName,
        val providerPlaylistId: String,
        val trackIds: List<String>,
        val linkWhenProviderPlaylistIdBlank: Boolean
    )

    private data class StreamingLocalSyncCall(
        val playlistId: Long,
        val providerPlaylistId: String,
        val trackIds: List<String>
    )

    private data class StreamingLoginPlaylistCall(
        val playlistName: String,
        val provider: StreamingProviderName
    )

    private data class StreamingTrackMatchDirectCall(
        val trackId: Long,
        val provider: StreamingProviderName
    )

    private data class StreamingTrackMatchLoadCall(
        val trackId: Long,
        val provider: StreamingProviderName
    )

    private data class StreamingTrackMatchSaveCall(
        val trackId: Long,
        val provider: StreamingProviderName,
        val providerTrackId: String
    )

    private data class StreamingTrackMatchCandidatesCall(
        val provider: StreamingProviderName,
        val candidateTrackIds: List<Long>
    )

    private fun link(playlistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist =
        StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = playlistId,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-$playlistId",
            lastSyncMs = 0L
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

    private fun resolvedStreamingTrack(id: Long, providerTrackId: String): Track =
        Track(
            id,
            "Resolved $id",
            "Artist",
            "Streaming",
            120_000L,
            Uri.EMPTY,
            "streaming:${StreamingProviderName.NETEASE.wireName}:$providerTrackId"
        )

    private fun providerDescriptor(
        provider: StreamingProviderName,
        displayName: String,
        supportsSearch: Boolean
    ): StreamingProviderDescriptor =
        StreamingProviderDescriptor(
            name = provider,
            displayName = displayName,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = supportsSearch,
                supportsPlayback = true,
                supportsAuth = true,
                supportsPlaylists = true
            ),
            auth = StreamingAuthState()
        )

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

    private class FakeStreamingPlaybackResolvePlanner : StreamingPlaybackResolvePlanner {
        var recoveryRequest: StreamingRecoveryRequest? = null
        val clearedRecoveryKeys = mutableListOf<String?>()

        override fun prepare(tracks: List<Track>?, index: Int): ResolveStreamingPlaybackRequest? = null

        override fun replaceResolvedTrack(
            request: ResolveStreamingPlaybackRequest,
            resolved: Track
        ): ArrayList<Track> = ArrayList(request.tracks)

        override fun prepareNextPreResolve(
            snapshot: PlaybackStateSnapshot?,
            queue: List<Track>?
        ): StreamingPreResolveRequest? = null

        override fun clearPreResolve(key: String?) = Unit

        override fun prepareRecovery(
            snapshot: PlaybackStateSnapshot?,
            selectedQuality: StreamingAudioQuality,
            adaptiveQuality: StreamingAudioQuality
        ): StreamingRecoveryRequest? = recoveryRequest

        override fun clearRecovery(key: String?) {
            clearedRecoveryKeys += key
        }
    }

    private class FakeStreamingLocalPlaylistOperations(
        private val importer: (
            playlistName: String,
            provider: StreamingProviderName,
            providerPlaylistId: String,
            streamingTracks: List<StreamingTrack>,
            linkWhenProviderPlaylistIdBlank: Boolean
        ) -> PlaylistImportResult = { _, _, _, tracks, _ ->
            PlaylistImportResult(-1L, "", tracks.size, tracks.size, tracks.size, 0)
        },
        private val syncer: (
            link: StreamingPlaylistSyncStore.LinkedPlaylist,
            streamingTracks: List<StreamingTrack>
        ) -> StreamingLocalPlaylistSyncResult = { link, tracks ->
            StreamingLocalPlaylistSyncResult(link.localPlaylistId, tracks.size, false)
        },
        private val ensurer: (
            playlistName: String,
            provider: StreamingProviderName
        ) -> StreamingLoginPlaylistResult = { playlistName, _ ->
            StreamingLoginPlaylistResult(playlistName = playlistName)
        },
        private val linkResolver: (localPlaylistId: Long) -> StreamingPlaylistSyncStore.LinkedPlaylist? = { null }
    ) : StreamingLocalPlaylistOperations {
        override fun importStreamingPlaylist(
            playlistName: String,
            provider: StreamingProviderName,
            providerPlaylistId: String,
            streamingTracks: List<StreamingTrack>,
            linkWhenProviderPlaylistIdBlank: Boolean
        ): PlaylistImportResult =
            importer(playlistName, provider, providerPlaylistId, streamingTracks, linkWhenProviderPlaylistIdBlank)

        override fun syncStreamingPlaylist(
            link: StreamingPlaylistSyncStore.LinkedPlaylist,
            streamingTracks: List<StreamingTrack>
        ): StreamingLocalPlaylistSyncResult = syncer(link, streamingTracks)

        override fun ensureStreamingLoginPlaylist(
            playlistName: String,
            provider: StreamingProviderName
        ): StreamingLoginPlaylistResult = ensurer(playlistName, provider)

        override fun linkedPlaylist(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? =
            linkResolver(localPlaylistId)
    }

    private class FakePlaybackTrackAdapter : StreamingPlaybackTrackAdapter {
        override fun toTrack(source: StreamingPlaybackSource, metadata: StreamingTrack?): Track {
            return Track(
                9_001L,
                metadata?.title ?: source.providerTrackId,
                metadata?.artist ?: source.provider.wireName,
                metadata?.album ?: "Streaming",
                metadata?.durationMs ?: 0L,
                Uri.EMPTY,
                "streaming:${source.provider.wireName}:${source.providerTrackId}"
            )
        }
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

    private class FakeStreamingTrackMatchStore : StreamingTrackMatchStore {
        var loadedProviderTrackId: String = ""
        var providerTrackIdFromCandidateResult: String = ""
        var heartbeatCandidates: List<Track> = emptyList()
        var heartbeatQueueSnapshot: List<Track> = emptyList()
        var heartbeatSeedMissLogMessage: String = ""
        val directProviderTrackIds = mutableMapOf<Long, String>()
        val events = mutableListOf<Any>()
        var lastHeartbeatSeedMissSnapshot: PlaybackStateSnapshot? = null
        var lastHeartbeatSeedMissStoreSnapshot: PlaybackStateSnapshot? = null
        var lastHeartbeatSeedMissQueue: List<Track?>? = null

        override fun directProviderTrackId(track: Track, provider: StreamingProviderName): String {
            events += StreamingTrackMatchDirectCall(track.id, provider)
            return directProviderTrackIds[track.id].orEmpty()
        }

        override fun providerTrackIdFor(track: Track, provider: StreamingProviderName): String {
            events += StreamingTrackMatchLoadCall(track.id, provider)
            return loadedProviderTrackId
        }

        override fun saveProviderTrackId(
            track: Track,
            provider: StreamingProviderName,
            providerTrackId: String
        ) {
            events += StreamingTrackMatchSaveCall(track.id, provider, providerTrackId)
        }

        override fun providerTrackIdFromCandidates(
            candidates: List<Track?>?,
            provider: StreamingProviderName?
        ): String {
            if (provider != null) {
                events += StreamingTrackMatchCandidatesCall(
                    provider,
                    candidates.orEmpty().mapNotNull { it?.id }
                )
            }
            return providerTrackIdFromCandidateResult
        }

        override fun heartbeatSeedCandidates(
            serviceSnapshot: PlaybackStateSnapshot?,
            serviceQueue: List<Track?>?,
            storeSnapshot: PlaybackStateSnapshot?,
            viewModelQueue: List<Track?>?
        ): List<Track> = heartbeatCandidates

        override fun snapshotQueueForHeartbeat(
            serviceQueue: List<Track?>?,
            viewModelQueue: List<Track?>?,
            storeSnapshot: PlaybackStateSnapshot?
        ): List<Track> = heartbeatQueueSnapshot

        override fun heartbeatSeedMissMessage(
            provider: StreamingProviderName?,
            snapshot: PlaybackStateSnapshot?,
            storeSnapshot: PlaybackStateSnapshot?,
            queue: List<Track?>?
        ): String {
            lastHeartbeatSeedMissSnapshot = snapshot
            lastHeartbeatSeedMissStoreSnapshot = storeSnapshot
            lastHeartbeatSeedMissQueue = queue
            return heartbeatSeedMissLogMessage
        }
    }

    private class FakeStreamingProvider(provider: StreamingProviderName) : StreamingProvider {
        override val descriptor: StreamingProviderDescriptor = StreamingProviderDescriptor(
            name = provider,
            displayName = provider.wireName,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = true,
                supportsPlayback = true,
                supportsAuth = true,
                supportsPlaylists = true
            ),
            auth = StreamingAuthState()
        )
        val playlistRequests = mutableListOf<StreamingPlaylistRequest>()
        val searchRequests = mutableListOf<StreamingSearchRequest>()
        val playbackRequests = mutableListOf<StreamingPlaybackRequest>()
        val completeAuthCalls = mutableListOf<String>()
        var searchTracks: List<StreamingTrack> = emptyList()
        var playlistTitle: String? = "Remote Playlist"
        var playlistTrackIds: List<String> = listOf("track-1", "track-2", "track-3")
        var playbackUrl: (String) -> String = { trackId -> "https://example.test/$trackId.mp3" }
        var authConnected: Boolean = false

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
            searchRequests += request
            return StreamingSearchResult(
                provider = descriptor.name,
                query = request.query,
                page = request.page,
                pageSize = request.pageSize,
                tracks = searchTracks
            )
        }

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
            playbackRequests += request
            return StreamingPlaybackSource(
                provider = descriptor.name,
                providerTrackId = request.providerTrackId,
                url = playbackUrl(request.providerTrackId)
            )
        }

        override suspend fun completeAuth(callbackUri: String): app.echo.next.streaming.StreamingAuthResult {
            completeAuthCalls += "$callbackUri|null"
            return app.echo.next.streaming.StreamingAuthResult(
                descriptor.name,
                StreamingAuthState(connected = authConnected)
            )
        }

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
            playlistRequests += request
            val allTracks = playlistTrackIds
            val tracks = if (request.page == 1) allTracks.take(2) else allTracks.drop(2)
            return StreamingPlaylistDetail(
                provider = descriptor.name,
                providerPlaylistId = request.providerPlaylistId,
                playlist = StreamingPlaylist(
                    provider = descriptor.name,
                    providerPlaylistId = request.providerPlaylistId,
                    title = playlistTitle.orEmpty(),
                    trackCount = allTracks.size
                ),
                tracks = tracks.map { trackId ->
                    StreamingTrack(
                        provider = descriptor.name,
                        providerTrackId = trackId,
                        title = trackId,
                        artist = "artist"
                    )
                },
                total = allTracks.size,
                page = request.page,
                pageSize = request.pageSize,
                hasMore = request.page == 1
            )
        }
    }

    private class FakeStreamingActionGateway : MainActivityStreamingActionGateway {
        var quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS
        var language: String = AppLanguage.MODE_SYSTEM
        val playedTrackIds = mutableListOf<Long>()
        val loginSuccessProviders = mutableListOf<StreamingProviderName>()

        override fun streamingPlaybackQuality(): StreamingAudioQuality = quality

        override fun languageMode(): String = language

        override fun openAuthLaunch(launch: MainActivityStreamingAuthLaunch?): Boolean = true

        override fun playResolvedTrack(track: Track) {
            playedTrackIds += track.id
        }

        override fun onStreamingLoginSuccess(provider: StreamingProviderName) {
            loginSuccessProviders += provider
        }
    }

    private class FakeLikedTracksGateway(
        private val providerName: StreamingProviderName,
        private val likedTracks: List<StreamingTrack>
    ) : StreamingGateway {
        private val descriptor = StreamingProviderDescriptor(
            name = providerName,
            displayName = providerName.wireName,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = true,
                supportsPlayback = true,
                supportsAuth = true,
                supportsPlaylists = true
            ),
            auth = StreamingAuthState()
        )

        override suspend fun providers(): List<StreamingProviderDescriptor> = listOf(descriptor)

        override suspend fun providerCapabilities(): List<app.echo.next.streaming.StreamingProviderCapability> = emptyList()

        override suspend fun providersHealth(): List<app.echo.next.streaming.StreamingProviderHealth> = emptyList()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
            StreamingSearchResult(
                provider = providerName,
                query = request.query,
                page = request.page,
                pageSize = request.pageSize
            )

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail =
            StreamingPlaylistDetail(provider = providerName, providerPlaylistId = request.providerPlaylistId)

        override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> = emptyList()

        override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> = likedTracks

        override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> = emptyList()

        override suspend fun heartbeatRecommendations(
            request: app.echo.next.streaming.StreamingHeartbeatRequest
        ): List<StreamingTrack> = emptyList()

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
            StreamingPlaybackSource(
                provider = providerName,
                providerTrackId = request.providerTrackId,
                url = "https://example.test/${request.providerTrackId}.mp3"
            )

        override suspend fun authState(provider: StreamingProviderName): StreamingAuthState = StreamingAuthState()

        override suspend fun startAuth(
            request: app.echo.next.streaming.StreamingAuthRequest
        ): app.echo.next.streaming.StreamingAuthResult =
            app.echo.next.streaming.StreamingAuthResult(providerName, StreamingAuthState())

        override suspend fun completeAuth(
            provider: StreamingProviderName,
            callbackUri: String,
            cookieHeader: String?
        ): app.echo.next.streaming.StreamingAuthResult =
            app.echo.next.streaming.StreamingAuthResult(providerName, StreamingAuthState())

        override suspend fun signOut(provider: StreamingProviderName): StreamingAuthState = StreamingAuthState()
    }

    private class FakeActionStreamingGateway(
        private val providerName: StreamingProviderName
    ) : StreamingGateway {
        var authConnected: Boolean = false
        val completeAuthCalls = mutableListOf<String>()

        private val descriptor = StreamingProviderDescriptor(
            name = providerName,
            displayName = providerName.wireName,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = true,
                supportsPlayback = true,
                supportsAuth = true,
                supportsPlaylists = true
            ),
            auth = StreamingAuthState()
        )

        override suspend fun providers(): List<StreamingProviderDescriptor> = listOf(descriptor)

        override suspend fun providerCapabilities(): List<app.echo.next.streaming.StreamingProviderCapability> =
            app.echo.next.streaming.StreamingCapabilityResolver.providerCapabilities(listOf(descriptor))

        override suspend fun providersHealth(): List<app.echo.next.streaming.StreamingProviderHealth> = emptyList()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
            StreamingSearchResult(provider = providerName, query = request.query, page = request.page, pageSize = request.pageSize)

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail =
            StreamingPlaylistDetail(provider = providerName, providerPlaylistId = request.providerPlaylistId)

        override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> = emptyList()

        override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> = emptyList()

        override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> = emptyList()

        override suspend fun heartbeatRecommendations(
            request: app.echo.next.streaming.StreamingHeartbeatRequest
        ): List<StreamingTrack> = emptyList()

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
            StreamingPlaybackSource(providerName, request.providerTrackId, "https://example.test/${request.providerTrackId}.mp3")

        override suspend fun authState(provider: StreamingProviderName): StreamingAuthState =
            StreamingAuthState(connected = authConnected)

        override suspend fun startAuth(
            request: app.echo.next.streaming.StreamingAuthRequest
        ): app.echo.next.streaming.StreamingAuthResult =
            app.echo.next.streaming.StreamingAuthResult(providerName, StreamingAuthState())

        override suspend fun completeAuth(
            provider: StreamingProviderName,
            callbackUri: String,
            cookieHeader: String?
        ): app.echo.next.streaming.StreamingAuthResult {
            completeAuthCalls += "$callbackUri|$cookieHeader"
            return app.echo.next.streaming.StreamingAuthResult(
                providerName,
                StreamingAuthState(connected = authConnected)
            )
        }

        override suspend fun signOut(provider: StreamingProviderName): StreamingAuthState = StreamingAuthState()
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val dispatcher = UnconfinedTestDispatcher()
                Dispatchers.setMain(dispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
    }
}
