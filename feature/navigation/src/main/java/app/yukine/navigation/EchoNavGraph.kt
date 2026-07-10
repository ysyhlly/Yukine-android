package app.yukine.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.yukine.TrackDownloadStatus
import app.yukine.TrackDownloadItem
import app.yukine.NowPlayingEvent
import app.yukine.MainRoutes
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import app.yukine.LibraryGroupsDestinationState
import app.yukine.StreamingSearchState
import app.yukine.LibraryTrackListDestinationState
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.collections.CollectionsDestination
import app.yukine.downloads.DownloadsDestination
import app.yukine.home.HomeDestination
import app.yukine.library.LibraryGroupsDestination
import app.yukine.library.LibraryTrackListDestination
import app.yukine.network.NetworkDestination
import app.yukine.now.NowPlayingDestination
import app.yukine.queue.QueueDestination
import app.yukine.search.SearchDestination
import app.yukine.settings.SettingsDestination
import app.yukine.ui.EchoTheme
import app.yukine.ui.StreamingSearchScreen
import app.yukine.ui.UnifiedSearchStreamingState
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

private val EmptyRealtimeBands = FloatArray(0)
private const val RealtimeVisualPollMs = 33L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoNavGraph(
    tabs: List<EchoTabItem>,
    onTabChanged: (TabRoute) -> Unit = {},
    hostState: EchoNavHostState,
    closeNowPlayingAction: Runnable = Runnable { },
    nowPlayingEventHandler: (NowPlayingEvent) -> Unit = {},
    nowBar: (@Composable () -> Unit)? = null,
    topBar: @Composable () -> Unit = {}
) {
    val playbackState by hostState.playbackSnapshotProvider.playbackSnapshot.collectAsState()
    val route by hostState.routeState.collectAsState()
    val playbackQuality = StreamingDataPathMetadata.quality(playbackState.currentTrack?.dataPath)
    val pagerTabs = tabs.map { it.tab }
    val selectedTab = TabRoute.fromKey(hostState.selectedTabRoute) ?: HomeTab
    val selectedPagerIndex = pagerTabs.indexOf(selectedTab)
    val selectedIndex = selectedPagerIndex.coerceAtLeast(0)
    val selectedInPager = selectedPagerIndex >= 0
    var realtimeBeat by remember(hostState) {
        mutableStateOf(0f)
    }
    var realtimeBands by remember(hostState) {
        mutableStateOf(EmptyRealtimeBands)
    }
    val realtimeVisualsActive = hostState.visualMotionEnabled &&
        playbackState.playing &&
        realtimeVisualsVisible(selectedTab, route.networkPage)
    LaunchedEffect(hostState, realtimeVisualsActive) {
        if (!realtimeVisualsActive) {
            if (realtimeBeat != 0f) {
                realtimeBeat = 0f
            }
            if (!realtimeBands.contentEquals(EmptyRealtimeBands)) {
                realtimeBands = EmptyRealtimeBands
            }
            return@LaunchedEffect
        }
        while (true) {
            val nextBeat = hostState.realtimeBeatProvider().coerceIn(0f, 1f)
            val nextBands = hostState.realtimeBandsProvider()
            if (realtimeBeat != nextBeat) {
                realtimeBeat = nextBeat
            }
            if (!realtimeBands.contentEquals(nextBands)) {
                realtimeBands = if (nextBands.isEmpty()) EmptyRealtimeBands else nextBands
            }
            delay(RealtimeVisualPollMs)
        }
    }
    val audioMotion = YukineOrbAudioMotion(
        spectrumBands = playbackState.spectrum.bands,
        generatedFrames = playbackState.spectrum.generatedFrames,
        bandCount = playbackState.spectrum.bandCount,
        waveformBars = playbackState.waveform.bars,
        waveformGeneratedBars = playbackState.waveform.generatedBars,
        positionMs = playbackState.positionMs,
        durationMs = playbackState.durationMs,
        playing = playbackState.playing,
        realtimeBeat = max(playbackState.realtimeBeat, realtimeBeat),
        realtimeBands = realtimeBands,
        visualMotionEnabled = hostState.visualMotionEnabled
    )
    val nowBarState by hostState.nowBarStateProvider.nowBarState.collectAsState()
    val settingsChromeState by hostState.settingsChromeState.collectAsState()
    var activeDownload by remember(hostState.trackDownloadController) {
        mutableStateOf<TrackDownloadItem?>(null)
    }
    val activeDownloadVisible = selectedInPager || selectedTab == SearchTab || selectedTab == NowTab
    LaunchedEffect(hostState.trackDownloadController, activeDownloadVisible) {
        if (!activeDownloadVisible) {
            activeDownload = null
            return@LaunchedEffect
        }
        while (true) {
            activeDownload = hostState.trackDownloadController
                ?.snapshot()
                ?.firstOrNull { it.status != TrackDownloadStatus.Finished }
            delay(1000L)
        }
    }
    val openSearchAction = remember(hostState) { Runnable { onTabChanged(SearchTab) } }
    var openNowPlayingImmersive by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = selectedIndex) { pagerTabs.size }

    var showQueueSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(selectedInPager, selectedIndex) {
    if (!selectedInPager) {
            return@LaunchedEffect
        }
        if (pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(pagerState, selectedInPager) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (!selectedInPager) {
                return@collect
            }
            val tab = pagerTabs.getOrNull(page) ?: return@collect
            if (tab.route != hostState.selectedTabRoute) {
                hostState.selectedTabRoute = tab.route
                onTabChanged(tab)
            }
        }
    }

    val persistentNowBar: @Composable () -> Unit = nowBar ?: {
        EchoNowBar(
            state = nowBarState,
            onPrevious = Runnable { nowPlayingEventHandler(NowPlayingEvent.Previous) },
            onPlayPause = Runnable { nowPlayingEventHandler(NowPlayingEvent.PlayPause) },
            onNext = Runnable { nowPlayingEventHandler(NowPlayingEvent.Next) },
            onFavorite = Runnable { nowPlayingEventHandler(NowPlayingEvent.ToggleFavorite) },
            onShuffle = Runnable { nowPlayingEventHandler(NowPlayingEvent.ToggleShuffle) },
            onRepeat = Runnable { nowPlayingEventHandler(NowPlayingEvent.CycleRepeatMode) },
            onOpenNowPlaying = {
                openNowPlayingImmersive = true
                hostState.selectedTabRoute = QueueTab.route
                onTabChanged(QueueTab)
            },
            onOpenQueue = { showQueueSheet = true },
            onSeek = { positionMs -> nowPlayingEventHandler(NowPlayingEvent.SeekTo(positionMs)) }
        )
    }

    EchoScaffold(
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = { tab -> 
            hostState.selectedTabRoute = tab.route
            onTabChanged(tab)
        },
        nowBar = persistentNowBar,
        topBar = topBar,
        backgroundUri = settingsChromeState.pageBackgrounds.uriFor(backgroundPageForTab(selectedTab)),
        backgroundTransform = settingsChromeState.pageBackgrounds.transformFor(backgroundPageForTab(selectedTab))
    ) { contentModifier ->
        if (!selectedInPager) {
            // Routes that live outside the 4-tab pager (Collections / Network / Now),
            // reachable via in-app navigation rather than the bottom bar. Render each
            // directly so navigating to them never leaves the pager showing a stale page.
            Box(modifier = contentModifier) {
                when (selectedTab) {
                    CollectionsTab -> CollectionsDestination(hostState.collectionsStateProvider)
                    NetworkTab -> {
                        val networkMenuState by hostState.networkMenuState.collectAsState()
                        NetworkDestination(
                            networkPage = route.networkPage,
                            menuState = networkMenuState,
                            sourcesState = hostState.networkSourcesState,
                            trackListState = hostState.libraryTrackListState,
                            activeDownload = activeDownload,
                            playbackQuality = playbackQuality,
                            audioMotion = audioMotion,
                            streamingContent = {
                                val streamingState by hostState.streamingState.collectAsState()
                                StreamingSearchScreen(
                                    state = streamingState,
                                    labels = streamingState.searchChromeLabels,
                                    actions = streamingState.searchChromeActions
                                )
                            }
                        )
                    }
                    DownloadsTab -> {
                        DownloadsDestination(
                            state = hostState.downloadsState,
                            openDirectoryRequests = hostState.downloadsOpenDirectoryRequests,
                            actions = hostState.downloadsActions
                        )
                    }
                    SearchTab -> {
                        val streamingState by hostState.streamingState.collectAsState()
                        SearchDestination(
                            searchState = hostState.searchState,
                            streamingState = streamingState.toUnifiedSearchStreamingState(),
                            activeDownload = activeDownload,
                            playbackQuality = playbackQuality,
                            audioMotion = audioMotion
                        )
                    }
                    NowTab -> NowPlayingDestination(
                        state = hostState.nowPlayingUiState,
                        defaultImmersive = openNowPlayingImmersive,
                        onDefaultImmersiveConsumed = { openNowPlayingImmersive = false },
                        gesturesEnabled = settingsChromeState.nowPlayingGesturesEnabled,
                        onClose = closeNowPlayingAction,
                        onEvent = nowPlayingEventHandler,
                        onSwitchSource = { track, provider, providerTrackId, quality ->
                            hostState.nowPlayingStateProvider.switchSource(track, provider, providerTrackId, quality)
                        },
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    else -> HomeDestination(hostState.homeDashboardState, activeDownload, playbackQuality, audioMotion)
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = contentModifier,
                beyondViewportPageCount = 1
            ) { page ->
                val tab = pagerTabs[page]
                when (tab) {
                    HomeTab -> HomeDestination(hostState.homeDashboardState, activeDownload, playbackQuality, audioMotion)
                    LibraryTab -> LibraryDestination(
                        groupsState = hostState.libraryGroupsState,
                        trackListState = hostState.libraryTrackListState,
                        openSearchAction = openSearchAction,
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    QueueTab -> NowPlayingDestination(
                        state = hostState.nowPlayingUiState,
                        defaultImmersive = openNowPlayingImmersive,
                        onDefaultImmersiveConsumed = { openNowPlayingImmersive = false },
                        gesturesEnabled = settingsChromeState.nowPlayingGesturesEnabled,
                        onClose = closeNowPlayingAction,
                        onEvent = nowPlayingEventHandler,
                        onSwitchSource = { track, provider, providerTrackId, quality ->
                            hostState.nowPlayingStateProvider.switchSource(track, provider, providerTrackId, quality)
                        },
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    SettingsTab -> SettingsDestination(
                        state = hostState.settingsState,
                        scrollState = hostState.settingsScrollState,
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    else -> HomeDestination(hostState.homeDashboardState, activeDownload, playbackQuality, audioMotion)
                }
            }
        }
    }

    if (showQueueSheet) {
        val p = EchoTheme.colors()
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            containerColor = p.surface
        ) {
            QueueDestination(
                hostState.queueStateProvider,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            )
        }
    }
}

@Composable
private fun LibraryDestination(
    groupsState: StateFlow<LibraryGroupsDestinationState>,
    trackListState: StateFlow<LibraryTrackListDestinationState>,
    openSearchAction: Runnable,
    activeDownload: TrackDownloadItem?,
    playbackQuality: String,
    audioMotion: YukineOrbAudioMotion
) {
    val groups by groupsState.collectAsState()
    val trackList by trackListState.collectAsState()
    val renderGroups = groups.title.isNotBlank() && trackList.title.isBlank()
    if (renderGroups) {
        LibraryGroupsDestination(
            state = groupsState,
            onSearch = openSearchAction,
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion
        )
        return
    }
    LibraryTrackListDestination(
        state = trackListState,
        onSearch = openSearchAction,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    )
}

private fun backgroundPageForTab(tab: TabRoute): String = when (tab) {
    HomeTab -> app.yukine.PageBackgrounds.PAGE_HOME
    LibraryTab -> app.yukine.PageBackgrounds.PAGE_LIBRARY
    QueueTab, NowTab -> app.yukine.PageBackgrounds.PAGE_PLAYER
    SettingsTab -> app.yukine.PageBackgrounds.PAGE_SETTINGS
    else -> ""
}

private fun realtimeVisualsVisible(selectedTab: TabRoute, networkPage: String): Boolean = when (selectedTab) {
    HomeTab, LibraryTab, QueueTab, SettingsTab, SearchTab, NowTab -> true
    NetworkTab -> networkPage == MainRoutes.NETWORK_HOME ||
        networkPage == MainRoutes.NETWORK_WEBDAV ||
        networkPage == MainRoutes.NETWORK_STREAM_LIST ||
        networkPage == MainRoutes.NETWORK_WEBDAV_TRACKS ||
        networkPage == MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS
    else -> false
}

private fun StreamingSearchState.toUnifiedSearchStreamingState(): UnifiedSearchStreamingState =
    UnifiedSearchStreamingState(
        tracks = searchResult?.tracks.orEmpty(),
        loading = loading,
        errorMessage = errorMessage,
        hasMore = searchResult?.hasMore == true,
        sourceLabels = providers.associate { provider ->
            provider.name to provider.displayName
        }
    )
