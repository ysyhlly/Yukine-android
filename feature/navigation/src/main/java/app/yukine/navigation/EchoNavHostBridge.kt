package app.yukine.navigation

import androidx.compose.runtime.Composable
import app.yukine.NowPlayingEvent

@Composable
fun EchoNavHostBridge(
    tabs: List<EchoTabItem>,
    hostState: EchoNavHostState,
    closeNowPlayingAction: Runnable = Runnable { },
    nowPlayingEventHandler: (NowPlayingEvent) -> Unit = {},
    onTabChanged: (TabRoute) -> Unit = {}
) {
    EchoNavGraph(
        tabs = tabs,
        hostState = hostState,
        closeNowPlayingAction = closeNowPlayingAction,
        nowPlayingEventHandler = nowPlayingEventHandler,
        onTabChanged = onTabChanged
    )
}
