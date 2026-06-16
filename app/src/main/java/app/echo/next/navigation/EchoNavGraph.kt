package app.echo.next.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.echo.next.collections.CollectionsDestination
import app.echo.next.home.HomeDestination
import app.echo.next.library.LibraryGroupsDestination
import app.echo.next.library.LibraryTrackListDestination
import app.echo.next.network.NetworkDestination
import app.echo.next.now.NowPlayingDestination
import app.echo.next.queue.QueueDestination
import app.echo.next.queue.QueueViewModel
import app.echo.next.settings.SettingsDestination
import app.echo.next.ui.EchoTheme

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
                    NowTab -> NowPlayingDestination(hostState.nowPlayingViewModel)
                    else -> HomeDestination(hostState.mainViewModel.homeDashboard, hostState.homeActions)
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
                    HomeTab -> HomeDestination(hostState.mainViewModel.homeDashboard, hostState.homeActions)
                    LibraryTab -> LibraryDestination(hostState)
                    QueueTab -> NowPlayingDestination(hostState.nowPlayingViewModel)
                    SettingsTab -> SettingsDestination(
                        state = hostState.settingsViewModel.uiState,
                        actions = hostState.settingsActions,
                        scrollState = hostState.settingsScrollState
                    )
                    else -> HomeDestination(hostState.mainViewModel.homeDashboard, hostState.homeActions)
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
            )
        }
    }
}

@Composable
private fun LibraryDestination(hostState: EchoNavHostState) {
    val groups by hostState.libraryViewModel.libraryGroups.collectAsState()
    val trackList by hostState.libraryViewModel.trackList.collectAsState()
    val renderGroups = groups.title.isNotBlank() && trackList.title.isBlank()
    if (renderGroups) {
        LibraryGroupsDestination(
            state = hostState.libraryViewModel.libraryGroups,
            actions = hostState.libraryGroupActions,
            emptyText = hostState.libraryGroupEmptyText,
            modeActions = hostState.libraryGroupModeActions
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
        labels = hostState.trackListLabels
    )
}
