package app.echo.next.navigation

import androidx.compose.runtime.Composable
import app.echo.next.queue.QueueViewModel

@Composable
fun EchoNavHostBridge(
    tabs: List<EchoTabItem>,
    queueViewModel: QueueViewModel,
    hostState: EchoNavHostState,
    onTabChanged: (TabRoute) -> Unit = {}
) {
    EchoNavGraph(
        tabs = tabs,
        queueViewModel = queueViewModel,
        hostState = hostState,
        onTabChanged = onTabChanged
    )
}
