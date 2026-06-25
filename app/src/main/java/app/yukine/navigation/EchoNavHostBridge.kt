package app.yukine.navigation

import androidx.compose.runtime.Composable
import app.yukine.SearchViewModel
import app.yukine.queue.QueueViewModel
import app.yukine.NowPlayingEvent

@Composable
fun EchoNavHostBridge(
    tabs: List<EchoTabItem>,
    queueViewModel: QueueViewModel,
    hostState: EchoNavHostState,
    searchViewModel: SearchViewModel = SearchViewModel(),
    openDownloadDirectoryPickerAction: Runnable = Runnable { },
    closeNowPlayingAction: Runnable = Runnable { },
    nowPlayingEventHandler: (NowPlayingEvent) -> Unit = { hostState.nowPlayingViewModel.onEvent(it) },
    onTabChanged: (TabRoute) -> Unit = {}
) {
    EchoNavGraph(
        tabs = tabs,
        queueViewModel = queueViewModel,
        hostState = hostState,
        searchViewModel = searchViewModel,
        openDownloadDirectoryPickerAction = openDownloadDirectoryPickerAction,
        closeNowPlayingAction = closeNowPlayingAction,
        nowPlayingEventHandler = nowPlayingEventHandler,
        onTabChanged = onTabChanged
    )
}
