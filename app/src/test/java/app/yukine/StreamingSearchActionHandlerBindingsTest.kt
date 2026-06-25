package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingAuthKind
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.RegistryStreamingGateway
import app.yukine.streaming.StreamingProviderCapabilities
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingProviderRegistry
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingPlaybackRequest
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProvider
import app.yukine.streaming.StreamingSearchRequest
import app.yukine.streaming.StreamingSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.test.runTest

class StreamingSearchActionHandlerBindingsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun delegatesProviderSelectionIntoViewModelState() {
        val streamingViewModel = StreamingViewModel()
        val gateway = FakeGateway()
        val bindings = StreamingSearchActionHandlerBindings(streamingViewModel, gateway)

        bindings.selectProvider(StreamingProviderName.NETEASE)

        assertEquals(StreamingProviderName.NETEASE, streamingViewModel.streaming.value.selectedProvider)
    }

    @Test
    fun searchWithoutSearchableProviderReportsNoSearchableSource() {
        val streamingViewModel = StreamingViewModel()
        val bindings = StreamingSearchActionHandlerBindings(streamingViewModel, FakeGateway())
        streamingViewModel.updateStreamingProviders(
            providers = listOf(
                StreamingProviderDescriptor(
                    name = StreamingProviderName.NETEASE,
                    displayName = "NetEase",
                    capabilities = StreamingProviderCapabilities(
                        supportsSearch = false,
                        supportsPlayback = true,
                        supportsAuth = false
                    )
                )
            )
        )

        bindings.search("echo")
        assertTrue(streamingViewModel.streaming.value.errorMessage?.contains("可搜索") == true)
    }

    @Test
    fun searchQueriesAllSearchableProviders() {
        val netease = FakeProvider(StreamingProviderName.NETEASE, searchTrackId = "netease-song")
        val qq = FakeProvider(StreamingProviderName.QQ_MUSIC, searchTrackId = "qq-song")
        val streamingViewModel = StreamingViewModel()
        streamingViewModel.bindStreamingRepository(repository(netease, qq))
        val bindings = StreamingSearchActionHandlerBindings(streamingViewModel, FakeGateway())
        streamingViewModel.updateStreamingProviders(
            providers = listOf(netease.descriptor, qq.descriptor)
        )

        bindings.search("echo")
        waitUntil { streamingViewModel.streaming.value.searchResult?.tracks?.size == 2 }

        assertEquals(listOf("echo"), netease.searchRequests.map { it.query })
        assertEquals(listOf("echo"), qq.searchRequests.map { it.query })
        assertEquals(setOf(StreamingMediaType.TRACK), netease.searchRequests.single().mediaTypes)
        assertEquals(setOf(StreamingMediaType.TRACK), qq.searchRequests.single().mediaTypes)
        assertEquals(
            setOf(StreamingProviderName.NETEASE, StreamingProviderName.QQ_MUSIC),
            streamingViewModel.streaming.value.searchResult?.tracks?.map { it.provider }?.toSet()
        )
    }

    @Test
    fun loginUnsupportedAuthUsesStreamingViewModelState() {
        val streamingViewModel = StreamingViewModel()
        val bindings = StreamingSearchActionHandlerBindings(streamingViewModel, FakeGateway())
        streamingViewModel.updateStreamingProviders(
            providers = listOf(
                StreamingProviderDescriptor(
                    name = StreamingProviderName.NETEASE,
                    displayName = "NetEase",
                    capabilities = StreamingProviderCapabilities(
                        supportsSearch = true,
                        supportsPlayback = true,
                        supportsAuth = false
                    )
                )
            )
        )

        bindings.login(StreamingProviderName.NETEASE)
        assertTrue(streamingViewModel.streaming.value.errorMessage?.contains("NetEase") == true)
    }

    @Test
    fun playResolvedTrackGoesDirectlyToActionGateway() {
        val gateway = FakeGateway()
        val bindings = StreamingSearchActionHandlerBindings(
            StreamingViewModel(),
            gateway
        )

        bindings.playResolvedTrack(Track(7L, "Song", "Artist", "Album", 1_000L, null, "file:7"))

        assertEquals(listOf(7L), gateway.playedTrackIds)
    }

    @Test
    fun playStreamingTrackUnsupportedProviderUsesStreamingViewModelState() {
        val streamingViewModel = StreamingViewModel()
        val bindings = StreamingSearchActionHandlerBindings(streamingViewModel, FakeGateway())
        streamingViewModel.updateStreamingProviders(
            providers = listOf(
                StreamingProviderDescriptor(
                    name = StreamingProviderName.NETEASE,
                    displayName = "NetEase",
                    capabilities = StreamingProviderCapabilities(
                        supportsSearch = true,
                        supportsPlayback = false,
                        supportsAuth = true
                    )
                )
            )
        )

        bindings.playStreamingTrack(
            app.yukine.streaming.StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "song-1",
                title = "Song",
                artist = "Artist"
            )
        )

        assertTrue(streamingViewModel.streaming.value.errorMessage?.contains("NetEase") == true)
    }

    @Test
    fun playStreamingTrackResolvesThroughStreamingViewModelRepository() = runTest {
        val newProvider = FakeProvider(StreamingProviderName.NETEASE)
        val streamingViewModel = StreamingViewModel()
        streamingViewModel.bindStreamingRepository(repository(newProvider))
        val gateway = FakeGateway().apply {
            quality = StreamingAudioQuality.HIRES
        }
        val bindings = StreamingSearchActionHandlerBindings(streamingViewModel, gateway)
        streamingViewModel.updateStreamingProviders(
            providers = listOf(newProvider.descriptor)
        )

        bindings.playStreamingTrack(
            app.yukine.streaming.StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "song-1",
                title = "Song",
                artist = "Artist"
            )
        )
        waitUntil { newProvider.playbackRequests.isNotEmpty() }
        waitUntil { streamingViewModel.streaming.value.resolvedPlaybackSource != null }

        assertEquals(
            listOf(StreamingPlaybackRequest(StreamingProviderName.NETEASE, "song-1", StreamingAudioQuality.HIRES)),
            newProvider.playbackRequests
        )
        assertEquals("https://example.test/song-1.mp3", streamingViewModel.streaming.value.resolvedPlaybackSource?.url)
    }

    @Test
    fun openAuthLaunchGoesDirectlyToGatewayAndClearsPendingLaunch() {
        val streamingViewModel = StreamingViewModel()
        val gateway = FakeGateway(openAuthResult = true)
        val bindings = StreamingSearchActionHandlerBindings(
            streamingViewModel,
            gateway
        )
        streamingViewModel.updateStreamingAuthLaunch(
            StreamingProviderName.NETEASE,
            StreamingAuthState(kind = StreamingAuthKind.REMOTE_GATEWAY),
            "https://login"
        )

        bindings.openAuthLaunch()

        assertEquals(listOf("https://login"), gateway.launchedUrls)
        assertEquals(null, streamingViewModel.streaming.value.pendingAuthLaunch)
    }

    private class FakeGateway(
        private val openAuthResult: Boolean = false
    ) : MainActivityStreamingActionGateway {
        val playedTrackIds = ArrayList<Long>()
        val launchedUrls = ArrayList<String>()
        val manualCookieProviders = ArrayList<StreamingProviderName>()
        var quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS

        override fun streamingPlaybackQuality(): StreamingAudioQuality = quality

        override fun languageMode(): String = AppLanguage.MODE_ENGLISH

        override fun openAuthLaunch(launch: MainActivityStreamingAuthLaunch?): Boolean {
            launch?.launchUrl?.let { launchedUrls += it }
            return openAuthResult
        }

        override fun playResolvedTrack(track: Track) {
            playedTrackIds += track.id
        }

        override fun onStreamingLoginSuccess(provider: StreamingProviderName) = Unit

        override fun openManualCookieImport(provider: StreamingProviderName) {
            manualCookieProviders += provider
        }
    }

    private class FakeProvider(
        provider: StreamingProviderName,
        private val searchTrackId: String = ""
    ) : StreamingProvider {
        val searchRequests = mutableListOf<StreamingSearchRequest>()
        val playbackRequests = mutableListOf<StreamingPlaybackRequest>()

        override val descriptor: StreamingProviderDescriptor =
            StreamingProviderDescriptor(
                name = provider,
                displayName = provider.wireName,
                capabilities = StreamingProviderCapabilities(
                    supportsSearch = true,
                    supportsPlayback = true,
                    supportsAuth = true
                )
            )

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
            searchRequests += request
            return StreamingSearchResult(
                provider = descriptor.name,
                query = request.query,
                page = request.page,
                pageSize = request.pageSize,
                tracks = searchTrackId.takeIf { it.isNotBlank() }?.let {
                    listOf(
                        app.yukine.streaming.StreamingTrack(
                            provider = descriptor.name,
                            providerTrackId = it,
                            title = "Song $it",
                            artist = "Artist"
                        )
                    )
                }.orEmpty()
            )
        }

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
            playbackRequests += request
            return StreamingPlaybackSource(
                provider = request.provider,
                providerTrackId = request.providerTrackId,
                url = "https://example.test/${request.providerTrackId}.mp3"
            )
        }
    }

    private fun repository(vararg providers: StreamingProvider): StreamingRepository =
        StreamingRepository(RegistryStreamingGateway(StreamingProviderRegistry(providers.toList())))

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
