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
import app.yukine.ui.OnboardingLabels
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
        OnboardingActions(
            Runnable {},
            Runnable {},
            Runnable {},
            Runnable {},
            Runnable {},
            Runnable {}
        )
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
                        libraryScanCompleted = onboarding.libraryScanCompleted,
                        libraryScanInProgress = onboarding.libraryScanInProgress,
                        localMusicFolderCount = onboarding.localMusicFolderCount,
                        labels = onboardingLabels(mount.languageMode()),
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

private fun onboardingLabels(language: String) = OnboardingLabels(
    title = AppLanguage.text(language, "onboarding.source.title"),
    subtitle = AppLanguage.text(language, "onboarding.source.subtitle"),
    summaryTitle = AppLanguage.text(language, "onboarding.source.summary"),
    summaryEmpty = AppLanguage.text(language, "onboarding.source.empty"),
    summaryFolders = AppLanguage.text(language, "onboarding.source.folders"),
    folderTitle = AppLanguage.text(language, "onboarding.folder.title"),
    folderDescription = AppLanguage.text(language, "onboarding.folder.description"),
    folderConfigured = AppLanguage.text(language, "onboarding.folder.configured"),
    folderAction = AppLanguage.text(language, "onboarding.folder.action"),
    deviceTitle = AppLanguage.text(language, "onboarding.device.title"),
    deviceDescription = AppLanguage.text(language, "onboarding.device.description"),
    devicePermissionDescription = AppLanguage.text(language, "onboarding.device.permission"),
    deviceScanning = AppLanguage.text(language, "onboarding.device.scanning"),
    deviceCompleted = AppLanguage.text(language, "onboarding.device.completed"),
    deviceAction = AppLanguage.text(language, "onboarding.device.action"),
    deviceRescanAction = AppLanguage.text(language, "onboarding.device.rescan"),
    playlistAction = AppLanguage.text(language, "onboarding.playlist.action"),
    streamingAction = AppLanguage.text(language, "onboarding.streaming.action"),
    optionalHint = AppLanguage.text(language, "onboarding.optional"),
    finishHint = AppLanguage.text(language, "onboarding.finish.hint"),
    finishAction = AppLanguage.text(language, "onboarding.finish.action")
)
