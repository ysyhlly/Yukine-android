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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.collections.CollectionsDestination
import app.yukine.downloads.DownloadsDestination
import app.yukine.home.HomeDestination
import app.yukine.library.LibraryGroupsDestination
import app.yukine.library.LibraryTrackListDestination
import app.yukine.network.NetworkDestination
import app.yukine.now.NowPlayingDestination
import app.yukine.queue.QueueDestination
import app.yukine.queue.QueueViewModel
import app.yukine.search.SearchDestination
import app.yukine.SearchViewModel
import app.yukine.settings.SettingsDestination
import app.yukine.ui.EchoTheme
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.delay
import kotlin.math.max

private val EmptyRealtimeBands = FloatArray(0)
private const val RealtimeVisualPollMs = 33L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoNavGraph(
    tabs: List<EchoTabItem>,
    queueViewModel: QueueViewModel,
    onTabChanged: (TabRoute) -> Unit = {},
    hostState: EchoNavHostState,
    searchViewModel: SearchViewModel = SearchViewModel(),
    openDownloadDirectoryPickerAction: Runnable = Runnable { },
    closeNowPlayingAction: Runnable = Runnable { },
    nowPlayingEventHandler: (NowPlayingEvent) -> Unit = {},
    nowBar: (@Composable () -> Unit)? = null,
    topBar: @Composable () -> Unit = {}
) {
    val playbackState by hostState.playbackSnapshotProvider.playbackSnapshot.collectAsState()
    val playbackQuality = StreamingDataPathMetadata.quality(playbackState.currentTrack?.dataPath)
    var realtimeBeat by remember(hostState) {
        mutableStateOf(0f)
    }
    var realtimeBands by remember(hostState) {
        mutableStateOf(EmptyRealtimeBands)
    }
    val realtimeVisualsActive = hostState.visualMotionEnabled && playbackState.playing
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
            withFrameNanos { }
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
    val settingsState by hostState.settingsViewModel.state.collectAsState()
    var activeDownload by remember(hostState.trackDownloadManager) {
        mutableStateOf<TrackDownloadItem?>(null)
    }
    LaunchedEffect(hostState.trackDownloadManager) {
        while (true) {
            activeDownload = hostState.trackDownloadManager
                ?.snapshot()
                ?.firstOrNull { it.status != TrackDownloadStatus.Finished }
            delay(1000L)
        }
    }
    val pagerTabs = tabs.map { it.tab }
    val selectedTab = TabRoute.fromKey(hostState.selectedTabRoute) ?: HomeTab
    val selectedPagerIndex = pagerTabs.indexOf(selectedTab)
    val selectedIndex = selectedPagerIndex.coerceAtLeast(0)
    val selectedInPager = selectedPagerIndex >= 0
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
        backgroundUri = settingsState.preferences.pageBackgrounds.uriFor(backgroundPageForTab(selectedTab)),
        backgroundTransform = settingsState.preferences.pageBackgrounds.transformFor(backgroundPageForTab(selectedTab))
    ) { contentModifier ->
        if (!selectedInPager) {
            // Routes that live outside the 4-tab pager (Collections / Network / Now),
            // reachable via in-app navigation rather than the bottom bar. Render each
            // directly so navigating to them never leaves the pager showing a stale page.
            Box(modifier = contentModifier) {
                when (selectedTab) {
                    CollectionsTab -> CollectionsDestination(hostState.collectionsViewModel)
                    NetworkTab -> NetworkDestination(hostState)
                    DownloadsTab -> DownloadsDestination(
                        hostState.downloadsViewModel,
                        hostState.trackDownloadManager,
                        openDownloadDirectoryPickerAction
                    )
                    SearchTab -> SearchDestination(
                        searchViewModel,
                        hostState.streamingViewModel,
                        activeDownload,
                        playbackQuality,
                        audioMotion
                    )
                    NowTab -> NowPlayingDestination(
                        state = hostState.nowPlayingUiState,
                        defaultImmersive = openNowPlayingImmersive,
                        onDefaultImmersiveConsumed = { openNowPlayingImmersive = false },
                        gesturesEnabled = settingsState.preferences.nowPlayingGesturesEnabled,
                        onClose = closeNowPlayingAction,
                        onEvent = nowPlayingEventHandler,
                        onSwitchSource = { track, provider, providerTrackId, quality ->
                            hostState.nowPlayingStateProvider.switchSource(track, provider, providerTrackId, quality)
                        },
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    else -> HomeDestination(hostState.homeDashboardViewModel.uiState, activeDownload, playbackQuality, audioMotion)
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
                    HomeTab -> HomeDestination(hostState.homeDashboardViewModel.uiState, activeDownload, playbackQuality, audioMotion)
                    LibraryTab -> LibraryDestination(hostState, openSearchAction, activeDownload, playbackQuality, audioMotion)
                    QueueTab -> NowPlayingDestination(
                        state = hostState.nowPlayingUiState,
                        defaultImmersive = openNowPlayingImmersive,
                        onDefaultImmersiveConsumed = { openNowPlayingImmersive = false },
                        gesturesEnabled = settingsState.preferences.nowPlayingGesturesEnabled,
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
                        state = hostState.settingsViewModel.state,
                        scrollState = hostState.settingsViewModel.scrollState,
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    else -> HomeDestination(hostState.homeDashboardViewModel.uiState, activeDownload, playbackQuality, audioMotion)
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
                queueViewModel,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            )
        }
    }
}

@Composable
private fun LibraryDestination(
    hostState: EchoNavHostState,
    openSearchAction: Runnable,
    activeDownload: TrackDownloadItem?,
    playbackQuality: String,
    audioMotion: YukineOrbAudioMotion
) {
    val groups by hostState.libraryViewModel.libraryGroups.collectAsState()
    val trackList by hostState.libraryViewModel.trackList.collectAsState()
    val renderGroups = groups.title.isNotBlank() && trackList.title.isBlank()
    if (renderGroups) {
        LibraryGroupsDestination(
            state = hostState.libraryViewModel.libraryGroups,
            onSearch = openSearchAction,
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion
        )
        return
    }
    LibraryTrackListDestination(
        state = hostState.libraryViewModel.trackList,
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
