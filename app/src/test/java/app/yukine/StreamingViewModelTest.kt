package app.yukine

import android.net.Uri
import app.yukine.model.PlaylistImportResult
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAuthKind
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingAlbum
import app.yukine.streaming.StreamingArtist
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingMvItem
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaybackCandidate
import app.yukine.streaming.StreamingHeartbeatRequest
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingPlaylistDetail
import app.yukine.streaming.StreamingPlaylistRequest
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingPlaylistSyncDirection
import app.yukine.streaming.StreamingProviderCapabilities
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingProvider
import app.yukine.streaming.StreamingProviderRegistry
import app.yukine.streaming.StreamingProviderStatus
import app.yukine.streaming.RegistryStreamingGateway
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingSearchItem
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectProviderClearsSearchPlaybackAndLaunchState() {
        val viewModel = StreamingViewModel()
        viewModel.search.updateStreamingSearchResult(searchResult(StreamingProviderName.NETEASE, "echo", 1))
        viewModel.auth.updateAuthLaunch(
            StreamingProviderName.NETEASE,
            StreamingAuthState(connected = false, kind = StreamingAuthKind.REMOTE_GATEWAY),
            "https://login"
        )

        viewModel.auth.selectProvider(StreamingProviderName.QQ_MUSIC)

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
        viewModel.search.updateStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 1, listOf(streamingTrack("1")))
        )

        viewModel.search.appendStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 2, listOf(streamingTrack("2")))
        )

        val result = viewModel.streaming.value.searchResult
        assertEquals(listOf("1", "2"), result?.tracks?.map { it.providerTrackId })
        assertEquals(2, result?.unifiedItems?.size)
        assertFalse(viewModel.streaming.value.loadingMore)
    }

    @Test
    fun updateSearchResultFiltersOutNonSongStreamingItems() {
        val viewModel = StreamingViewModel()
        val song = streamingTrack("song-1")
        val album = StreamingAlbum(StreamingProviderName.NETEASE, "album-1", "Album", "Artist")
        val artist = StreamingArtist(StreamingProviderName.NETEASE, "artist-1", "Artist")
        val playlist = StreamingPlaylist(StreamingProviderName.NETEASE, "playlist-1", "Playlist")
        val mv = StreamingMvItem(StreamingProviderName.NETEASE, "mv-1", title = "MV", artist = "Artist")

        viewModel.search.updateStreamingSearchResult(
            StreamingSearchResult(
                provider = StreamingProviderName.NETEASE,
                query = "echo",
                page = 1,
                pageSize = 20,
                total = 5,
                tracks = listOf(song),
                albums = listOf(album),
                artists = listOf(artist),
                playlists = listOf(playlist),
                mvs = listOf(mv),
                items = listOf(
                    StreamingSearchItem.fromTrack(song),
                    StreamingSearchItem.fromAlbum(album),
                    StreamingSearchItem.fromArtist(artist),
                    StreamingSearchItem.fromPlaylist(playlist),
                    StreamingSearchItem.fromMv(mv)
                )
            )
        )

        val result = viewModel.streaming.value.searchResult
        assertEquals(listOf("song-1"), result?.tracks?.map { it.providerTrackId })
        assertTrue(result?.albums.orEmpty().isEmpty())
        assertTrue(result?.artists.orEmpty().isEmpty())
        assertTrue(result?.playlists.orEmpty().isEmpty())
        assertTrue(result?.mvs.orEmpty().isEmpty())
        assertEquals(listOf(StreamingMediaType.TRACK), result?.unifiedItems?.map { it.type })
        assertEquals(1, result?.total)
    }

    @Test
    fun appendSearchResultFiltersOutNonSongStreamingItems() {
        val viewModel = StreamingViewModel()
        val song1 = streamingTrack("song-1")
        val song2 = streamingTrack("song-2")
        val playlist = StreamingPlaylist(StreamingProviderName.NETEASE, "playlist-1", "Playlist")
        viewModel.search.updateStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 1, listOf(song1))
        )

        viewModel.search.appendStreamingSearchResult(
            StreamingSearchResult(
                provider = StreamingProviderName.NETEASE,
                query = "echo",
                page = 2,
                pageSize = 20,
                total = 3,
                tracks = listOf(song2),
                playlists = listOf(playlist),
                items = listOf(
                    StreamingSearchItem.fromTrack(song2),
                    StreamingSearchItem.fromPlaylist(playlist)
                )
            )
        )

        val result = viewModel.streaming.value.searchResult
        assertEquals(listOf("song-1", "song-2"), result?.tracks?.map { it.providerTrackId })
        assertTrue(result?.playlists.orEmpty().isEmpty())
        assertEquals(listOf(StreamingMediaType.TRACK, StreamingMediaType.TRACK), result?.unifiedItems?.map { it.type })
    }

    @Test
    fun searchAllProvidersRanksTracksBySimilarityBeforeProviderOrder() = runTest {
        val weakProvider = FakeProvider(
            descriptor = descriptor(StreamingProviderName.NETEASE, "NetEase"),
            searchResult = searchResult(
                StreamingProviderName.NETEASE,
                "Echo",
                1,
                listOf(streamingTrack("netease-weak").copy(title = "Another Song", artist = "Other"))
            )
        )
        val strongProvider = FakeProvider(
            descriptor = descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music"),
            searchResult = searchResult(
                StreamingProviderName.QQ_MUSIC,
                "Echo",
                1,
                listOf(streamingTrack("qq-strong").copy(provider = StreamingProviderName.QQ_MUSIC, title = "Echo", artist = "Artist"))
            )
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(
            StreamingRepository(
                RegistryStreamingGateway(StreamingProviderRegistry(listOf(weakProvider, strongProvider)))
            )
        )
        viewModel.auth.updateProviders(
            providers = listOf(
                descriptor(StreamingProviderName.NETEASE, "NetEase"),
                descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music")
            ),
            capabilities = listOf(capability(StreamingProviderName.NETEASE), capability(StreamingProviderName.QQ_MUSIC)),
            health = emptyList()
        )

        viewModel.search.searchAllStreaming("Echo", setOf(StreamingMediaType.TRACK), pageSize = 20)
        waitUntil { !viewModel.streaming.value.loading }

        assertEquals(
            listOf("qq-strong", "netease-weak"),
            viewModel.streaming.value.searchResult?.tracks?.map { it.providerTrackId }
        )
    }

    @Test
    fun searchAllProvidersMergesSameArtistAndTitleAcrossSources() = runTest {
        val neteaseProvider = FakeProvider(
            descriptor = descriptor(StreamingProviderName.NETEASE, "NetEase"),
            searchResult = searchResult(
                StreamingProviderName.NETEASE,
                "Echo",
                1,
                listOf(
                    streamingTrack("netease-echo").copy(
                        title = "Echo (如果是回声)",
                        artist = "Artist / Guest",
                        durationMs = 210_000L
                    )
                )
            )
        )
        val qqProvider = FakeProvider(
            descriptor = descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music"),
            searchResult = searchResult(
                StreamingProviderName.QQ_MUSIC,
                "Echo",
                1,
                listOf(
                    streamingTrack("qq-echo").copy(
                        provider = StreamingProviderName.QQ_MUSIC,
                        title = "Echo",
                        artist = "Guest feat. Artist",
                        durationMs = 214_500L,
                        playbackCandidates = listOf(
                            StreamingPlaybackCandidate(
                                provider = StreamingProviderName.QQ_MUSIC,
                                quality = StreamingAudioQuality.HIGH,
                                label = "QQ 高音质",
                                providerTrackId = "qq-echo"
                            )
                        )
                    )
                )
            )
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(
            StreamingRepository(
                RegistryStreamingGateway(StreamingProviderRegistry(listOf(neteaseProvider, qqProvider)))
            )
        )
        viewModel.auth.updateProviders(
            providers = listOf(
                descriptor(StreamingProviderName.NETEASE, "NetEase"),
                descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music")
            ),
            capabilities = listOf(capability(StreamingProviderName.NETEASE), capability(StreamingProviderName.QQ_MUSIC)),
            health = emptyList()
        )

        viewModel.search.searchAllStreaming("Echo", setOf(StreamingMediaType.TRACK), pageSize = 20)
        waitUntil { !viewModel.streaming.value.loading }

        val tracks = viewModel.streaming.value.searchResult?.tracks.orEmpty()
        assertEquals(1, tracks.size)
        // 备用音源被折叠进代表项的候选列表，供失败回退与手动切换。
        val candidateProviders = tracks.first().playbackCandidates.map { it.provider }
        assertTrue(candidateProviders.contains(StreamingProviderName.QQ_MUSIC))
        assertTrue(
            tracks.first().playbackCandidates.any { candidate ->
                candidate.provider == StreamingProviderName.QQ_MUSIC &&
                    candidate.quality == StreamingAudioQuality.HIGH
            }
        )
        assertEquals(2, tracks.first().playbackSourceCount)
    }

    @Test
    fun searchAllProvidersUsesPlaybackCapableSourceAsMergedRepresentative() = runTest {
        val searchOnlyDescriptor = descriptor(StreamingProviderName.NETEASE, "NetEase").let { value ->
            value.copy(capabilities = value.capabilities.copy(supportsPlayback = false))
        }
        val neteaseProvider = FakeProvider(
            descriptor = searchOnlyDescriptor,
            searchResult = searchResult(
                StreamingProviderName.NETEASE,
                "Echo",
                1,
                listOf(
                    streamingTrack("netease-echo").copy(
                        title = "Echo",
                        artist = "Artist feat Guest",
                        durationMs = 210_000L
                    )
                )
            )
        )
        val qqProvider = FakeProvider(
            descriptor = descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music"),
            searchResult = searchResult(
                StreamingProviderName.QQ_MUSIC,
                "Echo",
                1,
                listOf(
                    streamingTrack("qq-echo").copy(
                        provider = StreamingProviderName.QQ_MUSIC,
                        title = "Echo",
                        artist = "Guest / Artist",
                        durationMs = 211_000L
                    )
                )
            )
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(
            StreamingRepository(
                RegistryStreamingGateway(StreamingProviderRegistry(listOf(neteaseProvider, qqProvider)))
            )
        )
        viewModel.auth.updateProviders(
            providers = listOf(searchOnlyDescriptor, descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music")),
            capabilities = listOf(
                capability(StreamingProviderName.NETEASE).copy(supportsPlayback = false),
                capability(StreamingProviderName.QQ_MUSIC)
            ),
            health = emptyList()
        )

        viewModel.search.searchAllStreaming("Echo", setOf(StreamingMediaType.TRACK), pageSize = 20)
        waitUntil { !viewModel.streaming.value.loading }

        val merged = viewModel.streaming.value.searchResult?.tracks?.single()
        assertEquals(StreamingProviderName.QQ_MUSIC, merged?.provider)
        assertEquals(2, merged?.playbackSourceCount)
    }

    @Test
    fun searchAllProvidersDoesNotMergeDuplicateEntriesFromTheSameSourceFamily() = runTest {
        val provider = FakeProvider(
            descriptor = descriptor(StreamingProviderName.NETEASE, "NetEase"),
            searchResult = searchResult(
                StreamingProviderName.NETEASE,
                "Echo",
                1,
                listOf(
                    streamingTrack("netease-album").copy(
                        title = "Echo",
                        artist = "Artist",
                        durationMs = 210_000L
                    ),
                    streamingTrack("netease-single").copy(
                        title = "Echo",
                        artist = "Artist",
                        durationMs = 211_000L
                    )
                )
            )
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(repository(provider))
        viewModel.auth.updateProviders(
            providers = listOf(provider.descriptor),
            capabilities = listOf(capability(StreamingProviderName.NETEASE)),
            health = emptyList()
        )

        viewModel.search.searchAllStreaming("Echo", setOf(StreamingMediaType.TRACK), pageSize = 20)
        waitUntil { !viewModel.streaming.value.loading }

        assertEquals(2, viewModel.streaming.value.searchResult?.tracks?.size)
    }

    @Test
    fun searchAllProvidersKeepsDifferentVersionSeparateAndMergesItsMatchingSources() = runTest {
        val neteaseProvider = FakeProvider(
            descriptor = descriptor(StreamingProviderName.NETEASE, "NetEase"),
            searchResult = searchResult(
                StreamingProviderName.NETEASE,
                "Echo",
                1,
                listOf(
                    streamingTrack("netease-echo").copy(
                        title = "Echo",
                        artist = "Artist",
                        durationMs = 120_000L
                    )
                )
            )
        )
        val qqProvider = FakeProvider(
            descriptor = descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music"),
            searchResult = searchResult(
                StreamingProviderName.QQ_MUSIC,
                "Echo",
                1,
                listOf(
                    streamingTrack("qq-echo").copy(
                        provider = StreamingProviderName.QQ_MUSIC,
                        title = "Echo",
                        artist = "Artist",
                        durationMs = 300_000L
                    )
                )
            )
        )
        val kugouProvider = FakeProvider(
            descriptor = descriptor(StreamingProviderName.KUGOU, "KuGou"),
            searchResult = searchResult(
                StreamingProviderName.KUGOU,
                "Echo",
                1,
                listOf(
                    streamingTrack("kugou-echo").copy(
                        provider = StreamingProviderName.KUGOU,
                        title = "Echo",
                        artist = "Artist",
                        durationMs = 301_000L
                    )
                )
            )
        )
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(
            StreamingRepository(
                RegistryStreamingGateway(
                    StreamingProviderRegistry(listOf(neteaseProvider, qqProvider, kugouProvider))
                )
            )
        )
        viewModel.auth.updateProviders(
            providers = listOf(
                descriptor(StreamingProviderName.NETEASE, "NetEase"),
                descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music"),
                descriptor(StreamingProviderName.KUGOU, "KuGou")
            ),
            capabilities = listOf(
                capability(StreamingProviderName.NETEASE),
                capability(StreamingProviderName.QQ_MUSIC),
                capability(StreamingProviderName.KUGOU)
            ),
            health = emptyList()
        )

        viewModel.search.searchAllStreaming("Echo", setOf(StreamingMediaType.TRACK), pageSize = 20)
        waitUntil { !viewModel.streaming.value.loading }

        val tracks = viewModel.streaming.value.searchResult?.tracks.orEmpty()
        // 120 秒版本保持独立；300/301 秒的两个音源合并成另一条。
        assertEquals(2, tracks.size)
        assertEquals(listOf(1, 2), tracks.map { it.playbackSourceCount }.sorted())
    }

    @Test
    fun providerSelectionPrefersConnectedNonMockProvider() {
        val viewModel = StreamingViewModel()
        viewModel.auth.updateAuthState(
            StreamingProviderName.QQ_MUSIC,
            StreamingAuthState(connected = true)
        )

        viewModel.auth.updateProviders(
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

        viewModel.auth.refreshProviders().join()

        assertEquals(1, source.currentCalls)
        assertEquals(StreamingProviderName.NETEASE, viewModel.streaming.value.providers.single().name)

        viewModel.configureStreamingRepository().join()
        viewModel.auth.refreshProviders().join()

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

        viewModel.search.searchStreaming(
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
        viewModel.search.updateStreamingSearchResult(
            searchResult(StreamingProviderName.NETEASE, "echo", 1, listOf(streamingTrack("song-1")))
        )

        viewModel.search.searchNextStreamingPage()
        waitUntil {
            viewModel.streaming.value.searchResult?.tracks?.map { it.providerTrackId } == listOf("song-1", "song-2")
        }

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

        viewModel.auth.startAuth(
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

        viewModel.auth.completeAuth(
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

        viewModel.auth.signOut(StreamingProviderName.NETEASE).join()
        waitUntil { !viewModel.streaming.value.loading }

        assertEquals(1, provider.signOutCalls.get())
        assertFalse(viewModel.streaming.value.authStates[StreamingProviderName.NETEASE]?.connected == true)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun resolveStreamingPlaybackTrackStoresSourceAndTrack() = runTest {
        val provider = FakeProvider()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(repository(provider))
        val metadata = streamingTrack("play-1")

        viewModel.playbackResolution.resolveStreamingPlaybackTrack(
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
        viewModel.playbackResolution.bindPlaybackCoordinator(ResolveStreamingPlaybackUseCase(), taskQueue)

        val scheduled = viewModel.playbackResolution.preResolveNextStreamingTrack(
            snapshot = playbackSnapshot(currentTrack = local, currentIndex = 0, queueSize = 2, playing = true),
            queue = listOf(local, next),
            quality = StreamingAudioQuality.HIGH
        ) { oldTrackId, track -> resolved += oldTrackId to track }

        assertTrue(scheduled)
        assertEquals(1, taskQueue.nextUrlResolveCount)
        advanceUntilIdle()
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
        viewModel.playbackResolution.bindPlaybackCoordinator(ResolveStreamingPlaybackUseCase(), taskQueue)

        val scheduled = viewModel.playbackResolution.resolveStreamingTrackListForPlayback(
            tracks = listOf(local, unresolved),
            index = 1,
            quality = StreamingAudioQuality.HIGH
        ) { result -> resolved += result }

        assertTrue(scheduled)
        assertEquals(1, taskQueue.currentUrlResolveCount)
        advanceUntilIdle()
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
    fun failedNextTrackPreResolveDoesNotPublishForegroundError() = runTest {
        val provider = FakeProvider().apply {
            playbackResolveError = IllegalStateException("LX prefetch failed")
        }
        val viewModel = StreamingViewModel()
        val taskQueue = FakeStreamingPlaybackTaskQueue()
        val local = localTrack(id = 1L)
        val next = streamingPlaceholderTrack(id = 2L, providerTrackId = "next-2")
        val resolved = mutableListOf<Pair<Long, Track?>>()
        viewModel.bindStreamingRepository(repository(provider))
        viewModel.playbackResolution.bindPlaybackCoordinator(ResolveStreamingPlaybackUseCase(), taskQueue)

        val scheduled = viewModel.playbackResolution.preResolveNextStreamingTrack(
            snapshot = playbackSnapshot(currentTrack = local, currentIndex = 0, queueSize = 2, playing = true),
            queue = listOf(local, next),
            quality = StreamingAudioQuality.HIGH
        ) { oldTrackId, track -> resolved += oldTrackId to track }

        assertTrue(scheduled)
        advanceUntilIdle()
        waitUntil { resolved.isNotEmpty() }
        assertEquals(listOf(2L to null), resolved)
        assertFalse(viewModel.streaming.value.loading)
        assertNull(viewModel.streaming.value.errorMessage)
    }

    @Test
    fun newForegroundTrackCancelsPreviousInFlightResolve() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val provider = FakeProvider()
        val gate = CompletableDeferred<Unit>()
        provider.playbackResolveGate = gate
        val viewModel = StreamingViewModel()
        viewModel.bindIoDispatcherForTest(dispatcher)
        viewModel.bindStreamingRepository(repository(provider))
        viewModel.playbackResolution.bindPlaybackCoordinator(
            ResolveStreamingPlaybackUseCase(),
            FakeStreamingPlaybackTaskQueue()
        )
        val firstResults = mutableListOf<ResolvedStreamingTrackList?>()
        val secondResults = mutableListOf<ResolvedStreamingTrackList?>()

        assertTrue(
            viewModel.playbackResolution.resolveStreamingTrackListForPlayback(
                listOf(streamingPlaceholderTrack(31L, "first-31")),
                0,
                StreamingAudioQuality.HIGH
            ) { firstResults += it }
        )
        waitUntil { provider.playbackRequests.any { it.providerTrackId == "first-31" } }

        assertTrue(
            viewModel.playbackResolution.resolveStreamingTrackListForPlayback(
                listOf(streamingPlaceholderTrack(32L, "second-32")),
                0,
                StreamingAudioQuality.HIGH
            ) { secondResults += it }
        )
        waitUntil { provider.playbackRequests.any { it.providerTrackId == "second-32" } }
        gate.complete(Unit)
        advanceUntilIdle()
        waitUntil { secondResults.isNotEmpty() }

        assertTrue(firstResults.isEmpty())
        assertEquals("Streaming 32", secondResults.single()?.tracks?.single()?.title)
    }

    @Test
    fun preResolveStreamingQueueWindowBatchResolvesUpcomingTracksInOneCallback() = runTest {
        // Unify viewModelScope (Main), the IO async children, and runTest on a single
        // scheduler so awaitAll fully drains before job.join() returns. Mixing the class
        // rule's Main scheduler with runTest's scheduler dropped one of the two resolves.
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val provider = FakeProvider()
        val viewModel = StreamingViewModel()
        viewModel.bindIoDispatcherForTest(dispatcher)
        val current = localTrack(id = 1L)
        val next = streamingPlaceholderTrack(id = 2L, providerTrackId = "next-2")
        val third = streamingPlaceholderTrack(id = 3L, providerTrackId = "next-3")
        val fourth = streamingPlaceholderTrack(id = 4L, providerTrackId = "next-4")
        val resolved = mutableListOf<Map<Long, Track>>()
        viewModel.bindStreamingRepository(repository(provider))

        val job = viewModel.playbackResolution.preResolveStreamingQueueWindowBatch(
            snapshot = playbackSnapshot(currentTrack = current, currentIndex = 0, queueSize = 4, playing = true),
            queue = listOf(current, next, third, fourth),
            quality = StreamingAudioQuality.HIGH,
            maxCount = 2
        ) { resolvedTracks -> resolved += resolvedTracks }

        job?.join()

        assertEquals(
            setOf(
                StreamingPlaybackRequest(StreamingProviderName.NETEASE, "next-3", StreamingAudioQuality.HIGH),
                StreamingPlaybackRequest(StreamingProviderName.NETEASE, "next-4", StreamingAudioQuality.HIGH)
            ),
            provider.playbackRequests.toSet()
        )
        assertEquals(1, resolved.size)
        assertEquals(listOf(3L, 4L), resolved.single().keys.toList())
        assertEquals(listOf("Streaming 3", "Streaming 4"), resolved.single().values.map { it.title })
    }

    @Test
    fun preResolveStreamingQueueWindowDoesNotDuplicateInFlightTargets() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val provider = FakeProvider()
        val gate = CompletableDeferred<Unit>()
        provider.playbackResolveGate = gate
        val viewModel = StreamingViewModel()
        viewModel.bindIoDispatcherForTest(dispatcher)
        val current = localTrack(id = 1L)
        val next = streamingPlaceholderTrack(id = 2L, providerTrackId = "next-2")
        val third = streamingPlaceholderTrack(id = 3L, providerTrackId = "next-3")
        val resolved = mutableListOf<Pair<Long, Track?>>()
        viewModel.bindStreamingRepository(repository(provider))
        val snapshot = playbackSnapshot(
            currentTrack = current,
            currentIndex = 0,
            queueSize = 3,
            playing = true
        )
        val queue = listOf(current, next, third)

        val first = viewModel.playbackResolution.preResolveStreamingQueueWindow(
            snapshot = snapshot,
            queue = queue,
            quality = StreamingAudioQuality.HIGH,
            maxCount = 1
        ) { oldTrackId, track -> resolved += oldTrackId to track }
        val repeated = viewModel.playbackResolution.preResolveStreamingQueueWindow(
            snapshot = snapshot,
            queue = queue,
            quality = StreamingAudioQuality.HIGH,
            maxCount = 1
        ) { oldTrackId, track -> resolved += oldTrackId to track }

        assertNull(repeated)

        gate.complete(Unit)
        first?.join()

        assertEquals(
            listOf(StreamingPlaybackRequest(StreamingProviderName.NETEASE, "next-3", StreamingAudioQuality.HIGH)),
            provider.playbackRequests.toList()
        )
        assertEquals(listOf(3L), resolved.map { it.first })
    }

    @Test
    fun foregroundPlaybackSharesInFlightQueuePreResolveForSameTrack() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val provider = FakeProvider()
        val gate = CompletableDeferred<Unit>()
        provider.playbackResolveGate = gate
        val viewModel = StreamingViewModel()
        viewModel.bindIoDispatcherForTest(dispatcher)
        val current = localTrack(id = 1L)
        val next = localTrack(id = 2L)
        val target = streamingPlaceholderTrack(id = 3L, providerTrackId = "shared-3")
        val playbackResults = mutableListOf<Track?>()
        viewModel.bindStreamingRepository(repository(provider))

        val background = viewModel.playbackResolution.preResolveStreamingQueueWindowBatch(
            snapshot = playbackSnapshot(
                currentTrack = current,
                currentIndex = 0,
                queueSize = 3,
                playing = true
            ),
            queue = listOf(current, next, target),
            quality = StreamingAudioQuality.HIGH,
            maxCount = 1
        ) { }
        waitUntil { provider.playbackRequests.size == 1 }

        val foreground = viewModel.playbackResolution.resolveStreamingTrackForPlayback(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "shared-3",
            metadata = ResolveStreamingPlaybackUseCase().metadataFor(
                target,
                StreamingProviderName.NETEASE,
                "shared-3"
            ),
            quality = StreamingAudioQuality.HIGH
        ) { resolved -> playbackResults += resolved }

        assertEquals(1, provider.playbackRequests.size)
        gate.complete(Unit)
        background?.join()
        foreground.join()

        assertEquals(
            listOf(StreamingPlaybackRequest(StreamingProviderName.NETEASE, "shared-3", StreamingAudioQuality.HIGH)),
            provider.playbackRequests.toList()
        )
        assertEquals(listOf("Streaming 3"), playbackResults.mapNotNull { it?.title })
    }

    @Test
    fun prepareCurrentStreamingQueueResolveTargetSelectsCurrentPlaceholder() {
        val viewModel = StreamingViewModel()
        val local = localTrack(id = 1L)
        val current = streamingPlaceholderTrack(id = 2L, providerTrackId = "play-2")
        val next = streamingPlaceholderTrack(id = 3L, providerTrackId = "play-3")

        val target = viewModel.playbackResolution.prepareCurrentStreamingQueueResolveTarget(
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
            viewModel.playbackResolution.prepareCurrentStreamingQueueResolveTarget(
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

        viewModel.playlists.loadUserPlaylists(StreamingProviderName.NETEASE).join()

        val state = viewModel.streaming.value
        assertEquals(listOf("playlist-1"), state.userPlaylists.map { it.providerPlaylistId })
        assertEquals(StreamingProviderName.NETEASE, state.selectedProvider)
        assertFalse(state.userPlaylistsLoading)
    }

    @Test
    fun fetchLikedTracksDeliversTracksAndClearsLoading() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.userLikedTracksResult = listOf(streamingTrack("liked-1"))
        gateway.heartbeatRecommendationsResult = listOf(streamingTrack("heart-1"))
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        val liked = mutableListOf<List<StreamingTrack>>()

        viewModel.playlists.fetchUserLikedTracks(StreamingProviderName.NETEASE) { tracks -> liked += tracks }.join()

        assertEquals(listOf("liked-1"), liked.single().map { it.providerTrackId })
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

        viewModel.playlists.fetchStreamingPlaylistTracks(StreamingProviderName.NETEASE, "playlist-1") { name, tracks ->
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
    fun fetchStreamingPlaylistTracksStopsAtLocalPaginationCap() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.playlistAlwaysHasMoreWithoutTotal = true
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        val resolvedTrackIds = mutableListOf<List<String>>()

        viewModel.playlists.fetchStreamingPlaylistTracks(StreamingProviderName.NETEASE, "playlist-loop") { _, tracks ->
            resolvedTrackIds += tracks.map { it.providerTrackId }
        }.join()

        assertEquals((1..STREAMING_PLAYLIST_MAX_PAGES).toList(), gateway.playlistRequests.map { it.page })
        assertEquals(
            (1..STREAMING_PLAYLIST_MAX_PAGES).map { page -> "loop-$page" },
            resolvedTrackIds.single()
        )
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

        viewModel.playlists.fetchStreamingPlaylistTracks(StreamingProviderName.NETEASE, "playlist-42") { name, _ ->
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
        viewModel.playlists.bindLocalPlaylistOperations(operations)

        viewModel.playlists.importStreamingPlaylistToLocal(
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
        viewModel.playlists.bindLocalPlaylistOperations(operations)

        viewModel.playlists.importStreamingPlaylistToLocal(StreamingProviderName.NETEASE, "playlist-empty") { result ->
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
        viewModel.playlists.bindLocalPlaylistOperations(operations)

        viewModel.playlists.importStreamingLikedTracksToLocal(StreamingProviderName.NETEASE, "Liked from NetEase") { result ->
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
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        val link = StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = 42L,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "",
            lastSyncMs = 0L
        )

        viewModel.playlists.syncStreamingPlaylistToLocal(link) { result -> synced += result }.join()

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
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        val link = StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = 15L,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-15",
            lastSyncMs = 0L
        )

        viewModel.playlists.syncStreamingPlaylistToLocal(link) { result -> synced += result }.join()

        assertEquals(listOf(1, 2), gateway.playlistRequests.map { it.page })
        assertEquals(listOf(15L), operations.syncPlaylistIds)
        assertEquals(listOf("playlist-15"), operations.syncProviderPlaylistIds)
        assertEquals(listOf(listOf("track-1", "track-2", "track-3")), operations.syncTrackIds)
        assertEquals(3, synced.single().syncedCount)
        assertFalse(synced.single().empty)
        assertFalse(viewModel.streaming.value.loading)
    }

    @Test
    fun syncStreamingPlaylistPushesLinkedLocalMirrorToRemoteProvider() = runTest {
        val provider = FakeProvider().apply {
            searchResult = StreamingSearchResult(
                provider = StreamingProviderName.NETEASE,
                query = "New song Artist",
                page = 1,
                pageSize = 5,
                tracks = listOf(
                    streamingTrack("new").copy(title = "New song", artist = "Artist")
                )
            )
        }
        val gateway = FakeGateway(provider).apply {
            playlistTitle = "Old title"
            playlistTrackIds = listOf("old")
        }
        val operations = FakeStreamingLocalPlaylistOperations().apply {
            localSnapshot = StreamingLocalPlaylistSnapshot(
                playlistId = 15L,
                playlistName = "Local mirror",
                tracks = listOf(
                    Track(15L, "New song", "Artist", "Album", 180_000L, null, "")
                )
            )
        }
        val synced = mutableListOf<StreamingLocalPlaylistSyncResult>()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        val link = StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = 15L,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-15",
            lastSyncMs = 0L,
            direction = StreamingPlaylistSyncDirection.LOCAL_TO_REMOTE
        )

        viewModel.playlists.syncStreamingPlaylist(link) { result -> synced += result }.join()

        assertEquals(listOf("Local mirror"), gateway.renamedPlaylists)
        assertEquals(listOf(listOf("new")), gateway.addedTrackIds)
        assertEquals(listOf(listOf("old")), gateway.removedTrackIds)
        assertEquals(listOf(listOf("new")), gateway.reorderedTrackIds)
        assertEquals(listOf(15L), operations.baselinePlaylistIds)
        assertEquals(1, synced.single().syncedCount)
        assertFalse(synced.single().empty)
    }

    @Test
    fun localMirrorSyncDoesNotChangeRemoteWhenAnyTrackCannotBeMatched() = runTest {
        val gateway = FakeGateway(FakeProvider()).apply {
            playlistTrackIds = listOf("keep")
        }
        val operations = FakeStreamingLocalPlaylistOperations().apply {
            localSnapshot = StreamingLocalPlaylistSnapshot(
                playlistId = 15L,
                playlistName = "Local mirror",
                tracks = listOf(
                    Track(15L, "Missing song", "Artist", "Album", 180_000L, null, "")
                )
            )
        }
        val synced = mutableListOf<StreamingLocalPlaylistSyncResult>()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        val link = StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = 15L,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-15",
            lastSyncMs = 0L,
            direction = StreamingPlaylistSyncDirection.LOCAL_TO_REMOTE
        )

        viewModel.playlists.syncStreamingPlaylist(link) { result -> synced += result }.join()

        assertTrue(gateway.renamedPlaylists.isEmpty())
        assertTrue(gateway.addedTrackIds.isEmpty())
        assertTrue(gateway.removedTrackIds.isEmpty())
        assertTrue(gateway.reorderedTrackIds.isEmpty())
        assertTrue(operations.markedPlaylistIds.isEmpty())
        assertTrue(synced.single().empty)
        assertTrue(synced.single().errorMessage.isNotBlank())
    }

    @Test
    fun importAccountPlaylistsToLocalRefreshesExistingLinkedPlaylist() = runTest {
        val provider = FakeProvider()
        val gateway = FakeGateway(provider)
        gateway.playlistTrackIds = listOf("track-1", "track-2")
        val link = StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = 88L,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-88",
            lastSyncMs = 0L
        )
        val operations = FakeStreamingLocalPlaylistOperations().apply {
            linkedRemotePlaylists["netease:playlist-88"] = link
        }
        val imported = mutableListOf<StreamingAccountPlaylistImportResult>()
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        val playlists = listOf(
            StreamingPlaylist(
                provider = StreamingProviderName.NETEASE,
                providerPlaylistId = "playlist-88",
                title = "Cloud List"
            )
        )

        viewModel.playlists.importAccountPlaylistsToLocal(
            StreamingProviderName.NETEASE,
            playlists
        ) { result -> imported += result }.join()

        assertEquals(emptyList<String>(), operations.importPlaylistNames)
        assertEquals(listOf(88L), operations.syncPlaylistIds)
        assertEquals(listOf("playlist-88"), operations.syncProviderPlaylistIds)
        assertEquals(listOf(listOf("track-1", "track-2")), operations.syncTrackIds)
        assertEquals(1, imported.single().importedPlaylistCount)
        assertEquals(2, imported.single().importedTrackCount)
        assertEquals(0, imported.single().failedCount)
        assertFalse(viewModel.streaming.value.userPlaylistsLoading)
    }

    @Test
    fun ensureStreamingLoginPlaylistDelegatesToBoundOperations() = runTest {
        val operations = FakeStreamingLocalPlaylistOperations()
        val ensured = mutableListOf<StreamingLoginPlaylistResult>()
        val viewModel = StreamingViewModel()
        viewModel.playlists.bindLocalPlaylistOperations(operations)

        viewModel.playlists.ensureStreamingLoginPlaylist(
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
        viewModel.playlists.bindLocalPlaylistOperations(operations)
        viewModel.auth.updateProviders(listOf(descriptor(StreamingProviderName.NETEASE, "NetEase")))

        val tracks = listOf(localTrack(1L), localTrack(2L))
        val export = viewModel.playlists.prepareStreamingPlaylistExportRequest("Road Trip", tracks, AppLanguage.MODE_ENGLISH)
        val favorites = viewModel.playlists.prepareStreamingFavoritesExportRequest(tracks, AppLanguage.MODE_ENGLISH)
        val importStart = viewModel.playlists.prepareStreamingPlaylistImportStartRequest(
            "playlist-42",
            StreamingProviderName.NETEASE,
            AppLanguage.MODE_ENGLISH
        )
        val loginRequest = viewModel.playlists.prepareStreamingLoginPlaylistRequest(
            StreamingProviderName.NETEASE,
            AppLanguage.MODE_ENGLISH
        )
        val syncStart = viewModel.playlists.prepareStreamingPlaylistSyncStartRequest(42L, AppLanguage.MODE_ENGLISH)
        val importPresentation = viewModel.playlists.prepareStreamingPlaylistImportPresentation(
            StreamingLocalPlaylistImportResult("Remote", 3, empty = false),
            AppLanguage.MODE_ENGLISH
        )
        val syncPresentation = viewModel.playlists.prepareStreamingPlaylistSyncPresentation(
            StreamingLocalPlaylistSyncResult(42L, 2, empty = false),
            AppLanguage.MODE_ENGLISH
        )
        val loginPresentation = viewModel.playlists.prepareStreamingLoginPlaylistPresentation(
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
    fun prepareStreamingPlaylistSyncStartRequestReturnsNotLinkedWhenPlaylistWasDeleted() {
        val viewModel = StreamingViewModel()
        val operations = FakeStreamingLocalPlaylistOperations().apply {
            playlistExistsResult = false
            linkedPlaylistResult = StreamingPlaylistSyncStore.LinkedPlaylist(
                localPlaylistId = 42L,
                provider = StreamingProviderName.NETEASE,
                providerPlaylistId = "playlist-42",
                lastSyncMs = 0L
            )
        }
        viewModel.playlists.bindLocalPlaylistOperations(operations)

        val syncStart = viewModel.playlists.prepareStreamingPlaylistSyncStartRequest(42L, AppLanguage.MODE_ENGLISH)

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.not.linked"), syncStart?.status)
        assertEquals(false, syncStart?.valid)
        assertEquals(null, syncStart?.link)
    }

    @Test
    fun streamingProviderTrackIdLoadsAndSavesThroughBoundStore() = runTest {
        val viewModel = StreamingViewModel()
        val track = localTrack(id = 42L)
        val store = FakeStreamingTrackMatchStore()
        store.loadedProviderTrackId = " cached-42 "
        val loaded = mutableListOf<String>()
        viewModel.playbackResolution.bindTrackMatchStore(store)

        viewModel.playbackResolution.loadStreamingProviderTrackId(track, StreamingProviderName.NETEASE) { providerTrackId ->
            loaded += providerTrackId
        }.join()
        val directLoaded = viewModel.playbackResolution.streamingProviderTrackIdFor(track, StreamingProviderName.NETEASE)
        viewModel.playbackResolution.saveStreamingProviderTrackId(track, StreamingProviderName.NETEASE, " resolved-42 ").join()
        viewModel.playbackResolution.saveStreamingProviderTrackId(track, StreamingProviderName.NETEASE, " ").join()

        assertEquals(listOf(" cached-42 "), loaded)
        assertEquals(" cached-42 ", directLoaded)
        assertEquals(listOf("load:42", "load:42", "save:42:resolved-42"), store.events)
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

    private class FakeProvider(
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
            ),
        var searchResult: StreamingSearchResult = StreamingSearchResult(
            provider = descriptor.name,
            query = "",
            page = 1,
            pageSize = 20
        )
    ) : StreamingProvider {
        // resolvePlaybackTrack runs on Dispatchers.IO and may resolve multiple
        // candidates concurrently, so the recorded calls must be thread-safe —
        // a plain ArrayList loses concurrent add() calls (the next-3 race).
        val searchRequests: MutableList<StreamingSearchRequest> = java.util.concurrent.CopyOnWriteArrayList()
        val playbackRequests: MutableList<StreamingPlaybackRequest> = java.util.concurrent.CopyOnWriteArrayList()
        val startAuthRedirectUris: MutableList<String?> = java.util.concurrent.CopyOnWriteArrayList()
        val completeAuthCalls: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()
        val signOutCalls = java.util.concurrent.atomic.AtomicInteger(0)
        var playbackResolveGate: CompletableDeferred<Unit>? = null
        var playbackResolveError: Throwable? = null
        var startAuthResult: StreamingAuthResult = StreamingAuthResult(
            StreamingProviderName.NETEASE,
            StreamingAuthState()
        )
        var completeAuthState: StreamingAuthState = StreamingAuthState()
        var signOutState: StreamingAuthState = StreamingAuthState()
        var currentAuthState: StreamingAuthState = StreamingAuthState()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
            searchRequests += request
            return searchResult
        }

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
            playbackRequests += request
            playbackResolveGate?.await()
            playbackResolveError?.let { throw it }
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
            signOutCalls.incrementAndGet()
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
        var playlistAlwaysHasMoreWithoutTotal: Boolean = false
        val renamedPlaylists = mutableListOf<String>()
        val addedTrackIds = mutableListOf<List<String>>()
        val removedTrackIds = mutableListOf<List<String>>()
        val reorderedTrackIds = mutableListOf<List<String>>()

        override suspend fun providers(): List<StreamingProviderDescriptor> = listOf(provider.descriptor)

        override suspend fun providerCapabilities(): List<StreamingProviderCapability> = emptyList()

        override suspend fun providersHealth(): List<app.yukine.streaming.StreamingProviderHealth> = emptyList()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
            provider.search(request)

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
            playlistRequests += request
            if (playlistAlwaysHasMoreWithoutTotal) {
                val trackId = "loop-${request.page}"
                return StreamingPlaylistDetail(
                    provider = provider.descriptor.name,
                    providerPlaylistId = request.providerPlaylistId,
                    playlist = StreamingPlaylist(
                        provider = provider.descriptor.name,
                        providerPlaylistId = request.providerPlaylistId,
                        title = playlistTitle.orEmpty(),
                        trackCount = 0
                    ),
                    tracks = listOf(
                        StreamingTrack(
                            provider = provider.descriptor.name,
                            providerTrackId = trackId,
                            title = "Track $trackId",
                            artist = "Artist",
                            album = "Album",
                            durationMs = 1_000L
                        )
                    ),
                    total = null,
                    page = request.page,
                    pageSize = request.pageSize,
                    hasMore = true
                )
            }
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

        override suspend fun renameUserPlaylist(
            provider: StreamingProviderName,
            providerPlaylistId: String,
            title: String
        ) {
            renamedPlaylists += title
        }

        override suspend fun mutateUserPlaylistTracks(
            provider: StreamingProviderName,
            providerPlaylistId: String,
            providerTrackIds: List<String>,
            add: Boolean
        ) {
            if (add) {
                addedTrackIds += providerTrackIds
                playlistTrackIds = (playlistTrackIds + providerTrackIds).distinct()
            } else {
                removedTrackIds += providerTrackIds
                playlistTrackIds = playlistTrackIds.filterNot(providerTrackIds.toSet()::contains)
            }
        }

        override suspend fun reorderUserPlaylistTracks(
            provider: StreamingProviderName,
            providerPlaylistId: String,
            orderedProviderTrackIds: List<String>
        ) {
            reorderedTrackIds += orderedProviderTrackIds
            playlistTrackIds = orderedProviderTrackIds
        }
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
        var playlistExistsResult = true
        var linkedPlaylistResult: StreamingPlaylistSyncStore.LinkedPlaylist? = null
        val linkedRemotePlaylists = mutableMapOf<String, StreamingPlaylistSyncStore.LinkedPlaylist>()
        var localSnapshot: StreamingLocalPlaylistSnapshot? = null
        val markedPlaylistIds = mutableListOf<Long>()
        val baselinePlaylistIds = mutableListOf<Long>()

        override fun playlistExists(localPlaylistId: Long): Boolean = playlistExistsResult

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

        override fun linkedPlaylist(
            provider: StreamingProviderName,
            providerPlaylistId: String
        ): StreamingPlaylistSyncStore.LinkedPlaylist? =
            linkedRemotePlaylists["${provider.wireName}:$providerPlaylistId"]

        override fun localPlaylistSnapshot(localPlaylistId: Long): StreamingLocalPlaylistSnapshot? =
            localSnapshot?.takeIf { it.playlistId == localPlaylistId }

        override fun markPlaylistSynced(localPlaylistId: Long) {
            markedPlaylistIds += localPlaylistId
        }

        override fun updatePlaylistSyncBaseline(
            localPlaylistId: Long,
            snapshot: app.yukine.streaming.StreamingPlaylistSyncSnapshot,
            localUpdatedAtMs: Long?,
            remoteUpdatedAtMs: Long?,
            remoteObservedChangeAtMs: Long?
        ) {
            baselinePlaylistIds += localPlaylistId
        }
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

        override suspend fun deleteSearchForProvider(provider: String): Int = 0

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

        override suspend fun deletePlaybackForProvider(provider: String): Int = 0

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
