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
import org.junit.Rule
import org.junit.Test

class DailyRecommendationControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun reportsEmptyStatusWhenDailyProviderIsUnavailable() {
        val viewModel = StreamingViewModel()
        val calls = mutableListOf<String>()
        val controller = DailyRecommendationController(
            viewModel,
            DailyRecommendationController.LanguageProvider { AppLanguage.MODE_ENGLISH },
            listener(calls)
        )

        controller.playStreamingDailyRecommendations(StreamingProviderName.SPOTIFY)

        assertEquals(listOf("status:No daily recommendations (login required?)"), calls)
    }

    @Test
    fun fetchesDailyRecommendationsThroughStreamingViewModel() = runTest {
        val gateway = FakeDailyGateway()
        gateway.dailyTracks = listOf(streamingTrack("daily-1"))
        val viewModel = StreamingViewModel()
        viewModel.bindStreamingRepository(StreamingRepository(gateway))
        viewModel.refreshStreamingProviders().join()
        val calls = mutableListOf<String>()
        val controller = DailyRecommendationController(
            viewModel,
            DailyRecommendationController.LanguageProvider { AppLanguage.MODE_ENGLISH },
            listener(calls)
        )

        controller.playStreamingDailyRecommendations(StreamingProviderName.NETEASE)
        waitUntil { calls.any { it.startsWith("play:") } }

        assertEquals(listOf(StreamingProviderName.NETEASE), gateway.dailyProviders)
        assertEquals(
            listOf(
                "status:Loading daily recommendations",
                "play:daily-1:No daily recommendations (login required?):Daily recommendations"
            ),
            calls
        )
    }

    private fun listener(calls: MutableList<String>): DailyRecommendationController.Listener =
        object : DailyRecommendationController.Listener {
            override fun playRecommendationTracks(
                streamingTracks: List<StreamingTrack>,
                emptyStatus: String,
                title: String
            ) {
                calls += "play:${streamingTracks.joinToString { it.providerTrackId }}:$emptyStatus:$title"
            }

            override fun setStatus(status: String) {
                calls += "status:$status"
            }
        }

    private fun streamingTrack(id: String): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = "Track $id",
            artist = "Artist"
        )

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 1_000L
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10L)
        }
    }

    private class FakeDailyGateway : StreamingGateway {
        val dailyProviders = mutableListOf<StreamingProviderName>()
        var dailyTracks: List<StreamingTrack> = emptyList()

        override suspend fun providers(): List<StreamingProviderDescriptor> =
            listOf(
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
            )

        override suspend fun providerCapabilities(): List<StreamingProviderCapability> = emptyList()

        override suspend fun providersHealth(): List<StreamingProviderHealth> = emptyList()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
            StreamingSearchResult(request.provider, request.query, request.page, request.pageSize)

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail =
            StreamingPlaylistDetail(request.provider, request.providerPlaylistId)

        override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> = emptyList()

        override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> = emptyList()

        override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> {
            dailyProviders += provider
            return dailyTracks
        }

        override suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack> = emptyList()

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
}
