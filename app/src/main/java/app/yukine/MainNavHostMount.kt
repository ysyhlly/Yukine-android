package app.yukine

import app.yukine.navigation.EchoNavHostState
import app.yukine.navigation.EchoTabItem
import app.yukine.navigation.HomeTab
import app.yukine.navigation.LibraryTab
import app.yukine.navigation.QueueTab
import app.yukine.navigation.SettingsTab
import app.yukine.navigation.TabRoute
import app.yukine.ui.OnboardingActions
import kotlinx.coroutines.flow.StateFlow

/** Immutable Compose-shell mount assembled once by the Activity. */
internal class MainNavHostMount(
    private val hostStateProvider: () -> EchoNavHostState,
    private val onboarding: StateFlow<OnboardingUiState>,
    private val languageModeProvider: () -> String,
    private val onboardingActionSet: OnboardingActions,
    private val closeNowPlaying: Runnable,
    private val nowPlayingEvents: (NowPlayingEvent) -> Unit,
    private val tabChanges: (TabRoute) -> Unit
) : EchoNavHostMount {
    override fun languageMode(): String = languageModeProvider()

    override fun tabs(): List<EchoTabItem> {
        val language = languageModeProvider()
        return listOf(
            EchoTabItem(HomeTab, AppLanguage.tabLabel(language, MainRoutes.TAB_HOME)),
            EchoTabItem(LibraryTab, AppLanguage.tabLabel(language, MainRoutes.TAB_LIBRARY)),
            EchoTabItem(QueueTab, AppLanguage.text(language, "tab.playing")),
            EchoTabItem(SettingsTab, AppLanguage.tabLabel(language, MainRoutes.TAB_SETTINGS))
        )
    }

    override fun closeNowPlayingAction(): Runnable = closeNowPlaying

    override fun nowPlayingEventHandler(): (NowPlayingEvent) -> Unit = nowPlayingEvents

    override fun hostState(): EchoNavHostState = hostStateProvider()

    override fun onboardingState(): StateFlow<OnboardingUiState> = onboarding

    override fun onboardingActions(): OnboardingActions = onboardingActionSet

    override fun onTabChanged(tab: TabRoute) {
        tabChanges(tab)
    }
}
