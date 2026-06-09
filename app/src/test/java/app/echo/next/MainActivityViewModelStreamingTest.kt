package app.echo.next

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.echo.next.model.Track
import app.echo.next.streaming.RegistryStreamingGateway
import app.echo.next.streaming.StreamingAudioQuality
import app.echo.next.streaming.StreamingAuthState
import app.echo.next.streaming.StreamingPlaybackRequest
import app.echo.next.streaming.StreamingPlaybackSource
import app.echo.next.streaming.StreamingProvider
import app.echo.next.streaming.StreamingProviderCapabilities
import app.echo.next.streaming.StreamingProviderDescriptor
import app.echo.next.streaming.StreamingMediaType
import app.echo.next.streaming.StreamingPlaylist
import app.echo.next.streaming.StreamingPlaylistDetail
import app.echo.next.streaming.StreamingPlaylistRequest
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingProviderRegistry
import app.echo.next.streaming.StreamingRepository
import app.echo.next.streaming.StreamingSearchRequest
import app.echo.next.streaming.StreamingSearchResult
import app.echo.next.streaming.StreamingTrack
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
        var searchTracks: List<StreamingTrack> = emptyList()

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
            return StreamingPlaybackSource(
                provider = descriptor.name,
                providerTrackId = request.providerTrackId,
                url = "https://example.test/${request.providerTrackId}.mp3"
            )
        }

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
            playlistRequests += request
            val allTracks = listOf("track-1", "track-2", "track-3")
            val tracks = if (request.page == 1) allTracks.take(2) else allTracks.drop(2)
            return StreamingPlaylistDetail(
                provider = descriptor.name,
                providerPlaylistId = request.providerPlaylistId,
                playlist = StreamingPlaylist(
                    provider = descriptor.name,
                    providerPlaylistId = request.providerPlaylistId,
                    title = "Remote Playlist",
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
