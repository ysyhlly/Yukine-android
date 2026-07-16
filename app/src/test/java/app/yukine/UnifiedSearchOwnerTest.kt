package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Track
import app.yukine.navigation.SearchTab
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnifiedSearchOwnerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun actionsRouteSearchPlayLocalAndClearEverySearchProjection() {
        val fixture = fixture()
        val local = localTrack(1L, "Local")
        val actions = fixture.owner.actions()

        actions.onQueryChange.run("  draft  ")
        actions.onSearch.run("  echo  ")
        actions.onPlayLocalTrack.run(local)
        actions.onLoadMoreStreaming.run()

        assertEquals("  draft  ", fixture.searchViewModel.uiState.value.query)
        assertEquals("echo", fixture.routeController.current().searchQuery)
        assertEquals(listOf("search:echo", "more"), fixture.streamingHandler.calls)
        assertEquals(listOf("play:1:0"), fixture.playerCalls)

        actions.onExit.run()

        assertEquals("", fixture.routeController.current().searchQuery)
        assertEquals("", fixture.searchViewModel.uiState.value.query)
        assertEquals("", fixture.libraryViewModel.libraryUi.value.query)
    }

    @Test
    fun remotePlaybackRejectsStaleResolutionAndReportsUnavailableTracks() {
        val fixture = fixture()
        val first = streamingTrack("first")
        val second = streamingTrack("second")
        val firstResolved = localTrack(10L, "First resolved")
        val secondResolved = localTrack(11L, "Second resolved")

        fixture.owner.playStreamingTrack(first)
        fixture.owner.playStreamingTrack(second)
        fixture.resolverCallbacks[0].onResult(firstResolved)
        fixture.resolverCallbacks[1].onResult(secondResolved)
        fixture.owner.playStreamingTrack(
            streamingTrack("blocked").copy(playable = false, unavailableReason = "地区不可用")
        )

        assertEquals(listOf("play:11:0"), fixture.playerCalls)
        assertEquals(
            listOf(
                "正在解析在线歌曲：Song first",
                "正在解析在线歌曲：Song second",
                "开始播放：Second resolved",
                "地区不可用"
            ),
            fixture.feedback
        )
    }

    @Test
    fun currentSearchAppliesLocalFilterAndRequestsRemoteResultsOnSearchTab() {
        val fixture = fixture()
        fixture.routeController.navigateToTab(SearchTab, true)
        fixture.routeController.setSearchQuery("missing")

        fixture.owner.applyCurrentSearch()
        mainDispatcherRule.testScheduler.advanceUntilIdle()

        assertEquals(listOf("search:missing"), fixture.streamingHandler.calls)
        assertEquals(
            listOf(AppLanguage.text(AppLanguage.MODE_ENGLISH, "search.no.results")),
            fixture.status
        )
    }

    private fun fixture(): Fixture {
        val routeController = MainRouteController(NavigationViewModel(SavedStateHandle()))
        val searchViewModel = SearchViewModel()
        val streamingViewModel = StreamingViewModel()
        val libraryViewModel = LibraryViewModel(preparationDispatcher = Dispatchers.Unconfined)
        val libraryStore = libraryViewModel.dataOwner()
        val streamingHandler = RecordingStreamingSearchHandler()
        val settingsStore = MainSettingsStore().apply {
            setLanguageMode(AppLanguage.MODE_ENGLISH)
        }
        val playerCalls = mutableListOf<String>()
        val feedback = mutableListOf<String>()
        val status = mutableListOf<String>()
        val resolverCallbacks = mutableListOf<StreamingCallback<Track?>>()
        val owner = UnifiedSearchOwner(
            routeController = routeController,
            searchViewModel = searchViewModel,
            streamingViewModel = streamingViewModel,
            libraryViewModel = libraryViewModel,
            libraryStore = libraryStore,
            streamingSearch = streamingHandler,
            settingsStore = settingsStore,
            quality = object : StreamingPlaybackQuality {
                override fun adaptive() = StreamingAudioQuality.HIGH
                override fun selected() = StreamingAudioQuality.LOSSLESS
            },
            player = TrackListPlaybackAction { tracks, index ->
                playerCalls += "play:${tracks.single().id}:$index"
            },
            feedback = feedback::add,
            status = status::add,
            resolver = StreamingSourceResolver { _, _, _, quality, callback ->
                assertEquals(StreamingAudioQuality.LOSSLESS, quality)
                resolverCallbacks += callback
            }
        )
        return Fixture(
            owner,
            routeController,
            searchViewModel,
            libraryViewModel,
            streamingHandler,
            playerCalls,
            feedback,
            status,
            resolverCallbacks
        )
    }

    private class RecordingStreamingSearchHandler : StreamingSearchActionHandler {
        val calls = mutableListOf<String>()

        override fun selectProvider(provider: StreamingProviderName) = Unit
        override fun search(query: String) {
            calls += "search:$query"
        }

        override fun login(provider: StreamingProviderName) = Unit
        override fun signOut(provider: StreamingProviderName) = Unit
        override fun openAuthLaunch() = Unit
        override fun playStreamingTrack(track: StreamingTrack) = Unit
        override fun playResolvedTrack(track: Track) = Unit
        override fun loadNextPage() {
            calls += "more"
        }
    }

    private data class Fixture(
        val owner: UnifiedSearchOwner,
        val routeController: MainRouteController,
        val searchViewModel: SearchViewModel,
        val libraryViewModel: LibraryViewModel,
        val streamingHandler: RecordingStreamingSearchHandler,
        val playerCalls: MutableList<String>,
        val feedback: MutableList<String>,
        val status: MutableList<String>,
        val resolverCallbacks: MutableList<StreamingCallback<Track?>>
    )

    private fun localTrack(id: Long, title: String) =
        Track(id, title, "Artist", "Album", 3_000L, Uri.EMPTY, "file:$id")

    private fun streamingTrack(id: String) = StreamingTrack(
        provider = StreamingProviderName.NETEASE,
        providerTrackId = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        durationMs = 3_000L
    )
}
