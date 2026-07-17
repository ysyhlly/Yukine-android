package app.yukine.navigation

import app.yukine.NetworkPage

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.yukine.LibraryStoreState
import app.yukine.DownloadsUiState
import app.yukine.StreamingSearchState
import app.yukine.LibraryTrackListDestinationState
import app.yukine.ui.LibraryActionHandler
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryFilter
import app.yukine.ui.LibraryOverviewScreen
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.downloads.DownloadsDestination
import app.yukine.home.HomeDestination
import app.yukine.library.LibraryGroupsDestination
import app.yukine.library.LibraryTrackListDestination
import app.yukine.library.RecordingMatchDestination
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val playbackState by hostState.player.playbackSnapshotProvider.playbackSnapshot.collectAsState()
    val route by hostState.routeState.collectAsState()
    val playbackQuality = StreamingDataPathMetadata.quality(playbackState.currentTrack?.dataPath)
    val pagerTabs = tabs.map { it.tab }
    val selectedTab = if (route.selectedTab == CollectionsTab) LibraryTab else route.selectedTab
    val selectedPagerIndex = pagerTabs.indexOf(selectedTab)
    val selectedIndex = selectedPagerIndex.coerceAtLeast(0)
    val selectedInPager = selectedPagerIndex >= 0
    var realtimeBeat by remember(hostState) {
        mutableStateOf(0f)
    }
    var realtimeBands by remember(hostState) {
        mutableStateOf(EmptyRealtimeBands)
    }
    val realtimeVisualsActive = hostState.player.visualMotionEnabled &&
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
            val nextBeat = hostState.player.realtimeBeatProvider().coerceIn(0f, 1f)
            val nextBands = hostState.player.realtimeBandsProvider()
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
        visualMotionEnabled = hostState.player.visualMotionEnabled
    )
    val nowBarState by hostState.player.nowPlayingStateProvider.nowBarState.collectAsState()
    val settingsChromeState by hostState.settings.settingsChromeState.collectAsState()
    val streamingState by hostState.streaming.streamingState.collectAsState()
    var activeDownload by remember(hostState.player.trackDownloadController) {
        mutableStateOf<TrackDownloadItem?>(null)
    }
    val activeDownloadVisible = selectedInPager || selectedTab == SearchTab || selectedTab == NowTab
    LaunchedEffect(hostState.player.trackDownloadController, activeDownloadVisible) {
        if (!activeDownloadVisible) {
            activeDownload = null
            return@LaunchedEffect
        }
        while (true) {
            activeDownload = hostState.player.trackDownloadController
                ?.snapshot()
                ?.firstOrNull { it.status != TrackDownloadStatus.Finished }
            delay(1000L)
        }
    }
    val openSearchAction = remember(hostState) { Runnable { onTabChanged(SearchTab) } }
    val openDownloadsAction = remember(hostState) { Runnable { onTabChanged(DownloadsTab) } }
    var nowPlayingImmersive by remember { mutableStateOf(false) }
    val playerPageSelected = selectedTab == QueueTab || selectedTab == NowTab
    LaunchedEffect(playerPageSelected) {
        if (!playerPageSelected) {
            nowPlayingImmersive = false
        }
    }

    val pagerState = rememberPagerState(initialPage = selectedIndex) { pagerTabs.size }

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
            if (tab != route.selectedTab) {
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
                nowPlayingImmersive = true
                onTabChanged(QueueTab)
            },
            onOpenQueue = { hostState.setQueueSheetVisibility(true) },
            onSeek = { positionMs -> nowPlayingEventHandler(NowPlayingEvent.SeekTo(positionMs)) }
        )
    }

    EchoScaffold(
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = { tab -> 
            onTabChanged(tab)
        },
        nowBar = persistentNowBar,
        topBar = topBar,
        backgroundUri = settingsChromeState.pageBackgrounds.uriFor(backgroundPageForTab(selectedTab)),
        backgroundTransform = settingsChromeState.pageBackgrounds.transformFor(backgroundPageForTab(selectedTab)),
        customBackgroundVisible = !playerPageSelected || !nowPlayingImmersive,
        customBackgroundBlurEnabled = settingsChromeState.customBackgroundBlurEnabled,
        customBackgroundBlurRadiusDp = settingsChromeState.customBackgroundBlurRadiusDp,
        glassBlurEnabled = settingsChromeState.glassBlurEnabled,
        glassBlurRadiusDp = settingsChromeState.glassBlurRadiusDp,
        glassSurfaceOpacity = settingsChromeState.glassSurfaceOpacity
    ) { contentModifier ->
        if (!selectedInPager) {
            // Routes that live outside the 4-tab pager (Network / Search / Now),
            // reachable via in-app navigation rather than the bottom bar. Render each
            // directly so navigating to them never leaves the pager showing a stale page.
            Box(modifier = contentModifier) {
                when (selectedTab) {
                    NetworkTab -> {
                        val networkMenuState by hostState.settings.networkMenuState.collectAsState()
                        NetworkDestination(
                            networkPage = route.networkPage,
                            menuState = networkMenuState,
                            sourcesState = hostState.settings.networkSourcesState,
                            trackListState = hostState.library.libraryTrackListState,
                            activeDownload = activeDownload,
                            playbackQuality = playbackQuality,
                            audioMotion = audioMotion,
                            streamingContent = {
                                val streamingState by hostState.streaming.streamingState.collectAsState()
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
                            state = hostState.library.downloadsState,
                            openDirectoryRequests = hostState.library.downloadsOpenDirectoryRequests,
                            actions = hostState.library.downloadsActions
                        )
                    }
                    SearchTab -> {
                        val streamingState by hostState.streaming.streamingState.collectAsState()
                        SearchDestination(
                            searchState = hostState.library.searchState,
                            streamingState = streamingState.toUnifiedSearchStreamingState(),
                            activeDownload = activeDownload,
                            playbackQuality = playbackQuality,
                            audioMotion = audioMotion
                        )
                    }
                    NowTab -> NowPlayingDestination(
                        state = hostState.player.nowPlayingStateProvider.uiState,
                        immersive = nowPlayingImmersive,
                        onImmersiveChanged = { nowPlayingImmersive = it },
                        gesturesEnabled = settingsChromeState.nowPlayingGesturesEnabled,
                        onClose = closeNowPlayingAction,
                        onEvent = nowPlayingEventHandler,
                        onSwitchSource = { track, provider, providerTrackId, quality ->
                            nowPlayingEventHandler(
                                NowPlayingEvent.SwitchSource(track, provider, providerTrackId, quality)
                            )
                        },
                        sourceCandidates = { track ->
                            hostState.player.nowPlayingStateProvider.sourceCandidatesFor(track)
                        },
                        streamingProviders = streamingState.providers,
                        playbackSourcePolicy = streamingState.playbackSourcePolicy,
                        onSwitchLocalSource = { current, replacement ->
                            nowPlayingEventHandler(NowPlayingEvent.SwitchLibrarySource(current, replacement))
                        },
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    else -> HomeDestination(hostState.library.homeDashboardState, activeDownload, playbackQuality, audioMotion)
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
                    HomeTab -> HomeDestination(hostState.library.homeDashboardState, activeDownload, playbackQuality, audioMotion)
                    LibraryTab -> LibraryDestination(
                        groupsState = hostState.library.libraryGroupsState,
                        trackListState = hostState.library.libraryTrackListState,
                        libraryState = hostState.library.libraryStoreState,
                        downloadsState = hostState.library.downloadsState,
                        openSearchAction = openSearchAction,
                        openDownloadsAction = openDownloadsAction,
                        openNetworkSourcesAction = hostState.library.openNetworkSourcesAction,
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion,
                        actionHandler = hostState.library.libraryActionHandler,
                        recordingMatchStateProvider = hostState.library.recordingMatchStateProvider
                    )
                    QueueTab -> NowPlayingDestination(
                        state = hostState.player.nowPlayingStateProvider.uiState,
                        immersive = nowPlayingImmersive,
                        onImmersiveChanged = { nowPlayingImmersive = it },
                        gesturesEnabled = settingsChromeState.nowPlayingGesturesEnabled,
                        onClose = closeNowPlayingAction,
                        onEvent = nowPlayingEventHandler,
                        onSwitchSource = { track, provider, providerTrackId, quality ->
                            nowPlayingEventHandler(
                                NowPlayingEvent.SwitchSource(track, provider, providerTrackId, quality)
                            )
                        },
                        sourceCandidates = { track ->
                            hostState.player.nowPlayingStateProvider.sourceCandidatesFor(track)
                        },
                        streamingProviders = streamingState.providers,
                        playbackSourcePolicy = streamingState.playbackSourcePolicy,
                        onSwitchLocalSource = { current, replacement ->
                            nowPlayingEventHandler(NowPlayingEvent.SwitchLibrarySource(current, replacement))
                        },
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    SettingsTab -> SettingsDestination(
                        state = hostState.settings.settingsState,
                        scrollState = hostState.settings.settingsScrollState,
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    else -> HomeDestination(hostState.library.homeDashboardState, activeDownload, playbackQuality, audioMotion)
                }
            }
        }
    }

    if (hostState.queueSheetVisible) {
        val p = EchoTheme.colors()
        ModalBottomSheet(
            onDismissRequest = { hostState.setQueueSheetVisibility(false) },
            sheetState = sheetState,
            containerColor = p.surface
        ) {
            QueueDestination(
                hostState.player.queueStateProvider,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            )
        }
    }
}

@Composable
private fun LibraryDestination(
    groupsState: StateFlow<LibraryGroupsDestinationState>,
    trackListState: StateFlow<LibraryTrackListDestinationState>,
    libraryState: StateFlow<LibraryStoreState>,
    downloadsState: StateFlow<DownloadsUiState>,
    openSearchAction: Runnable,
    openDownloadsAction: Runnable,
    openNetworkSourcesAction: Runnable,
    activeDownload: TrackDownloadItem?,
    playbackQuality: String,
    audioMotion: YukineOrbAudioMotion,
    actionHandler: LibraryActionHandler,
    recordingMatchStateProvider: app.yukine.RecordingMatchDestinationStateProvider?
) {
    var showOverview by rememberSaveable { mutableStateOf(true) }
    val recordingMatchState = recordingMatchStateProvider?.uiState?.collectAsState()
    if (recordingMatchState?.value?.visible == true) {
        RecordingMatchDestination(recordingMatchState.value, recordingMatchStateProvider)
        return
    }
    if (showOverview) {
        val library by libraryState.collectAsState()
        val downloads by downloadsState.collectAsState()
        val groups by groupsState.collectAsState()
        val trackList by trackListState.collectAsState()
        val modeActions = trackList.modeActions.ifEmpty { groups.modeActions }
        val openMode: (String) -> Unit = { mode ->
            modeActions.firstOrNull { it.mode == mode }?.let { action ->
                actionHandler.onAction(LibraryAction.FilterChanged(LibraryFilter.All))
                action.onClick.run()
                showOverview = false
            }
        }
        LibraryOverviewScreen(
            library = library,
            downloads = downloads,
            modeActions = modeActions,
            onOpenMode = openMode,
            onOpenFavorites = {
                modeActions.firstOrNull { it.mode == app.yukine.LibraryGrouping.SONGS }?.let { action ->
                    action.onClick.run()
                    actionHandler.onAction(LibraryAction.FilterChanged(LibraryFilter.Favorites))
                    showOverview = false
                }
            },
            onOpenRecent = { openMode(app.yukine.LibraryGrouping.SONGS) },
            onOpenDownloads = { openDownloadsAction.run() },
            onOpenSources = { openNetworkSourcesAction.run() },
            onManageLibrary = { actionHandler.onAction(LibraryAction.SyncLibrary) },
            onSearch = openSearchAction,
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion
        )
        return
    }
    // The child destinations own full list collection. The parent only needs to know which
    // destination is active, so row/action updates in a large list do not recompose this branch.
    val hasGroups by remember(groupsState) {
        groupsState.map { it.title.isNotBlank() }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val hasTrackList by remember(trackListState) {
        trackListState.map { it.title.isNotBlank() }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val hasTrackListBack by remember(trackListState) {
        trackListState.map { state -> state.headerActions.any { it.isBack } }.distinctUntilChanged()
    }.collectAsState(initial = false)
    BackHandler(enabled = !hasTrackListBack) {
        showOverview = true
    }
    val renderGroups = hasGroups && !hasTrackList
    if (renderGroups) {
        LibraryGroupsDestination(
            state = groupsState,
            onSearch = openSearchAction,
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion,
            actionHandler = actionHandler,
            libraryControlsEnabled = true
        )
        return
    }
    LibraryTrackListDestination(
        state = trackListState,
        onSearch = openSearchAction,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion,
        actionHandler = actionHandler,
        libraryControlsEnabled = true
    )
}

private fun backgroundPageForTab(tab: TabRoute): String = when (tab) {
    HomeTab -> app.yukine.PageBackgrounds.PAGE_HOME
    LibraryTab -> app.yukine.PageBackgrounds.PAGE_LIBRARY
    QueueTab, NowTab -> app.yukine.PageBackgrounds.PAGE_PLAYER
    SettingsTab -> app.yukine.PageBackgrounds.PAGE_SETTINGS
    else -> ""
}

private fun realtimeVisualsVisible(selectedTab: TabRoute, networkPage: NetworkPage): Boolean = when (selectedTab) {
    HomeTab, LibraryTab, QueueTab, SettingsTab, SearchTab, NowTab -> true
    NetworkTab -> networkPage == NetworkPage.Home ||
        networkPage == NetworkPage.WebDav ||
        networkPage == NetworkPage.StreamList ||
        networkPage == NetworkPage.WebDavTracks ||
        networkPage == NetworkPage.WebDavSourceTracks
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
