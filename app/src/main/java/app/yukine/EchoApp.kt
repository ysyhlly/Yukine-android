package app.yukine

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.yukine.navigation.EchoNavHostBridge
import app.yukine.navigation.EchoNavHostState
import app.yukine.navigation.EchoTabItem
import app.yukine.navigation.TabRoute
import app.yukine.NowPlayingEvent
import app.yukine.ui.EchoTheme
import app.yukine.ui.OnboardingActions
import app.yukine.ui.OnboardingScreen
import app.yukine.ui.StreamingUsageNoticeLabels

/**
 * Java-implementable wiring for the single-Activity Compose shell.
 *
 * MainActivity (Java) provides ViewModels and route callbacks; [EchoAppHost.installNavHost]
 * mounts the pure Compose NavHost entry.
 */
interface EchoNavHostMount {
    fun tabs(): List<EchoTabItem>
    fun hostState(): EchoNavHostState
    fun closeNowPlayingAction(): Runnable = Runnable { }
    fun nowPlayingEventHandler(): (NowPlayingEvent) -> Unit = {}
    fun showOnboarding(): Boolean = false
    fun audioPermissionGranted(): Boolean = true
    fun notificationPermissionGranted(): Boolean = true
    fun libraryScanCompleted(): Boolean = true
    fun libraryScanInProgress(): Boolean = false
    fun languageMode(): String = AppLanguage.MODE_SYSTEM
    fun onboardingActions(): OnboardingActions =
        OnboardingActions(Runnable {}, Runnable {}, Runnable {}, Runnable {}, Runnable {})
    fun onTabChanged(tab: TabRoute) {}
}

object EchoAppHost {
    /** Mounts the single-Activity Compose NavHost shell. */
    @JvmStatic
    fun installNavHost(activity: ComponentActivity, mount: EchoNavHostMount) {
        val tabs = mount.tabs()
        val hostState = mount.hostState()
        activity.setContent {
            EchoTheme.EchoTheme {
                if (mount.showOnboarding()) {
                    OnboardingScreen(
                        audioPermissionGranted = mount.audioPermissionGranted(),
                        notificationPermissionGranted = mount.notificationPermissionGranted(),
                        libraryScanCompleted = mount.libraryScanCompleted(),
                        libraryScanInProgress = mount.libraryScanInProgress(),
                        noticeLabels = StreamingUsageNoticeLabels(
                            title = AppLanguage.text(mount.languageMode(), "streaming.usage.notice.title"),
                            body = AppLanguage.text(mount.languageMode(), "streaming.usage.notice.body")
                        ),
                        actions = mount.onboardingActions()
                    )
                } else {
                    EchoNavHostBridge(
                        tabs = tabs,
                        hostState = hostState,
                        closeNowPlayingAction = mount.closeNowPlayingAction(),
                        nowPlayingEventHandler = mount.nowPlayingEventHandler(),
                        onTabChanged = { tab -> mount.onTabChanged(tab) }
                    )
                }
            }
        }
    }
}
