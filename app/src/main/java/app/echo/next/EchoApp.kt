package app.echo.next

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.echo.next.navigation.EchoNavHostBridge
import app.echo.next.navigation.EchoNavHostState
import app.echo.next.navigation.EchoTabItem
import app.echo.next.navigation.TabRoute
import app.echo.next.queue.QueueViewModel
import app.echo.next.ui.EchoTheme

/**
 * Java-implementable wiring for the single-Activity Compose shell.
 *
 * MainActivity (Java) provides ViewModels and route callbacks; [EchoAppHost.installNavHost]
 * mounts the pure Compose NavHost entry.
 */
interface EchoNavHostMount {
    fun tabs(): List<EchoTabItem>
    fun queueViewModel(): QueueViewModel
    fun hostState(): EchoNavHostState
    fun onTabChanged(tab: TabRoute) {}
}

object EchoAppHost {
    /** Mounts the single-Activity Compose NavHost shell. */
    @JvmStatic
    fun installNavHost(activity: ComponentActivity, mount: EchoNavHostMount) {
        activity.setContent {
            EchoTheme.EchoTheme {
                EchoNavHostBridge(
                    tabs = mount.tabs(),
                    queueViewModel = mount.queueViewModel(),
                    hostState = mount.hostState(),
                    onTabChanged = { tab -> mount.onTabChanged(tab) }
                )
            }
        }
    }
}
