package app.yukine

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.navigation.EchoNavHostBridge
import app.yukine.navigation.EchoNavHostState
import app.yukine.navigation.EchoTabItem
import app.yukine.navigation.TabRoute
import app.yukine.NowPlayingEvent
import app.yukine.ui.EchoTheme
import app.yukine.ui.OnboardingActions
import app.yukine.ui.OnboardingScreen
import app.yukine.ui.StreamingUsageNoticeLabels
import kotlinx.coroutines.flow.StateFlow

/**
 * Java-implementable wiring for the single-Activity Compose shell.
 *
 * MainActivity (Java) provides ViewModels and route callbacks; [EchoAppHost.installNavHost]
 * mounts the pure Compose NavHost entry.
 */
interface EchoNavHostMount {
    fun tabs(): List<EchoTabItem>
    fun hostState(): EchoNavHostState
    fun onboardingState(): StateFlow<OnboardingUiState>
    fun closeNowPlayingAction(): Runnable = Runnable { }
    fun nowPlayingEventHandler(): (NowPlayingEvent) -> Unit = {}
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
            val onboarding by mount.onboardingState().collectAsState()
            EchoTheme.EchoTheme {
                if (onboarding.visible) {
                    OnboardingScreen(
                        audioPermissionGranted = onboarding.audioPermissionGranted,
                        notificationPermissionGranted = onboarding.notificationPermissionGranted,
                        libraryScanCompleted = onboarding.libraryScanCompleted,
                        libraryScanInProgress = onboarding.libraryScanInProgress,
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
