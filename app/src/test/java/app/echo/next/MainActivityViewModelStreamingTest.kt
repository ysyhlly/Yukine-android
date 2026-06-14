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
        viewModel.bindStreamingLocalPlaylistImporter { playlistName, importProvider, providerPlaylistId, tracks, linkBlank ->
            importCalls += StreamingLocalImportCall(
                playlistName = playlistName,
                provider = importProvider,
                providerPlaylistId = providerPlaylistId,
                trackIds = tracks.map { it.providerTrackId },
                linkWhenProviderPlaylistIdBlank = linkBlank
            )
            PlaylistImportResult(7L, playlistName, tracks.size, tracks.size, tracks.size, 0)
        }

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
        viewModel.bindStreamingLocalPlaylistImporter { playlistName, _, _, tracks, _ ->
            importCalls += 1
            PlaylistImportResult(7L, playlistName, tracks.size, tracks.size, tracks.size, 0)
        }

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
        viewModel.bindStreamingLocalPlaylistImporter { playlistName, importProvider, providerPlaylistId, tracks, linkBlank ->
            importCalls += StreamingLocalImportCall(
                playlistName = playlistName,
                provider = importProvider,
                providerPlaylistId = providerPlaylistId,
                trackIds = tracks.map { it.providerTrackId },
                linkWhenProviderPlaylistIdBlank = linkBlank
            )
            PlaylistImportResult(9L, playlistName, tracks.size, tracks.size, tracks.size, 0)
        }

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
        viewModel.bindStreamingLocalPlaylistSyncer { link, tracks ->
            syncCalls += StreamingLocalSyncCall(
                playlistId = link.localPlaylistId,
                providerPlaylistId = link.providerPlaylistId,
                trackIds = tracks.map { it.providerTrackId }
            )
            StreamingLocalPlaylistSyncResult(link.localPlaylistId, tracks.size, false)
        }

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
        viewModel.bindStreamingLocalPlaylistSyncer { link, tracks ->
            syncCalls += StreamingLocalSyncCall(
                playlistId = link.localPlaylistId,
                providerPlaylistId = link.providerPlaylistId,
                trackIds = tracks.map { it.providerTrackId }
            )
            StreamingLocalPlaylistSyncResult(link.localPlaylistId, tracks.size, false)
        }

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
        viewModel.bindStreamingLoginPlaylistEnsurer { playlistName, provider ->
            ensureCalls += StreamingLoginPlaylistCall(playlistName, provider)
            StreamingLoginPlaylistResult(23L, playlistName)
        }

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
    fun recoverStreamingBufferingSchedulesRecoveryAndReturnsResolvedTrack() = runTest {
        val provider = FakeStreamingProvider(StreamingProviderName.NETEASE)
        provider.playbackUrl = { trackId -> "mock://playback/$trackId.mp3" }
        val viewModel = viewModelWithProvider(provider)
        val taskQueue = FakeStreamingPlaybackTaskQueue()
        val current = resolvedStreamingTrack(id = 3L, providerTrackId = "recover-3")
        val resolved = mutableListOf<StreamingRecoveryResolution?>()
        viewModel.bindStreamingPlaybackCoordinator(ResolveStreamingPlaybackUseCase(), taskQueue)

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
        assertFalse(viewModel.streaming.value.loading)
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
                        )
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
        val directProviderTrackIds = mutableMapOf<Long, String>()
        val events = mutableListOf<Any>()

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
        val playedTrackIds = mutableListOf<Long>()
        val loginSuccessProviders = mutableListOf<StreamingProviderName>()

        override fun streamingPlaybackQuality(): StreamingAudioQuality = quality

        override fun languageMode(): String = AppLanguage.MODE_SYSTEM

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
    private val dispatcher = UnconfinedTestDispatcher()

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
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
