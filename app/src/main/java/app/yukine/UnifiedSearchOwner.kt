package app.yukine

import app.yukine.model.Track
import app.yukine.navigation.NetworkTab
import app.yukine.navigation.SearchTab
import app.yukine.streaming.StreamingTrack
import app.yukine.ui.StreamingTrackAction
import app.yukine.ui.UnifiedLocalTrackAction
import app.yukine.ui.UnifiedSearchActions
import app.yukine.ui.UnifiedSearchQueryAction

/** Owns unified-search query, cleanup, remote resolution and playback policy. */
internal class UnifiedSearchOwner @JvmOverloads constructor(
    private val routeController: MainRouteController,
    private val searchViewModel: SearchViewModel,
    private val streamingViewModel: StreamingViewModel,
    private val libraryViewModel: LibraryViewModel,
    private val libraryStore: LibraryDataStateOwner,
    private val streamingSearch: StreamingSearchActionHandler,
    private val settingsStore: MainSettingsStore,
    private val quality: StreamingPlaybackQuality,
    private val player: TrackListPlaybackAction,
    private val feedback: (String) -> Unit,
    private val status: (String) -> Unit,
    private val resolver: StreamingSourceResolver = StreamingSourceResolver {
            provider,
            providerTrackId,
            metadata,
            selectedQuality,
            callback ->
        streamingViewModel.playbackResolution.resolveStreamingTrackForPlayback(
            provider,
            providerTrackId,
            metadata,
            selectedQuality,
            callback
        )
    }
) {
    private var streamingPlaybackGeneration = 0L

    fun actions(): UnifiedSearchActions = UnifiedSearchActions(
        onQueryChange = UnifiedSearchQueryAction(::updateQuery),
        onSearch = UnifiedSearchQueryAction(::search),
        onPlayLocalTrack = UnifiedLocalTrackAction(::playLocalTrack),
        onPlayStreamingTrack = StreamingTrackAction(::playStreamingTrack),
        onLoadMoreStreaming = Runnable(streamingSearch::loadNextPage),
        onExit = Runnable(::clear)
    )

    fun updateQuery(query: String?) {
        searchViewModel.updateQuery(query.orEmpty())
    }

    fun search(query: String?) {
        val normalized = query?.trim().orEmpty()
        routeController.setSearchQuery(normalized)
        if (normalized.isNotEmpty()) {
            streamingSearch.search(normalized)
        }
    }

    fun clear() {
        if (routeController.current().searchQuery.isNotEmpty()) {
            routeController.setSearchQuery("")
        }
        searchViewModel.clearSearch()
        streamingViewModel.search.clearStreamingSearchSession()
        streamingPlaybackGeneration++
        libraryStore.applySearch("")
        libraryViewModel.presentation.syncSearchQuery("")
    }

    fun playLocalTrack(track: Track) {
        player.play(listOf(track), 0)
    }

    fun playStreamingTrack(track: StreamingTrack) {
        if (!track.playable) {
            feedback(track.unavailableReason?.takeIf(String::isNotBlank) ?: "该在线歌曲暂不可播放")
            return
        }
        val requestId = ++streamingPlaybackGeneration
        feedback("正在解析在线歌曲：${track.title}")
        resolver.resolve(
            track.provider,
            track.providerTrackId,
            track,
            quality.selected()
        ) { resolved ->
            if (requestId != streamingPlaybackGeneration) {
                return@resolve
            }
            if (resolved == null) {
                feedback("在线歌曲解析失败，请稍后再试")
                return@resolve
            }
            player.play(listOf(resolved), 0)
            feedback("开始播放：${resolved.title}")
        }
    }

    fun applyCurrentSearch() {
        val route = routeController.current()
        val query = route.searchQuery
        libraryStore.applySearchAsync(query, Runnable {
            if (query.isNotBlank() && libraryStore.visibleTracks().isEmpty()) {
                status(AppLanguage.text(settingsStore.languageMode(), "search.no.results"))
            }
        })
        if (route.selectedTab == SearchTab && query.isNotBlank()) {
            streamingSearch.search(query)
        }
        if (
            route.selectedTab == NetworkTab &&
            (route.networkPage == NetworkPage.Streaming ||
                route.networkPage == NetworkPage.StreamingHub)
        ) {
            streamingSearch.search(query)
        }
    }
}
