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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
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
import app.yukine.settings.SettingsDestination
import app.yukine.ui.EchoTheme
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.delay
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoNavGraph(
    tabs: List<EchoTabItem>,
    queueViewModel: QueueViewModel,
    onTabChanged: (TabRoute) -> Unit = {},
    hostState: EchoNavHostState,
    nowBar: (@Composable () -> Unit)? = null,
    topBar: @Composable () -> Unit = {}
) {
    val playbackState by hostState.playbackViewModel.playback.collectAsState()
    val playbackQuality = qualityFromDataPath(playbackState.snapshot.currentTrack?.dataPath.orEmpty())
    var realtimeBeat by remember(hostState) {
        mutableStateOf(0f)
    }
    var realtimeBands by remember(hostState) {
        mutableStateOf(FloatArray(0))
    }
    LaunchedEffect(hostState) {
        while (true) {
            withFrameNanos { }
            realtimeBeat = hostState.realtimeBeatProvider().coerceIn(0f, 1f)
            realtimeBands = hostState.realtimeBandsProvider()
        }
    }
    val audioMotion = YukineOrbAudioMotion(
        spectrumBands = playbackState.snapshot.spectrum.bands,
        generatedFrames = playbackState.snapshot.spectrum.generatedFrames,
        bandCount = playbackState.snapshot.spectrum.bandCount,
        waveformBars = playbackState.snapshot.waveform.bars,
        waveformGeneratedBars = playbackState.snapshot.waveform.generatedBars,
        positionMs = playbackState.snapshot.positionMs,
        durationMs = playbackState.snapshot.durationMs,
        playing = playbackState.snapshot.playing,
        realtimeBeat = max(playbackState.snapshot.realtimeBeat, realtimeBeat),
        realtimeBands = realtimeBands
    )
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
            viewModel = hostState.nowPlayingViewModel,
            onOpenNowPlaying = {
                hostState.openNowPlayingImmersive = true
                hostState.selectedTabRoute = QueueTab.route
                onTabChanged(QueueTab)
            },
            onOpenQueue = { showQueueSheet = true }
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
        topBar = topBar
    ) { contentModifier ->
        if (!selectedInPager) {
            // Routes that live outside the 4-tab pager (Collections / Network / Now),
            // reachable via in-app navigation rather than the bottom bar. Render each
            // directly so navigating to them never leaves the pager showing a stale page.
            Box(modifier = contentModifier) {
                when (selectedTab) {
                    CollectionsTab -> CollectionsDestination(
                        hostState.collectionsViewModel,
                        hostState.collectionsActions
                    )
                    NetworkTab -> NetworkDestination(hostState)
                    DownloadsTab -> DownloadsDestination(
                        hostState.downloadsViewModel,
                        hostState.trackDownloadManager,
                        hostState.openDownloadDirectoryPickerAction
                    )
                    SearchTab -> SearchDestination(
                        hostState.searchViewModel,
                        hostState.streamingViewModel,
                        hostState.searchActions,
                        activeDownload,
                        playbackQuality,
                        audioMotion
                    )
                    NowTab -> NowPlayingDestination(
                        hostState.nowPlayingViewModel,
                        defaultImmersive = hostState.openNowPlayingImmersive,
                        onDefaultImmersiveConsumed = { hostState.openNowPlayingImmersive = false },
                        gesturesEnabled = hostState.nowPlayingGesturesEnabled,
                        onAppVolumeChanged = { volume -> hostState.settingsViewModel.applyAppVolume(volume) },
                        onEvent = hostState.nowPlayingEventHandler,
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    else -> HomeDestination(hostState.homeDashboardViewModel.uiState, hostState.homeActions, activeDownload, playbackQuality, audioMotion)
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
                    HomeTab -> HomeDestination(hostState.homeDashboardViewModel.uiState, hostState.homeActions, activeDownload, playbackQuality, audioMotion)
                    LibraryTab -> LibraryDestination(hostState, activeDownload, playbackQuality, audioMotion)
                    QueueTab -> NowPlayingDestination(
                        hostState.nowPlayingViewModel,
                        defaultImmersive = hostState.openNowPlayingImmersive,
                        onDefaultImmersiveConsumed = { hostState.openNowPlayingImmersive = false },
                        gesturesEnabled = hostState.nowPlayingGesturesEnabled,
                        onAppVolumeChanged = { volume -> hostState.settingsViewModel.applyAppVolume(volume) },
                        onEvent = hostState.nowPlayingEventHandler,
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    SettingsTab -> SettingsDestination(
                        state = hostState.settingsViewModel.uiState,
                        actions = hostState.settingsActions,
                        scrollState = hostState.settingsScrollState,
                        activeDownload = activeDownload,
                        playbackQuality = playbackQuality,
                        audioMotion = audioMotion
                    )
                    else -> HomeDestination(hostState.homeDashboardViewModel.uiState, hostState.homeActions, activeDownload, playbackQuality, audioMotion)
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
            actions = hostState.libraryGroupActions,
            emptyText = hostState.libraryGroupEmptyText,
            modeActions = hostState.libraryGroupModeActions,
            onSearch = hostState.openSearchAction,
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion
        )
        return
    }
    LibraryTrackListDestination(
        state = hostState.libraryViewModel.trackList,
        actions = hostState.trackListActions,
        headerMetrics = hostState.trackListHeaderMetrics,
        headerActions = hostState.trackListHeaderActions,
        emptyText = hostState.trackListEmptyText,
        modeActions = hostState.trackListModeActions,
        labels = hostState.trackListLabels,
        onSearch = hostState.openSearchAction,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    )
}

private fun qualityFromDataPath(dataPath: String): String {
    if (dataPath.isBlank()) {
        return ""
    }
    val marker = "quality="
    val start = dataPath.indexOf(marker)
    if (start < 0) {
        return ""
    }
    val valueStart = start + marker.length
    val valueEnd = listOf(
        dataPath.indexOf(':', valueStart),
        dataPath.indexOf('|', valueStart),
        dataPath.indexOf('&', valueStart),
        dataPath.indexOf('#', valueStart)
    ).filter { it >= 0 }.minOrNull() ?: dataPath.length
    return dataPath.substring(valueStart, valueEnd).trim().lowercase()
}
