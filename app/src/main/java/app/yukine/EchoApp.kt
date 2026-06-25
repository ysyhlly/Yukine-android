package app.yukine

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.yukine.navigation.EchoNavHostBridge
import app.yukine.navigation.EchoNavHostState
import app.yukine.navigation.EchoTabItem
import app.yukine.navigation.TabRoute
import app.yukine.NowPlayingEvent
import app.yukine.queue.QueueViewModel
import app.yukine.ui.EchoTheme
import app.yukine.ui.OnboardingActions
import app.yukine.ui.OnboardingScreen

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
    fun searchViewModel(): SearchViewModel = SearchViewModel()
    fun openDownloadDirectoryPickerAction(): Runnable = Runnable { }
    fun closeNowPlayingAction(): Runnable = Runnable { }
    fun nowPlayingEventHandler(): (NowPlayingEvent) -> Unit = { event -> hostState().nowPlayingViewModel.onEvent(event) }
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
        val queueViewModel = mount.queueViewModel()
        val hostState = mount.hostState()
        activity.setContent {
            EchoTheme.EchoTheme {
                if (mount.showOnboarding()) {
                    OnboardingScreen(
                        audioPermissionGranted = mount.audioPermissionGranted(),
                        notificationPermissionGranted = mount.notificationPermissionGranted(),
                        libraryScanCompleted = mount.libraryScanCompleted(),
                        libraryScanInProgress = mount.libraryScanInProgress(),
                        languageMode = mount.languageMode(),
                        actions = mount.onboardingActions()
                    )
                } else {
                    EchoNavHostBridge(
                        tabs = tabs,
                        queueViewModel = queueViewModel,
                        hostState = hostState,
                        searchViewModel = mount.searchViewModel(),
                        openDownloadDirectoryPickerAction = mount.openDownloadDirectoryPickerAction(),
                        closeNowPlayingAction = mount.closeNowPlayingAction(),
                        nowPlayingEventHandler = mount.nowPlayingEventHandler(),
                        onTabChanged = { tab -> mount.onTabChanged(tab) }
                    )
                }
            }
        }
    }
}
