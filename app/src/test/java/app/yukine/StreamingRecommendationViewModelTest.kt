package app.yukine

import app.yukine.streaming.StreamingAuthRequest
import app.yukine.streaming.StreamingAuthResult
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingGateway
import app.yukine.streaming.StreamingHeartbeatRequest
import app.yukine.streaming.StreamingPlaybackRequest
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingPlaylistDetail
import app.yukine.streaming.StreamingPlaylistRequest
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderCapabilities
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingProviderStatus
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingSearchRequest
import app.yukine.streaming.StreamingSearchResult
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StreamingRecommendationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun unavailableDailyProviderReportsEmptyStatus() {
        val viewModel = StreamingRecommendationViewModel(FakeRepositorySource(FakeDailyGateway()))
        val statuses = mutableListOf<String>()
        val presentations = mutableListOf<StreamingRecommendationPresentation>()

        viewModel.playDailyRecommendations(
            StreamingProviderName.SPOTIFY,
            AppLanguage.MODE_ENGLISH,
            statuses::add,
            presentations::add
        )

        assertEquals(listOf("No daily recommendations (login required?)"), statuses)
        assertTrue(presentations.isEmpty())
    }

    @Test
    fun dailyRecommendationFetchBuildsPlayablePresentation() = runTest {
        val gateway = FakeDailyGateway()
        gateway.dailyTracks = listOf(streamingTrack("daily-1"))
        val viewModel = StreamingRecommendationViewModel(FakeRepositorySource(gateway))
        viewModel.updateProviders(listOf(neteaseProvider()))
        val statuses = mutableListOf<String>()
        val presentations = mutableListOf<StreamingRecommendationPresentation>()

        viewModel.playDailyRecommendations(
            StreamingProviderName.NETEASE,
            AppLanguage.MODE_ENGLISH,
            statuses::add,
            presentations::add
        ).join()

        assertEquals(listOf(StreamingProviderName.NETEASE), gateway.dailyProviders)
        assertEquals(listOf("Loading daily recommendations"), statuses)
        val presentation = presentations.single()
        assertFalse(presentation.empty)
        assertEquals("Daily recommendations (1)", presentation.readyStatus)
        assertEquals("Daily recommendations", presentation.title)
        assertEquals("daily-1", presentation.tracks.single().dataPath.substringAfterLast(':'))
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun typedDailyActionRunsThroughRecommendationViewModel() = runTest {
        val gateway = FakeDailyGateway()
        gateway.dailyTracks = listOf(streamingTrack("daily-action"))
        val viewModel = StreamingRecommendationViewModel(FakeRepositorySource(gateway))
        viewModel.updateProviders(listOf(neteaseProvider()))
        val calls = mutableListOf<String>()

        viewModel.onAction(
            RecommendationAction.PlayDaily(StreamingProviderName.NETEASE),
            AppLanguage.MODE_ENGLISH,
            callbacks(calls)
        ).join()

        assertEquals(listOf(StreamingProviderName.NETEASE), gateway.dailyProviders)
        assertEquals(
            listOf(
                "status:Loading daily recommendations",
                "daily:Daily recommendations:1"
            ),
            calls
        )
    }

    @Test
    fun typedHeartbeatActionRunsThroughRecommendationViewModelWithSeed() = runTest {
        val gateway = FakeDailyGateway()
        gateway.heartbeatTracks = listOf(streamingTrack("heart-action"))
        val viewModel = StreamingRecommendationViewModel(FakeRepositorySource(gateway))
        viewModel.updateProviders(listOf(neteaseProvider()))
        val calls = mutableListOf<String>()

        viewModel.onAction(
            RecommendationAction.PlayHeartbeat(StreamingProviderName.NETEASE),
            AppLanguage.MODE_ENGLISH,
            callbacks(calls)
        ).join()

        assertEquals(1, gateway.heartbeatRequests.size)
        assertEquals("seed", gateway.heartbeatRequests.single().providerTrackId)
        assertEquals("seed", gateway.heartbeatRequests.single().providerPlaylistId)
        assertEquals(
            listOf(
                "status:Loading heartbeat recommendations",
                "seed:netease",
                "heartbeat:Playing heartbeat recommendations:1"
            ),
            calls
        )
    }

    @Test
    fun failedDailyRecommendationFetchReturnsEmptyPresentation() = runTest {
        val gateway = FakeDailyGateway()
        gateway.failDaily = true
        val viewModel = StreamingRecommendationViewModel(FakeRepositorySource(gateway))
        viewModel.updateProviders(listOf(neteaseProvider()))
        val presentations = mutableListOf<StreamingRecommendationPresentation>()

        viewModel.playDailyRecommendations(
            StreamingProviderName.NETEASE,
            AppLanguage.MODE_ENGLISH,
            {},
            presentations::add
        ).join()

        assertTrue(presentations.single().empty)
        assertEquals("No daily recommendations (login required?)", presentations.single().emptyStatus)
        assertEquals("daily failed", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun heartbeatRecommendationFetchBuildsProviderRequestAndClearsLoading() = runTest {
        val gateway = FakeDailyGateway()
        gateway.heartbeatTracks = listOf(streamingTrack("heart-1"))
        val viewModel = StreamingRecommendationViewModel(FakeRepositorySource(gateway))
        val heartbeat = mutableListOf<List<StreamingTrack>>()

        viewModel.fetchHeartbeatRecommendations(
            StreamingProviderName.NETEASE,
            providerTrackId = "seed-1",
            providerPlaylistId = "playlist-1"
        ) { tracks -> heartbeat += tracks }.join()

        assertEquals(listOf("heart-1"), heartbeat.single().map { it.providerTrackId })
        assertEquals(1, gateway.heartbeatRequests.size)
        assertEquals(60, gateway.heartbeatRequests.single().count)
        assertEquals("seed-1", gateway.heartbeatRequests.single().providerTrackId)
        assertEquals("playlist-1", gateway.heartbeatRequests.single().providerPlaylistId)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun heartbeatSeedResolutionSearchesAndSavesFirstMatch() = runTest {
        val gateway = FakeDailyGateway()
        gateway.searchTracks = listOf(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "song-88",
                title = "Local 88",
                artist = "Artist"
            )
        )
        val store = FakeStreamingTrackMatchStore()
        val viewModel = StreamingRecommendationViewModel(FakeRepositorySource(gateway))
        viewModel.bindStreamingTrackMatchStore(store)
        val resolvedTrackIds = mutableListOf<String>()

        viewModel.resolveHeartbeatRecommendationSeed(
            StreamingProviderName.NETEASE,
            listOf(localTrack(id = 88L))
        ) { providerTrackId -> resolvedTrackIds += providerTrackId }.join()

        assertEquals(listOf("song-88"), resolvedTrackIds)
        assertEquals(listOf("Local 88 Artist"), gateway.searchQueries)
        assertEquals(listOf("direct:88", "load:88", "save:88:song-88"), store.events)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun prepareStreamingHeartbeatRecommendationRequestUsesNetEaseAndStartsLoading() {
        val viewModel = StreamingRecommendationViewModel(FakeRepositorySource(FakeDailyGateway()))
        viewModel.updateProviders(
            listOf(
                StreamingProviderDescriptor(
                    name = StreamingProviderName.QQ_MUSIC,
                    displayName = "QQ Music",
                    capabilities = StreamingProviderCapabilities(
                        supportsSearch = false,
                        supportsPlayback = false
                    )
                ),
                neteaseProvider()
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
        val viewModel = StreamingRecommendationViewModel(FakeRepositorySource(FakeDailyGateway()))
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

    private class FakeRepositorySource(
        private val gateway: StreamingGateway
    ) : StreamingRepositorySource {
        override fun current(): StreamingRepository = StreamingRepository(gateway)
    }

    private class FakeDailyGateway : StreamingGateway {
        val dailyProviders = mutableListOf<StreamingProviderName>()
        val heartbeatRequests = mutableListOf<StreamingHeartbeatRequest>()
        val searchQueries = mutableListOf<String>()
        var dailyTracks: List<StreamingTrack> = emptyList()
        var heartbeatTracks: List<StreamingTrack> = emptyList()
        var searchTracks: List<StreamingTrack> = emptyList()
        var failDaily: Boolean = false

        override suspend fun providers(): List<StreamingProviderDescriptor> = listOf(neteaseProvider())

        override suspend fun providerCapabilities(): List<StreamingProviderCapability> = emptyList()

        override suspend fun providersHealth(): List<StreamingProviderHealth> = emptyList()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
            searchQueries += request.query
            return StreamingSearchResult(
                provider = request.provider,
                query = request.query,
                page = request.page,
                pageSize = request.pageSize,
                tracks = searchTracks
            )
        }

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail =
            StreamingPlaylistDetail(request.provider, request.providerPlaylistId)

        override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> = emptyList()

        override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> = emptyList()

        override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> {
            dailyProviders += provider
            if (failDaily) {
                error("daily failed")
            }
            return dailyTracks
        }

        override suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack> {
            heartbeatRequests += request
            return heartbeatTracks
        }

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
            StreamingPlaybackSource(request.provider, request.providerTrackId, "https://example.test/${request.providerTrackId}.mp3")

        override suspend fun authState(provider: StreamingProviderName): StreamingAuthState = StreamingAuthState()

        override suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult =
            StreamingAuthResult(request.provider, StreamingAuthState())

        override suspend fun completeAuth(
            provider: StreamingProviderName,
            callbackUri: String,
            cookieHeader: String?
        ): StreamingAuthResult = StreamingAuthResult(provider, StreamingAuthState())

        override suspend fun signOut(provider: StreamingProviderName): StreamingAuthState = StreamingAuthState()
    }

    private companion object {
        fun neteaseProvider(): StreamingProviderDescriptor =
            StreamingProviderDescriptor(
                name = StreamingProviderName.NETEASE,
                displayName = "NetEase",
                enabled = true,
                capabilities = StreamingProviderCapabilities(
                    supportsSearch = true,
                    supportsPlayback = true
                ),
                status = StreamingProviderStatus.READY
            )

        fun streamingTrack(id: String): StreamingTrack =
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = id,
                title = "Track $id",
                artist = "Artist"
            )

        fun localTrack(id: Long): app.yukine.model.Track =
            app.yukine.model.Track(
                id,
                "Local $id",
                "Artist",
                "Album",
                0L,
                android.net.Uri.parse("file:///music/$id.mp3"),
                "local:$id"
            )

        fun callbacks(calls: MutableList<String>): RecommendationActionCallbacks =
            object : RecommendationActionCallbacks {
                override fun setStatus(status: String) {
                    calls += "status:$status"
                }

                override fun playDailyRecommendation(presentation: StreamingRecommendationPresentation) {
                    calls += "daily:${presentation.title}:${presentation.tracks.size}"
                }

                override fun seedRequest(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest {
                    calls += "seed:${provider.wireName}"
                    return HeartbeatRecommendationSeedRequest(seedTrackId = "seed", playlistId = "seed")
                }

                override fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation) {
                    calls += "heartbeat:${presentation.title}:${presentation.tracks.size}"
                }

                override fun logSeedMiss(request: HeartbeatRecommendationSeedRequest) {
                    calls += "miss:${request.seedMissingMessage}"
                }
            }
    }

    private class FakeStreamingTrackMatchStore : StreamingTrackMatchStore {
        val events = mutableListOf<String>()

        override fun directProviderTrackId(track: app.yukine.model.Track, provider: StreamingProviderName): String {
            events += "direct:${track.id}"
            return ""
        }

        override fun providerTrackIdFor(track: app.yukine.model.Track, provider: StreamingProviderName): String {
            events += "load:${track.id}"
            return ""
        }

        override fun saveProviderTrackId(
            track: app.yukine.model.Track,
            provider: StreamingProviderName,
            providerTrackId: String
        ) {
            events += "save:${track.id}:$providerTrackId"
        }
    }
}
